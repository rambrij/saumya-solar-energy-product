# Cleanup schema equivalence

## Scope

Compare the complete historical Flyway sequence (V1–V63) with the consolidated clean-install baseline using two independent, empty MySQL 8.4 databases. The historical sequence remains in `backend/src/main/resources/db/migration/` during this validation pass; the candidate baseline is `backend/src/test/resources/db/baseline/V1__baseline.sql`.

## Commands executed by CI

```bash
mysql -h 127.0.0.1 -uroot -prootpass -e "CREATE DATABASE solar_baseline_test"
mysql -h 127.0.0.1 -uroot -prootpass solar_baseline_test < backend/src/test/resources/db/baseline/V1__baseline.sql
mysqldump -h 127.0.0.1 -uroot -prootpass --no-data --skip-comments --skip-add-drop-table --skip-set-charset --skip-lock-tables solar_migration_test > /tmp/historical-schema.sql
mysqldump -h 127.0.0.1 -uroot -prootpass --no-data --skip-comments --skip-add-drop-table --skip-set-charset --skip-lock-tables solar_baseline_test > /tmp/baseline-schema.sql
diff -u /tmp/historical-schema.sql /tmp/baseline-schema.sql
```

The workflow normalizes `AUTO_INCREMENT` counters before comparison and uploads the diff output as an artifact.

## Result

**Pending CI execution.**

No schema equivalence claim is made until the MySQL 8.4 comparison job passes. The baseline includes all original V1–V61 effects plus the new SRS-required notification idempotency index (V62) and tenant-scoped asynchronous export-job table (V63). These are additions to the documented design, not removals from the historical schema.

## Allowed differences

None approved. Any non-empty schema diff must be investigated and justified against the requirements and SRS/HLD before the old migrations can be deleted.
