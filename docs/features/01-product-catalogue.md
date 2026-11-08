# Product catalogue

## 1. Feature purpose
Maintain tenant-owned solar products, categories and packages used by customer discovery, enquiries, quotations and orders.

## 2. Supported roles and permissions
Vendor Admin (catalogue management), authorized Vendor users (catalogue view), Customer (active public/customer catalogue). Platform Super Admin manages tenants, not tenant product records.

## 3. Preconditions
An active tenant and authenticated vendor session are required for management. Public catalogue reads require an active store context.

## 4. Business rules
Categories and products are tenant-scoped; product category must be active and belong to the same tenant; prices and GST are validated server-side; archive is preferred over destructive removal when referenced.

## 5. Detailed user flow
Vendor opens catalogue, creates/updates categories and products, then optionally assembles packages. Customers browse active products/packages and select a product to enquire or request a quotation.

## 6. Frontend routes and screens
SPA `/` → vendor Catalogue tab; customer Available Products selector; public `/products` catalogue page.

## 7. Backend APIs
`GET /api/v1` (tenant-scoped catalogue list); `POST /api/v1/catalog/products`; `PATCH /api/v1/products/{id}`; `DELETE /api/v1/products/{id}`; `PATCH /api/v1/categories/{id}`; `DELETE /api/v1/categories/{id}`; public `GET /api/v1/public/{storeSlug}/products` and `/packages`.

## 8. Request and response examples
`POST /api/v1/catalog/products` illustrative body: `{"name":"540W Mono PERC Solar Panel","sku":"SP-540-MONO","price":18500,"taxPercent":18,"categoryId":"<same-tenant-category-uuid>"}`. Response is the persisted product DTO/entity; use actual returned UUIDs for later workflow records.

## 9. Database entities and relationships
`ProductCategory` 1—N `Product`; `SolarPackage` 1—N `PackageItem`; product and category rows carry `tenant_id`.

## 10. Validation rules
Name/SKU and category required; price non-negative; tax percentage bounded by request validation; category must be active and same-tenant; unsafe image URL schemes are rejected by upload/branding flows.

## 11. Status transitions
Active products are discoverable; inactive/archived products are excluded from public catalogue and cannot be selected for new quotations/packages.

## 12. Error and edge cases
Duplicate SKU/category, foreign-tenant category, archived product references, missing price/category and malformed package items return validation/conflict errors. Existing referenced records should be archived rather than physically removed.

## 13. Manual testing instructions
Sign in as Vendor Admin; create a category; create a product with price/GST; verify list; edit price; archive product; sign in as Customer and verify only active products appear in Available Products; verify public catalogue reads.

## 14. Expected results
The product is saved once in the current tenant; customer/public discovery excludes archived products; existing quotations retain their stored financial snapshots.

## 15. Business flow diagram
```mermaid\nflowchart TD\n A[Vendor creates category] --> B[Vendor creates product]\n B --> C[Product appears in tenant catalogue]\n C --> D[Customer browses active products]\n D --> E[Enquiry or quotation request]\n```

## 16. Known limitations
Public catalogue is a single-page application view rather than a separate route tree. Product image metadata is not certification evidence; the platform does not independently verify manufacturer claims.
