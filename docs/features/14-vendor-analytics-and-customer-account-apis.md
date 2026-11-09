# Vendor Analytics and Customer Account APIs

All endpoints use the /api/v1 base path and JSON responses. Protected endpoints require the existing JWT bearer authentication. Date-time filters use ISO-8601 UTC timestamps.

## Vendor analytics

All vendor analytics endpoints are restricted to a VENDOR account with the VENDOR_ADMIN role and both REPORT_VIEW and BRANCH_MANAGE permissions. Tenant ID is derived from the authenticated principal. Optional branch IDs are checked against that tenant.

### GET /vendor/analytics/breakdowns

Returns product and category aggregates.

| Parameter | Type | Required | Description |
|---|---|---:|---|
| from | ISO-8601 Instant | No | Inclusive start; defaults to 90 days before to |
| to | ISO-8601 Instant | No | Inclusive end; defaults to now |
| branchId | UUID | No | Branch within authenticated tenant |
| categoryId | UUID | No | Restrict category/product results |
| productId | UUID | No | Restrict product results |

Response contains categories and products arrays with id, name, orders, revenue, leads and enquiries (product rows also contain categoryId).

### GET /vendor/analytics/trends

Uses the same date and dimension filters, plus granularity (DAY, WEEK, MONTH; defaults to DAY). Returns granularity and items; each item contains period, leads, enquiries, orders and revenue.

### GET /vendor/analytics/rankings

Uses the same date and dimension filters, plus metric (orders, revenue, leads; defaults to orders) and limit (integer 1–50; defaults to 10). Returns topProducts and topCategories ordered by the selected metric.

Analytics date ranges are limited to 366 days. Invalid granularity, metric, limit or date ranges return HTTP 400. Cross-tenant branches return HTTP 404.

## Platform resource usage

### GET /platform/analytics/tenant-usage

Allowed to Platform Super Admin with SUBSCRIPTION_MANAGE. Parameters: page (zero-based, default 0) and size (1–100, default 20). Returns a paginated items collection with tenant identity/status, current plan and usage/limit/remaining values for users, products, branches, warehouses and storage MB. Tenants without a plan return a null limit rather than a fabricated quota.

## Customer profile

### GET /customer/profile

Returns the authenticated customer's own profile. Allowed to CUSTOMER.

### PATCH /customer/profile

Allowed to CUSTOMER. Request fields: name (required, max 150) and phone (optional, max 30). Email, account role, tenant and user IDs are not writable. Customer identity and tenant are resolved from the authenticated principal.

## Customer saved addresses

All address endpoints are restricted to CUSTOMER and resolve the customer profile from the authenticated principal. Client-supplied tenant/customer/user identifiers are not accepted.

### GET /customer/addresses

Lists the authenticated customer's addresses.

### POST /customer/addresses

Creates an address. Fields: label, line1, line2, city, state, pincode, country, landmark and isDefault. First saved address becomes default unless an explicit default is selected.

### PUT /customer/addresses/{id}

Updates an address owned by the authenticated customer.

### DELETE /customer/addresses/{id}

Deletes an owned address. If the default address is deleted, the most recently created remaining address becomes default.

Address validation failures return HTTP 400. Flyway migrations V49 and V50 add the customer address table and optional enquiry address snapshot. A foreign or unknown address ID returns HTTP 404. The existing POST /customer/enquiries request accepts an optional addressId. The API verifies ownership against the authenticated customer's tenant/profile and stores an address snapshot on the enquiry. Existing requests without addressId remain compatible and fall back to the customer's existing profile address. Product-order and quotation-acceptance payloads currently have no delivery/site-address field; adding one requires a separate order snapshot/fulfilment decision.
