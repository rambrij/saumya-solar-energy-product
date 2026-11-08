# Demo data and test users

## 1. Feature purpose
Provide safe local demo identities and reproducible tenant/catalogue baseline data for manual verification.

## 2. Supported roles and permissions
Local demo roles: Platform Super Admin, Vendor Admin, Sales user and Customer. Demo seed is disabled unless explicitly enabled.

## 3. Preconditions
Disposable local MySQL database; `SEED_DATA_ENABLED=true` or `app.seed.enabled=true`; never enable in production.

## 4. Business rules
Seeder is gated and intended to be idempotent; existing emails/categories/SKUs are checked before insertion; passwords are BCrypt/PasswordEncoder encoded; do not reuse demo passwords outside local development.

## 5. Detailed user flow
Start Compose with demo seeding enabled, sign in with role-specific account, inspect tenant catalogue and use manual workflow steps in `13-end-to-end-testing-guide.md`.

## 6. Frontend routes and screens
SPA `/` login page; README local startup and role navigation sections.

## 7. Backend APIs
Authentication `POST /api/v1/auth/login`; current user `GET /api/v1/me`; seed implementation `DataSeeder` and Flyway V45/V46.

## 8. Request and response examples
Local credentials: `platform@saumya.local / Platform@123`; `admin@saumya.local / Admin@123`; `sales@saumya.local / Sales@123`; `customer@saumya.local / Customer@123`. Change these before any shared environment.

Successful login response shape: `{"accessToken":"<redacted-jwt>","user":{"id":"<user-uuid>","email":"customer@saumya.local","accountType":"CUSTOMER"}}`. Never paste live tokens into tickets or documentation.
## 9. Database entities and relationships
Seed tenant `saumya-solar`; seeded roles/users, customer profiles, categories, products and warehouse/checklist templates; plans inserted by V45; assignments/history stored in V46 tables.

## 10. Validation rules
Do not seed when `SEED_DATA_ENABLED=false`; use a disposable database; ensure unique emails and tenant slugs; use PasswordEncoder, never plaintext storage.

## 11. Status transitions
Seeded user roles map to role-specific navigation and backend permissions; the full lead-to-service lifecycle is not yet guaranteed to be populated automatically by current DataSeeder.

## 12. Error and edge cases
Run with seed enabled on a fresh local database; sign in as each demo account; verify tenant separation and expected menus; restart and confirm no duplicate users/categories/products/plans; disable seed and verify no demo inserts occur on a clean database.

## 13. Manual testing instructions
Current documented baseline seed creates tenant, roles, demo users, category/product catalogue, warehouse and checklist templates; V45 adds three default plans. Additional fully connected lead→service sample records remain a known gap until lifecycle seeder is implemented.

## 14. Expected results
```mermaid\nflowchart TD\n A[Disposable local DB] --> B[Enable seed flag]\n B --> C[Flyway schema and plan seed]\n C --> D[DataSeeder users and catalogue]\n D --> E[Sign in with demo roles]\n E --> F[Run lifecycle verification]\n```

## 15. Business flow diagram
The existing seeder does not yet create a complete connected lead → enquiry → quotation → survey → order → installation → service-request graph. Do not represent this dataset as complete lifecycle demo data.

## 16. Known limitations
undefined
