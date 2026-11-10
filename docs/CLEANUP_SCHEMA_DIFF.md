# Cleanup schema equivalence

## Scope

Compare the historical Flyway sequence V1–V63 from pre-consolidation commit `467dc8278e13902a39c51d1c9d8d8e588db98f57` with the single clean-install baseline using two independent, empty MySQL 8.4 databases. The historical files are extracted from Git history to `/tmp/historical-source/backend/src/main/resources/db/migration/`; they are no longer present in the working migration directory.

## Commands executed by CI

The `mysql-migrations` job checks out full Git history, extracts the old migration files, applies the historical sequence to `solar_migration_test`, and applies the new baseline to `solar_baseline_test`:

```bash
git archive 467dc8278e13902a39c51d1c9d8d8e588db98f57 backend/src/main/resources/db/migration | tar -x -C /tmp/historical-source
mysql -h 127.0.0.1 -uroot -prootpass solar_baseline_test < backend/src/main/resources/db/migration/V1__baseline.sql
mysqldump -h 127.0.0.1 -uroot -prootpass --no-data --skip-comments --skip-add-drop-table --skip-set-charset --skip-lock-tables solar_migration_test > /tmp/historical-schema.sql
mysqldump -h 127.0.0.1 -uroot -prootpass --no-data --skip-comments --skip-add-drop-table --skip-set-charset --skip-lock-tables solar_baseline_test > /tmp/baseline-schema.sql
diff -u /tmp/historical-schema.sql /tmp/baseline-schema.sql
```

The workflow normalizes `AUTO_INCREMENT` counters before comparison and fails on any non-empty diff. It uploads the result as the `cleanup-schema-equivalence` artifact.

## Result

**PASS — schema diff is empty.** CI run `42877367240` applied historical V1–V63 and the consolidated baseline to separate fresh MySQL 8.4 databases. The schema dump comparison returned identical output. The same consolidated SQL blob is installed at `backend/src/main/resources/db/migration/V1__baseline.sql`.

The baseline retains all historical schema and seed effects, including the notification idempotency index (historical V62) and tenant-scoped asynchronous export-job table (historical V63). No schema object was removed as part of consolidation.

## Allowed differences

None. No schema differences are approved.
