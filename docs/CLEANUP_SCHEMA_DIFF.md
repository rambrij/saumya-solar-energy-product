# Flyway schema equivalence — cleanup baseline

**Schema comparison status:** PASS on final branch-head CI run 43089380154 (MySQL 8.4)  
**Old schema source:** `audit/implementation-gap-analysis` active Flyway V1–V14 chain  
**New schema source:** `backend/src/main/resources/db/migration/V1__baseline.sql`  
**Database engine:** MySQL 8.4  
**Scope:** Fresh empty databases only; this does not migrate or alter a deployed database.

## Procedure

The `mysql-migrations` job in `.github/workflows/release-validation.yml` performs the comparison:

1. Archives the original 14 active versioned migration files from the pinned base commit `589d7b838f1745570abab6e4f1c756e32747612a` (the requested `audit/implementation-gap-analysis` branch head).
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

<badge>PASS</badge>

**Identical schema — zero differences.** Final branch-head GitHub Actions run [43089380154](https://github.com/rambrij/saumya-solar-energy-product/actions/runs/43089380154), commit `deb61269572b19799bcc72685475c92c2e4f9a5e`, completed the full MySQL 8.4 migration job successfully. It applied the original V1–V14 chain and the consolidated V1 baseline to separate fresh databases, dumped both schemas including triggers, normalized generated AUTO_INCREMENT counters and found no diff.

Final artifact: [cleanup-schema-equivalence, artifact 13888520692](https://github.com/rambrij/saumya-solar-energy-product/actions/runs/43089380154). The downloaded `schema-diff.txt` contains exactly:

```text
PASS: old V1-V14 schema and the single baseline are identical.
```

The job also passed the one-baseline layout assertion and the updated tenant schema/foreign-key/index/NULL-row audit. No schema differences or documented-requirement deviations needed justification.

## Data and seed behavior

The comparison is a schema-only dump by design. Seed and reference SQL from all 14 original migration files is retained in the baseline in source migration order after DDL. Seed behavior is validated by baseline startup and the tenant/schema assertions in CI. No production data migration, backfill, deletion or Flyway history rewrite is part of this cleanup.
