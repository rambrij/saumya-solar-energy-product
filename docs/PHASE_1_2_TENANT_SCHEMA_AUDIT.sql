-- Read-only tenant schema audit for Saumya Solar Energy.
-- Run against the target MySQL 8.4 database using a read-only account.
-- This script does not modify application data or schema.

-- 1) Every table and whether tenant_id exists, its nullability, and current NULL count.
SELECT
    t.TABLE_NAME,
    CASE WHEN c.COLUMN_NAME IS NULL THEN 'MISSING_TENANT_ID'
         ELSE CONCAT('tenant_id ', c.IS_NULLABLE, ' NULLABLE') END AS tenant_column_status,
    CASE WHEN c.COLUMN_NAME IS NULL THEN NULL
         ELSE (SELECT COUNT(*) FROM information_schema.COLUMNS c2
               WHERE c2.TABLE_SCHEMA = DATABASE()
                 AND c2.TABLE_NAME = t.TABLE_NAME
                 AND c2.COLUMN_NAME = 'tenant_id') END AS tenant_column_count
FROM information_schema.TABLES t
LEFT JOIN information_schema.COLUMNS c
  ON c.TABLE_SCHEMA = t.TABLE_SCHEMA
 AND c.TABLE_NAME = t.TABLE_NAME
 AND c.COLUMN_NAME = 'tenant_id'
WHERE t.TABLE_SCHEMA = DATABASE()
  AND t.TABLE_TYPE = 'BASE TABLE'
  AND t.TABLE_NAME NOT IN ('flyway_schema_history')
ORDER BY tenant_column_status, t.TABLE_NAME;

-- 2) NULL tenant rows in tables that expose tenant_id.
-- The dynamic SQL is intentionally emitted for operator review rather than executed
-- automatically, so the report is safe on production and can be reviewed before counts.
SELECT CONCAT(
    'SELECT ', QUOTE(TABLE_NAME), ' AS table_name, COUNT(*) AS null_tenant_rows FROM `',
    REPLACE(TABLE_NAME, '`', '``'),
    '` WHERE tenant_id IS NULL HAVING COUNT(*) > 0;'
) AS read_only_null_check_sql
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND COLUMN_NAME = 'tenant_id'
ORDER BY TABLE_NAME;

-- 3) Foreign-key inventory. Review tenant-owned relationships where the FK does not
-- include tenant_id on both child and referenced parent columns.
SELECT
    k.TABLE_NAME AS child_table,
    k.CONSTRAINT_NAME,
    GROUP_CONCAT(k.COLUMN_NAME ORDER BY k.ORDINAL_POSITION) AS child_columns,
    k.REFERENCED_TABLE_NAME AS parent_table,
    GROUP_CONCAT(k.REFERENCED_COLUMN_NAME ORDER BY k.ORDINAL_POSITION) AS parent_columns,
    SUM(k.COLUMN_NAME = 'tenant_id') AS child_tenant_columns,
    SUM(k.REFERENCED_COLUMN_NAME = 'tenant_id') AS parent_tenant_columns
FROM information_schema.KEY_COLUMN_USAGE k
WHERE k.TABLE_SCHEMA = DATABASE()
  AND k.REFERENCED_TABLE_NAME IS NOT NULL
GROUP BY k.TABLE_NAME, k.CONSTRAINT_NAME, k.REFERENCED_TABLE_NAME
ORDER BY child_table, k.CONSTRAINT_NAME;

-- 4) Unique-index inventory. Tenant-owned natural keys should normally include tenant_id.
SELECT
    s.TABLE_NAME,
    s.INDEX_NAME,
    GROUP_CONCAT(s.COLUMN_NAME ORDER BY s.SEQ_IN_INDEX) AS index_columns,
    s.NON_UNIQUE
FROM information_schema.STATISTICS s
WHERE s.TABLE_SCHEMA = DATABASE()
GROUP BY s.TABLE_NAME, s.INDEX_NAME, s.NON_UNIQUE
ORDER BY s.TABLE_NAME, s.INDEX_NAME;

-- Operator decision required:
-- classify platform/global tables (for example tenant, subscription_plan, permission,
-- and shared/global role catalogue rows) separately from tenant-owned rows before
-- enforcing NOT NULL. Role/app_user can intentionally contain platform/global rows;
-- do not bulk alter their tenant_id without applying the SRS identity model.
