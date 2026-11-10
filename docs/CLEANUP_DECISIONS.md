# Cleanup Decisions

**Repository:** `rambrij/saumya-solar-energy-product`  
**Only target branch:** `cleanup/code-cleanup`  
**Phase:** 0 — analysis only  
**Inspected baseline:** `d4acfa8edb8e6828299f6a8abcbe3e9e46178afe`  
**Authority:** `saumya-solar-energy-solution-requirements.md` (business behavior), then `saumya-solar-production-ready-srs-hld.md` (technical design); `IMPLEMENTATION_GAP_ANALYSIS.md` is evidence/context only. `README.md` is not a validation source.

## Conflicts

| Document | Section | Conflict | Resolution |
|---|---|---|---|
| Requirements and SRS/HLD | Requirements §17; SRS §§6–7 | No material architecture conflict identified: both describe Java 21/Spring Boot/React/MySQL and a modular monolith as the first production architecture. | Retain the modular-monolith implementation. Do not split modules into services or remove modules on architectural preference. |
| Requirements and SRS/HLD | Requirements §§13, 15–17; SRS §§8, 10, 13, 17–18 | No conflicting business behavior identified in the reviewed source documents. The SRS is more specific about tenant context, session security, API conventions and asynchronous execution. | Apply the business rules in the requirements document and the detailed technical controls in the SRS/HLD. |
| SRS/HLD and current implementation | SRS §13; actual `@RequestMapping` annotations in `backend/src/main/java/com/saumyasolar/api/` | Some SRS endpoint examples do not have identical path spelling to current controller mappings (for example, catalogue, customer-order and survey route families). This is a documentation-to-code contract reconciliation item, not proof that either route is dead. | Preserve current routes and frontend callers pending endpoint-by-endpoint reconciliation. Do not rename, remove or add compatibility shims in Phase 0. Record an approved contract change before changing any route. |
| SRS/HLD and current implementation | SRS §§10.13, 11, 13.8; current source tree | CMS/blog management is specified, but no dedicated CMS controller/domain/service/repository or CMS migration is visible in the inspected source inventory. | Treat as a required implementation gap. Keep all related public content and do not delete anything to make the tree appear aligned. |

## Removals

### Evidence-based removal inventory

**No source-code, dependency, configuration, environment-variable, script, test-fixture or SQL object is approved for deletion in Phase 0.** A removal requires all three: (1) no requirement in either authoritative document, (2) no code/config/runtime/test/script reference, and (3) no indirect role in a required workflow or schema relationship. A negative search alone is not enough to establish that a feature is unused.

| Path | Why it is unused | Evidence | Phase 0 disposition |
|---|---|---|---|
| _None approved_ | No candidate has yet met the full evidence threshold. | The source inventory contains active functional controllers, domain entities, repositories, service classes, frontend panels, tests, and migrations mapped in `docs/CLEANUP_TRACEABILITY.md`. The current API mappings and frontend calls must be reconciled before any route or class can be classified as dead. | Keep. Revisit only with path-specific reference and requirement evidence. |

### Consolidation inventory (not deletion approval)

| Path | Reason it may be retired after consolidation | Evidence / required proof | Disposition |
|---|---|---|---|
| `backend/src/main/resources/db/migration/V1__complete_release2.sql` | Will become the content source for the single baseline migration. | Flyway is used; V1 is the current initial schema and later migrations extend it. Preserve its schema effects in the new baseline. | Retain content; rename/rebuild only after schema equivalence test. |
| `backend/src/main/resources/db/migration/V2__vendor_employee_management_permission.sql` through `V61__install_integration_and_approval_permissions.sql` (each intervening V-file) | Versioned upgrade history is not needed for a clean, never-deployed environment after a single baseline is established. | The migration directory contains 61 ordered Flyway SQL files; several add constraints, security controls, operational entities, tenant composite FKs, seed/grant data and tenant NOT NULL enforcement. Every effect must be included in the baseline or explicitly justified as not required by the specs. | No deletion until old-sequence and baseline schema dumps have been compared on fresh MySQL 8.4 databases and the diff is saved to `docs/CLEANUP_SCHEMA_DIFF.md`. |

### Known non-removal constraints

- Preserve all tenant-owned tables, tenant composite foreign keys, uniqueness constraints, indexes, seed/reference rows and platform-vs-tenant distinctions required by SRS §§8–9 and 14–15.
- Preserve security/session controls, audit trails, role/permission checks, branch/customer/assigned-resource scoping, private attachments and tests.
- Preserve the scheduled jobs and queue-like database workflows until the asynchronous requirement and implementation choices are explicitly resolved.
- Preserve test fixtures that are used by automated tests or local onboarding; determine usage before classifying them as stale.
- Do not remove the audit scripts merely because they are not runtime code; they are invoked by CI today.

## Contract Changes

| Old | New | Reason | Document reference |
|---|---|---|---|
| No asynchronous export-job API existed | Add `POST /api/v1/exports`, `GET /api/v1/exports/{exportId}` and `GET /api/v1/exports/{exportId}/download`; retain all existing synchronous report/export routes unchanged. | Implement required RPT-005 persisted asynchronous exports with controlled status and download. | SRS §§10.14, 13.8, 16; RPT-005; NFR-PERF-005 |
| Notification delivery had no pre-publish persisted job guarantee | Persist a tenant/channel-idempotent `PENDING` delivery before publishing an event; recover stale pending rows from MySQL. | Prevent process restart from losing unprocessed business notification requests. | Requirements §15; SRS §§10.12, 17–18 |
| V1–V63 migration sequence | One V1 clean-install baseline containing the same schema and seed effects. | Product is pre-production; preserve schema effects while removing migration-history clutter. | SRS §§8, 17, 18; task scope |

Any later API, event, persisted-data or configuration contract change must be listed here before it is applied. Do not add compatibility layers: the environment is pre-production, but required documented contracts still have to be preserved.

## Open Questions

1. **Asynchronous notifications — resolved by user.** Use Spring `ApplicationEventPublisher` and `ApplicationListener` with a bounded Spring-managed asynchronous executor; use only in-process Spring application events and persisted MySQL work records; no separate messaging infrastructure. Business notification events are published after the surrounding transaction commits, and the listener establishes tenant execution context. Existing notification-delivery persistence and scheduled retries remain in place. Password-reset and MFA messages remain synchronous because authentication depends on immediate delivery success. Spring events are in-process and are not durable across a process crash; the implementation must not claim durability beyond persisted notification state.
2. **Asynchronous exports — implementation design accepted.** Add the SRS API contract `POST /api/v1/exports`, `GET /api/v1/exports/{exportId}` and an authenticated download route. Initial implementation supports tenant operations reports in CSV/XLSX/PDF; other report families can be added through the same export-job abstraction. Persist tenant, requester, format, parameters, status, timestamps, expiry and private object-storage key. Use `ApplicationEventPublisher`/`ApplicationListener`, a bounded async executor and scheduled recovery of pending/stale processing jobs. Existing synchronous export endpoints and response contracts remain unchanged. Access to status/download is restricted to the requesting user and tenant, and files expire after a configurable retention period.
2. **CMS/blog scope.** The requirements and SRS specify tenant-scoped blogs, banners, FAQs, testimonials, pages and publication lifecycle (Requirements §16; SRS §10.13 and §13.8), but the inspected tree does not expose a dedicated CMS backend module or schema. Is CMS expected in this cleanup's accepted feature baseline, or is it explicitly deferred? Until answered, retain the requirement and all possibly related public content.
3. **REST path reconciliation.** SRS §13 provides normative API examples; several current controller paths differ in naming. Should the current, frontend-consumed route set be adopted as the canonical contract, or should documented route examples be updated to match implementation? No route changes are approved pending this decision.
4. **Optional Redis/cache.** SRS §§7.1 and 16 describe Redis-compatible caching as optional for multi-instance coordination/rate limiting. No Redis service/dependency is evident in the inspected local compose and Maven dependency scan. Is Redis intentionally out of the first release? Keep optional configuration decisions out of scope until confirmed.
5. **CMS and public calculator parity.** The public screen catalogue includes calculator, legal, about/contact and blog screens. The current frontend is composed primarily through `PublicHome.tsx` and `main.tsx`; individual feature parity needs an explicit screen-by-screen acceptance review before any content or component removal.
6. **Seed data vs production defaults.** `DataSeeder.java` contains demo catalogue/customer/lifecycle data and default subscription plan seeding; V45 seeds default subscription plans. Which demo data should remain enabled only in local/test profiles, and which plan/reference values are required baseline seed data? Do not remove seeders or rows until their profile and business purpose are established.
7. **Local helper and audit scripts.** `start-clean.ps1` and the three `scripts/audit/*` files exist. The audit scripts are called by CI; the local helper's references/usage need checking. No script is approved for deletion yet.
8. **Single baseline naming and Flyway history — confirmed by task context.** The product has not been deployed. Replace V1–V63 with one clean-install Flyway baseline; no deployed-database upgrade path or compatibility migration is required. The schema-equivalence check must compare the historical sequence from Git commit `467dc8278e13902a39c51d1c9d8d8e588db98f57` with the baseline before deleting the source files. The final workflow will retrieve the historical SQL from Git history only for this comparison.
9. **Other workflow scope.** The repository contains a production preflight workflow with a hard-coded checkout ref. The requested single-branch policy should be applied consistently, but the intended operational status of a workflow named production preflight in a product not yet deployed should be confirmed before disabling or deleting it. Its database checks are not evidence of an actual production deployment.

## Verification

### Phase 0 checks performed

| Check | Result |
|---|---|
| Confirm only permitted branch exists in branch search | PASS — `cleanup/code-cleanup` returned as the target branch. |
| Read requirements document fully | PASS — 880 lines / 14,591 characters. |
| Read SRS/HLD fully | PASS — 1,388 lines / 65,492 characters. |
| Read implementation gap analysis fully | PASS — 1,560 lines / 239,969 characters. |
| Inspect recursive repository tree | PASS — 447 tree entries; no truncated tree response. |
| Inventory Flyway migrations | PASS — 1 file, V1 baseline (historical V1–V63 are retrieved from Git history only for equivalence validation). |
| Inventory GitHub workflows | PASS — two workflow files identified. |
| Inspect local compose, Maven POM, runtime application configuration and primary controllers | PASS — inspected. |
| Phase 0 source/config/SQL removals | NONE — no deletion was performed. |
| Build/tests/schema-dump comparison/app startup | NOT RUN — Phase 0 is documentation-only; no source changes have been made. |

### Phase 0 commands / evidence

- Repository tree: GitHub Git Trees API, recursive tree at baseline commit `d4acfa8edb8e6828299f6a8abcbe3e9e46178afe` (447 entries, `truncated=false`).
- Requirements/specification/gap analysis: full file retrieval from `cleanup/code-cleanup`; line counts recorded above.
- Migration inventory: directory listing of `backend/src/main/resources/db/migration/`; V1 through V61.
- Workflow inventory: `.github/workflows/release-validation.yml` and `.github/workflows/phase-2-production-preflight.yml`.
- Static route inventory: `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PatchMapping`, `@PutMapping` and `@DeleteMapping` annotations in the API controllers listed in the traceability matrix.
- Local clone/build execution was unavailable in this environment because outbound DNS/network access to GitHub failed. No build or test result is claimed from this inspection.

### Phase 1–4 verification evidence

**Full verification run:** GitHub Actions run `42878162886` — `success`  
**Schema-equivalence run:** GitHub Actions run `42877702249` — `success`  
**Resolved Maven dependency scan:** GitHub Actions run `42878486653` — scanner step passed; remaining jobs were running when this entry was prepared.

| Area | Command / check | Result |
|---|---|---|
| Whole-repository external messaging scan | `bash scripts/audit/scan-external-messaging.sh` | PASS — all repository text files scanned, including README for banned infrastructure terms only; README was not used as a requirements or feature-validation source. |
| Resolved Maven dependency graph | `mvn --batch-mode --no-transfer-progress dependency:tree -Dscope=test -DoutputType=text` (invoked by the scan script) | PASS — no matching external messaging libraries in the resolved graph (run 42878486653 scan step). |
| Async execution inventory | `bash scripts/audit/scan-async-workers.sh | tee async-worker-inventory.txt` | PASS — artifact uploaded. |
| Legacy authorization inventory | `bash scripts/audit/scan-legacy-authz.sh | tee legacy-authz-inventory.txt` | PASS — artifact uploaded. |
| Permission catalogue drift | `python3 scripts/audit/check-permission-catalogue.py backend | tee permission-catalogue-drift.txt` | PASS. |
| Backend build and full test suite | `cd backend && mvn --batch-mode --no-transfer-progress clean verify` | PASS — run 42878162886. |
| Frontend tests and production build | `cd frontend && npm install && npm test && npm run build` | PASS — run 42878162886. |
| MySQL migration equivalence | Historical V1–V63 from Git commit `467dc8278e13902a39c51d1c9d8d8e588db98f57` applied to a fresh MySQL 8.4 schema; baseline applied to a second fresh schema; schema-only `mysqldump` outputs compared with `diff -u`. | PASS — empty schema diff; artifact `cleanup-schema-equivalence`; run 42877702249. |
| Exactly one Flyway migration | Recursive Git tree inspection of `backend/src/main/resources/db/migration/` | PASS — only `V1__baseline.sql`. |
| MySQL scheduler concurrency | `mvn --batch-mode --no-transfer-progress -Dtest=TenantSchedulerConcurrencyIntegrationTest test` | PASS — 4 tests, 0 failures, 0 errors. |
| Fresh database application startup | `java -jar target/platform-2.0.0.jar`; poll `curl --fail --silent http://127.0.0.1:8080/actuator/health` | PASS — Flyway applied the single V1 baseline; Hibernate schema validation passed; health returned UP. |
| Tenant integrity preflight | `mysql ... solar < backend/src/test/resources/tenant-isolation-production-preflight.sql` | PASS — V57/V58 tenant integrity preflight. |
| Browser tenant isolation | `cd frontend && npm run test:e2e` | PASS — browser-level tenant filter tampering tests. |
| CI branch restrictions | Inspect both workflow files for trigger and checkout refs | PASS — only `cleanup/code-cleanup` appears as a branch target/ref. |

**Remaining scope note:** no unproven dead-code or orphan-file removals were made. The traceability and removal inventories must continue to be used for any further deletion; documented CMS/blog gaps and export-family/filter parity remain open questions, not cleanup candidates.
