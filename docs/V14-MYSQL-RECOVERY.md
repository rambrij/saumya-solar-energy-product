# V14 MySQL migration recovery

## What failed

The original V14 migration added `order_item.tax_percent` and then attempted to create two inventory-ledger triggers. Managed MySQL accounts commonly cannot create triggers while binary logging is enabled unless they have the SUPER privilege or the server administrator enables `log_bin_trust_function_creators`. The trigger DDL failed with MySQL error 1419.

MySQL DDL is not fully transactional. The `tax_percent` column may already exist even though Flyway reports V14 as failed. Do not drop tables, restore an old schema over live data, or blindly rerun the old SQL.

## Code correction

- V14 no longer creates database triggers and is now a compatibility marker.
- V15 is an idempotent Flyway SQL migration. It checks `information_schema` and adds `tax_percent` only if it is missing.
- Inventory ledger rows are append-only through JPA lifecycle guards. Corrections must be new compensating movements, not updates/deletes.
- Database triggers are deliberately not required, so the application DB user does not need SUPER privileges.

## Safe recovery procedure

1. Stop the backend container/service so Flyway does not run during recovery.
2. Take a provider snapshot/backup of the database before changing migration metadata. For a database reachable with a MySQL client, an example is:

   ```bash
   mysqldump --single-transaction --routines --triggers --hex-blob -h DB_HOST -u DB_USER -p DB_NAME > before-v14-repair.sql
   ```

3. Inspect the existing schema and Flyway history:

   ```sql
   SELECT DATABASE();
   SHOW COLUMNS FROM order_item LIKE 'tax_percent';
   SELECT installed_rank, version, description, script, success
   FROM flyway_schema_history
   WHERE version = '14';
   ```

4. If and only if the V14 row exists with `success = 0`, remove that failed history row after the backup:

   ```sql
   DELETE FROM flyway_schema_history
   WHERE version = '14' AND success = 0;
   ```

   Do not delete a successful row. Do not delete any other Flyway history row. This only clears Flyway's failed-attempt marker; it does not delete business data or undo the already-applied column DDL.

5. Deploy the updated application. Flyway will re-run the V14 marker, then V15 will add `tax_percent` only if it is absent. Hibernate schema validation must remain enabled.

6. Verify:

   ```sql
   SHOW COLUMNS FROM order_item LIKE 'tax_percent';
   SELECT version, description, success
   FROM flyway_schema_history
   WHERE version IN ('14', '15')
   ORDER BY installed_rank;
   ```

   Confirm the application health endpoint is UP and inspect backend logs for successful Flyway migration and Hibernate initialization.

## Rollback

Do not drop `order_item.tax_percent` as a rollback: existing order tax snapshots may depend on it. Do not restore a database backup unless the owner explicitly chooses a point-in-time data restore and accepts losing all changes made after that backup. This recovery is forward-only and preserves existing application data.
