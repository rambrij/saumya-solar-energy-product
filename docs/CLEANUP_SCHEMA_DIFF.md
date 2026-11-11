# Flyway schema equivalence — cleanup baseline

**Status:** Awaiting branch-head GitHub Actions result  
**Old schema source:** `audit/implementation-gap-analysis` active Flyway V1–V14 chain  
**New schema source:** `backend/src/main/resources/db/migration/V1__baseline.sql`  
**Database engine:** MySQL 8.4  
**Scope:** Fresh empty databases only; this does not migrate or alter a deployed database.

## Procedure

The `mysql-migrations` job in `.github/workflows/release-validation.yml` performs the comparison:

1. Fetches `audit/implementation-gap-analysis` and archives its original 14 active versioned migration files.
2. Creates two empty MySQL 8.4 databases: `solar_old_chain` and `solar_baseline_test`.
3. Applies the old V1–V14 migrations in Flyway version order to `solar_old_chain`.
4. Applies the consolidated V1 baseline to `solar_baseline_test`.
5. Dumps schema-only SQL for both databases, including triggers, and normalizes generated AUTO_INCREMENT counters.
6. Diffs the two normalized schema dumps. Any difference fails the job and is retained as the `cleanup-schema-equivalence` artifact.
7. Runs the tenant schema, foreign-key, unique-index and NULL-row audit against the new baseline database.

## Expected schema inventory

| Item | Consolidated baseline |
|---|---:|
| `CREATE TABLE` statements | 84 |
| `ALTER TABLE` statements | 14 |
| Standalone indexes | 6 |
| Triggers | 8 |
| Seed/initialization SQL statements | 67 |
| Unclassified SQL statements | 0 |

The 84 tables include the original 74-table baseline plus ten tables introduced by the later migration chain. Later migration effects also include reservation expiry metadata, tenant lifecycle metadata, persisted order transition rules, finance reconciliation state, immutable CMS revisions, in-app inbox, typed platform settings, operational analytics indexes, notification outbox/preferences/provider events, notification status checks and tenant-matched composite foreign keys.

## Result

**Pending CI result**

No schema equivalence claim is made until a successful MySQL 8.4 GitHub Actions run on the final branch HEAD is observed. After that run, this section will record its run URL, commit SHA, artifact name and the actual diff result. Any non-empty diff must be investigated and each accepted difference justified against the SRS/HLD before this document can be marked equivalent.

## Data and seed behavior

The comparison is a schema-only dump by design. Seed and reference SQL from all 14 original migration files is retained in the baseline in source migration order after DDL. Seed behavior is validated by baseline startup and the tenant/schema assertions in CI. No production data migration, backfill, deletion or Flyway history rewrite is part of this cleanup.
