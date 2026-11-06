# Saumya Solar Energy Platform - Release 2

Clean, runnable Release 2 including Release 1 identity/tenant foundation and complete Sales Lifecycle.

## One-command clean start (Windows PowerShell)

```powershell
.\start-clean.ps1
```

This removes the local Docker volume, rebuilds MySQL/backend/frontend, and starts with deterministic seed data. **It
deletes local development data.**

Manual equivalent:

```powershell
docker compose down -v --remove-orphans
docker compose build --no-cache backend frontend
docker compose up
```

URLs: UI `http://localhost:3000`, Swagger `http://localhost:8080/swagger-ui/index.html`, health
`http://localhost:8080/actuator/health`.

## Local credentials

MySQL root: `root` / `rootpass`; application DB: `solar` / `solarpass`; database: `solar`.

Application users:

- Platform Super Admin: `platform@saumya.local` / `Platform@123`
- Vendor Admin: `admin@saumya.local` / `Admin@123`
- Vendor Sales User: `sales@saumya.local` / `Sales@123`
- Customer: `customer@saumya.local` / `Customer@123`

Development credentials only. Change them before any shared deployment.

## Role-specific UI

- Platform: platform dashboard, tenant/vendor list, tenant creation and activation.
- Vendor: catalogue, customers, enquiries, leads, surveys, quotations, approvals, orders and team lookup.
- Customer: own quotations, PDF download, accept/reject, own orders.

## Correct business flow

1. Vendor creates Category -> Product -> Solar Package with package components.
2. Vendor creates Customer or receives Enquiry.
3. Enquiry -> Lead; assign vendor employee, set priority/follow-up and transition stage.
4. Lead -> Site Survey; schedule, assign, capture roof/property/load details, complete.
5. Survey -> Quotation; add immutable product/package line snapshots; calculate subtotal, discount, tax and total.
6. Discount <=10% -> DRAFT. Discount >10% -> PENDING_APPROVAL -> approve/reject.
7. DRAFT -> SEND. New revision clones lines, marks old revision SUPERSEDED and increments revision.
8. Customer sees only their latest SENT quotation, downloads PDF, accepts/rejects. Expired/superseded quotes are
   blocked.
9. ACCEPTED quotation -> order conversion using `Idempotency-Key`. Repeating the key returns the same order; DB unique
   quotation ID blocks duplicates.

Lead stages:
`NEW -> CONTACTED -> SITE_VISIT_SCHEDULED -> SURVEY_COMPLETED -> QUOTATION_SENT -> NEGOTIATION -> WON/LOST`.
Quotation statuses: `DRAFT, PENDING_APPROVAL, APPROVAL_REJECTED, SENT, SUPERSEDED, ACCEPTED, REJECTED, EXPIRED`.

## 403 prevention

Permissions are created and assigned idempotently by `DataSeeder` before use. A clean start produces new JWTs with
correct authorities. UI navigation is separated by `accountType`; platform users are never shown vendor sales screens.
Backend `@PreAuthorize` remains authoritative.

## Release 2 acceptance test

Login as Vendor Admin, create category/product/package/customer/enquiry, convert to lead, assign `sales@saumya.local`,
update follow-up/stage, create/complete survey, create quote, approve >10% discount, send quote. Login as Customer,
download/accept it. Login as Vendor, convert to order twice with the same key and confirm the same order ID.

## Implementation matrix

| Feature                           | Backend     | Frontend    | Database    | Evidence / notes                                                                |
|-----------------------------------|-------------|-------------|-------------|---------------------------------------------------------------------------------|
| Tenant/vendor management          | Implemented | Implemented | Implemented | `Tenant`, `/api/v1/tenants`, Platform layout                                    |
| Roles/permissions                 | Implemented | Implemented | Implemented | JWT role+permission claims, role tables, seeded authorities                     |
| Platform/vendor/customer layouts  | Implemented | Implemented | N/A         | `main.tsx` dispatches by `accountType`                                          |
| Product categories                | Implemented | Implemented | Implemented | category entity/repo/service/UI                                                 |
| Products                          | Implemented | Implemented | Implemented | product entity/repo/service/UI                                                  |
| Solar packages                    | Implemented | Partial     | Implemented | backend package items supported; compact UI captures package core fields        |
| Customers                         | Implemented | Implemented | Implemented | customer profile CRUD creation/listing                                          |
| Enquiries/leads                   | Implemented | Implemented | Implemented | enquiry creation and convert action                                             |
| Lead assignment/follow-up/history | Implemented | Partial     | Implemented | service and tables exist; compact UI listing lacks full edit dialog             |
| Site surveys                      | Implemented | Partial     | Implemented | API/service and schema; compact UI lacks complete survey form                   |
| Versioned quotations              | Implemented | Implemented | Implemented | revision endpoint clones immutable line snapshots and supersedes prior revision |
| Discount approval                 | Implemented | Partial     | Implemented | >10% state and action supported; UI approval view limited                       |
| Quotation PDF                     | Implemented | Implemented | N/A         | authenticated PDF download action                                               |
| Customer accept/reject            | Implemented | Implemented | Implemented | customer-only quotation ownership list/actions                                  |
| Idempotent quote-to-order         | Implemented | Implemented | Implemented | stable per-quotation browser idempotency key + DB uniqueness                    |
| Access token                      | Implemented | Implemented | N/A         | JWT generated at login                                                          |
| Refresh/logout/password reset     | Implemented | API-ready   | Implemented | rotating refresh token, revocation, forgot/reset endpoints                      |
| Cross-tenant tests                | Missing     | N/A         | N/A         | must be added before production claim                                           |

### UI reset bug root cause

The previous async submit handler referenced `event.currentTarget` after an `await`. React does not guarantee that the
synthetic event's `currentTarget` remains available for asynchronous continuation, and a re-render/unmount can also
invalidate it. The fixed handler captures `const form = event.currentTarget` synchronously before any asynchronous
operation, then calls `form.reset()` only after a successful response. This also avoids modal/lifecycle races.

### Validation status

Frontend `npm run build` is intended as the first executable check. Backend Maven and Docker must also pass in the
target environment. Features marked Partial/Missing above are deliberately not claimed complete.

### Authentication APIs

`POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout`, `/forgot-password`, `/reset-password`. Local password
reset returns a development reset token; connect an email provider before production.
