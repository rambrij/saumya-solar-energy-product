-- READ-ONLY tenant schema inventory and NOT NULL evidence for MySQL 8.4.
-- Run against the exact production or production-like database with a SELECT-only account.
-- This script reads INFORMATION_SCHEMA and tenant_id values only; it performs no DDL/DML.
-- It deliberately reports candidate classifications instead of assuming every table is tenant-owned.

SET SESSION group_concat_max_len = 1048576;

-- A. Every base table: tenant_id presence, nullability and an explicit review classification.
SELECT
    t.TABLE_NAME,
    CASE
      WHEN t.TABLE_NAME IN ('tenant', 'subscription_plan', 'permission', 'auth_rate_limit_bucket', 'payment_webhook_event')
        THEN 'PLATFORM_GLOBAL_CANDIDATE_OWNER_CONFIRMATION'
      WHEN t.TABLE_NAME IN ('app_user', 'role', 'audit_event', 'notification_delivery')
        THEN 'MIXED_PLATFORM_AND_TENANT_REVIEW_REQUIRED'
      WHEN t.TABLE_NAME IN ('mfa_challenge', 'password_reset_token', 'refresh_token')
        THEN 'USER_SCOPED_VIA_APP_USER_REVIEW_REQUIRED'
      WHEN t.TABLE_NAME IN ('role_permission', 'user_role')
        THEN 'JOIN_TABLE_SCOPE_INHERITED_FROM_PARENT_REVIEW_REQUIRED'
      WHEN c.COLUMN_NAME IS NULL THEN 'UNCLASSIFIED_NO_TENANT_COLUMN'
      ELSE 'TENANT_SCOPED_CANDIDATE_REQUIRES_OWNER_CONFIRMATION'
    END AS ownership_classification,
    CASE WHEN c.COLUMN_NAME IS NULL THEN 'MISSING'
         ELSE CONCAT(c.IS_NULLABLE, ' / ', c.COLUMN_TYPE) END AS tenant_id_definition,
    t.TABLE_ROWS AS estimated_rows
FROM information_schema.TABLES t
LEFT JOIN information_schema.COLUMNS c
  ON c.TABLE_SCHEMA=t.TABLE_SCHEMA AND c.TABLE_NAME=t.TABLE_NAME AND c.COLUMN_NAME='tenant_id'
WHERE t.TABLE_SCHEMA=DATABASE() AND t.TABLE_TYPE='BASE TABLE'
  AND t.TABLE_NAME <> 'flyway_schema_history'
ORDER BY ownership_classification, t.TABLE_NAME;

-- B. Execute exact COUNT(*) / COUNT(tenant_id IS NULL) for every base table exposing tenant_id.
-- Counts are exact; the generated statement is bounded by group_concat_max_len above.
SET @tenant_count_sql = (
  SELECT GROUP_CONCAT(
    CONCAT(
      'SELECT ', QUOTE(c.TABLE_NAME), ' AS table_name, COUNT(*) AS total_rows, ',
      'COALESCE(SUM(tenant_id IS NULL),0) AS null_tenant_rows, ',
      QUOTE(c.IS_NULLABLE), ' AS column_is_nullable FROM `',
      REPLACE(c.TABLE_NAME, '`', '``'), '`'
    ) SEPARATOR ' UNION ALL '
  )
  FROM information_schema.COLUMNS c
  JOIN information_schema.TABLES t
    ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME
  WHERE c.TABLE_SCHEMA=DATABASE() AND c.COLUMN_NAME='tenant_id'
    AND t.TABLE_TYPE='BASE TABLE' AND c.TABLE_NAME <> 'flyway_schema_history'
);
SET @tenant_count_sql = COALESCE(
  @tenant_count_sql,
  'SELECT CAST(NULL AS CHAR) table_name, 0 total_rows, 0 null_tenant_rows, CAST(NULL AS CHAR) column_is_nullable WHERE 1=0'
);
PREPARE tenant_count_stmt FROM @tenant_count_sql;
EXECUTE tenant_count_stmt;
DEALLOCATE PREPARE tenant_count_stmt;

-- C. Foreign-key inventory. A tenant-owned child/parent relationship should normally
-- carry tenant_id on both sides of the composite FK. Review all exceptions.
SELECT
    k.TABLE_NAME AS child_table,
    k.CONSTRAINT_NAME,
    GROUP_CONCAT(k.COLUMN_NAME ORDER BY k.ORDINAL_POSITION) AS child_columns,
    k.REFERENCED_TABLE_NAME AS parent_table,
    GROUP_CONCAT(k.REFERENCED_COLUMN_NAME ORDER BY k.ORDINAL_POSITION) AS parent_columns,
    SUM(k.COLUMN_NAME='tenant_id') AS child_tenant_columns,
    SUM(k.REFERENCED_COLUMN_NAME='tenant_id') AS parent_tenant_columns
FROM information_schema.KEY_COLUMN_USAGE k
WHERE k.TABLE_SCHEMA=DATABASE() AND k.REFERENCED_TABLE_NAME IS NOT NULL
GROUP BY k.TABLE_NAME, k.CONSTRAINT_NAME, k.REFERENCED_TABLE_NAME
ORDER BY child_table, k.CONSTRAINT_NAME;

-- D. Unique-index inventory. Tenant-owned natural keys should normally include tenant_id.
SELECT s.TABLE_NAME, s.INDEX_NAME,
       GROUP_CONCAT(s.COLUMN_NAME ORDER BY s.SEQ_IN_INDEX) AS index_columns,
       s.NON_UNIQUE
FROM information_schema.STATISTICS s
WHERE s.TABLE_SCHEMA=DATABASE()
GROUP BY s.TABLE_NAME, s.INDEX_NAME, s.NON_UNIQUE
ORDER BY s.TABLE_NAME, s.INDEX_NAME;

-- E. Machine-readable release gate summary for tenant_id column nullability.
SELECT
    COUNT(*) AS tables_with_tenant_id,
    SUM(IS_NULLABLE='NO') AS tenant_id_columns_declared_not_null,
    SUM(IS_NULLABLE='YES') AS tenant_id_columns_still_nullable
FROM information_schema.COLUMNS c
JOIN information_schema.TABLES t
  ON t.TABLE_SCHEMA=c.TABLE_SCHEMA AND t.TABLE_NAME=c.TABLE_NAME
WHERE c.TABLE_SCHEMA=DATABASE() AND c.COLUMN_NAME='tenant_id'
  AND t.TABLE_TYPE='BASE TABLE' AND c.TABLE_NAME <> 'flyway_schema_history';

-- Interpretation:
-- 1) A candidate tenant table passes the schema gate only when IS_NULLABLE='NO' AND null_tenant_rows=0.
-- 2) app_user, role, audit_event and notification_delivery are mixed-scope; notification_delivery permits NULL tenant_id only for platform authentication messages. Validate owner/account_type semantics before any alteration.
-- 3) A table with no tenant_id is not automatically global. It remains UNCLASSIFIED until its owner
--    proves platform-global scope or a safe tenant relationship/column is added.
-- 4) Compare actual base tables, FK and unique indexes with JPA entities, migrations and the SRS.
-- 5) This SQL does not prove production cleanliness until it has been run against production and its
--    complete result set has been reviewed, archived and signed by the DBA/data owner.
