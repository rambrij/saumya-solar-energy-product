# End-to-end testing guide

## 1. Feature purpose
Validate the integrated business and subscription workflows using isolated test data and role-separated sessions.

## 2. Supported roles and permissions
Platform Super Admin, Vendor Admin, Vendor Sales user, Customer; additional technician/service roles when configured.

## 3. Preconditions
Fresh MySQL 8.4 database; apply all Flyway migrations; run backend/frontend; enable demo seed only for disposable local testing; use separate browser sessions for roles.

## 4. Business rules
Never bypass backend authorization; keep tenant IDs and customer IDs from authenticated responses; use stable idempotency keys; preserve test data and inspect audit/history after state changes.

## 5. Detailed user flow
Execute platform setup → tenant subscription → product setup → customer enquiry → lead → quotation → site survey → order → installation → service request. Capture UUIDs and expected statuses at each step.

## 6. Frontend routes and screens
SPA `/` role-specific navigation; API calls below are under `/api/v1`.

## 7. Backend APIs
Platform plans: `/platform/subscription-plans`; tenant search/assign: `/platform/tenants/search`, `/platform/tenants/{id}/subscriptions`; catalogue: `/catalog/products`; enquiry: `/customer/enquiries`; lead: `/enquiries/{id}/convert`, `/leads/{id}`; quotation: `/quotations`; survey: `/surveys`; order: `/quotations/{id}/convert-to-order`; installation: `/installations`; service: service operations UI and warranty claim routes `/warranty-claims`.

## 8. Request and response examples
Example API assertions: platform plan create returns 201/200 and unique `planCode`; tenant assignment returns status TRIAL/ACTIVE; quotation conversion repeated with same idempotency key returns the same order identity; foreign-tenant requests return 403/404.

## 9. Database entities and relationships
Verify tenant, plan, tenant_subscription, subscription_assignment_history, product/category, customer, enquiry, lead/lead_history, quotation/items/revisions, site_survey/history, customer_order/order_item/status_history, installation/checklist/history and service/warranty entities.

## 10. Validation rules
Use valid UUIDs; prices/taxes are server-derived; end dates after start; product and customer references tenant-owned; customer ownership checked; status transitions enforced; plan entitlements and limits enforced after assignment.

## 11. Status transitions
Capture state at every transition; invalid/unauthorized operations must not mutate data; subscription replacement must append history; quotation acceptance precedes order conversion; mandatory installation checklist precedes completion.

## 12. Error and edge cases
1) Sign in Platform Super Admin; create/edit Starter/Professional/Enterprise and confirm one each. 2) Search tenant and assign plan/trial. 3) Sign in Vendor Admin; create category/product and verify GST. 4) Sign in Customer; submit enquiry. 5) Vendor converts enquiry to lead and assigns Sales user. 6) Create and approve/send quotation; customer accepts. 7) Schedule and approve site survey. 8) Convert accepted quotation to order with idempotency key. 9) Schedule installation, complete checklist and commission. 10) Customer/vendor creates service request or warranty claim; resolve according to state rules. 11) Verify audit/history and tenant ownership. 12) Repeat critical negative tests.

## 13. Manual testing instructions
Expected: each stage links to the previous record; only one order exists for an idempotent conversion; current subscription/plan reflects assignment; history rows retain old and new state; no cross-tenant or unauthorized data is exposed.

## 14. Expected results
```mermaid\nflowchart TD\n A[Platform setup] --> B[Tenant subscription]\n B --> C[Product setup]\n C --> D[Customer enquiry]\n D --> E[Lead qualification]\n E --> F[Quotation and approval]\n F --> G[Site survey]\n G --> H[Order conversion]\n H --> I[Installation and commissioning]\n I --> J[Service request]\n```

## 15. Business flow diagram
Automated browser E2E coverage is not currently configured in the repository. The current demo seeder does not yet populate the complete linked lifecycle; create test records through the supported UI/APIs until the lifecycle seeder and browser suite are added.

## 16. Known limitations
undefined
