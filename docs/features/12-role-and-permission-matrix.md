# Role and permission matrix

## 1. Feature purpose
Document the separation between platform administration, vendor operations and customer self-service.

## 2. Supported roles and permissions
Platform Super Admin; Platform User (if provisioned with platform permissions); Vendor Admin; Vendor employee roles; Customer.

## 3. Preconditions
User must authenticate; roles and permissions are loaded from persisted role mappings; tenant status and user enabled state are rechecked by JWT request filter.

## 4. Business rules
Backend permission checks are authoritative; frontend menu visibility is not security; vendor records must be tenant-scoped; customer records must be ownership-scoped; platform subscription APIs require Platform Super Admin plus `SUBSCRIPTION_MANAGE`.

## 5. Detailed user flow
Platform provisions tenant/vendor admin; vendor admin provisions allowed employee roles; users see permitted tabs/actions; customers access only their own catalogue/enquiries/quotations/orders.

## 6. Frontend routes and screens
SPA `/` renders role-aware views; permissions determine vendor operations and platform panel visibility.

## 7. Backend APIs
Authentication `POST /api/v1/auth/login`, `GET /api/v1/me`; platform tenant routes `/api/v1/platform/tenants`; subscription administration `/api/v1/platform/subscription-plans` and `/api/v1/platform/tenants/...`; customer endpoints `/api/v1/customer/...`.

## 8. Request and response examples
Example permission-gated operation: Platform plan create requires authenticated principal with account type `PLATFORM`, role `PLATFORM_SUPER_ADMIN` and permission `SUBSCRIPTION_MANAGE`; missing permission returns 403.

## 9. Database entities and relationships
`AppUser` N—M `Role`; `Role` N—M `Permission`; vendor business records include `tenant_id`; customer profile maps authenticated user to customer-owned records.

## 10. Validation rules
JWT must be valid; account enabled; tenant active; role/permission current; referenced entity same-tenant or owned by customer.

## 11. Status transitions
Role changes take effect through request-time principal/permission revalidation; business status transitions are defined by individual services.

## 12. Error and edge cases
Test each role independently; attempt platform APIs as vendor/customer; attempt foreign tenant IDs; verify 403 or non-disclosing 404; remove permission and retry; verify hidden menu is not sole control.

## 13. Manual testing instructions
Platform subscription APIs are restricted to Platform Super Admin; vendor/customer APIs remain constrained by tenant/ownership rules.

## 14. Expected results
```mermaid\nflowchart TD\n A[Authenticate user] --> B[Load current roles and permissions]\n B --> C{Platform account?}\n C -- Yes --> D[Platform-authorized endpoints]\n C -- No --> E{Vendor account?}\n E -- Yes --> F[Tenant and permission checks]\n E -- No --> G[Customer ownership checks]\n```

## 15. Business flow diagram
A distinct generic Platform User role is not seeded as a login; the seeded privileged account is Platform Super Admin. Use least privilege when adding additional platform operators.

## 16. Known limitations
undefined
