# Soumya Solar — Business Flow

## 1. Actors and authority

| Actor | Scope | Responsibilities | Must not do |
|---|---|---|---|
| Platform Super Admin | Platform-wide | Create/suspend vendor tenants; provision initial Vendor Admin; oversee tenant lifecycle | Operate as a vendor employee or access customer/vendor data without an explicitly authorized platform capability |
| Vendor Admin | Own vendor tenant | Manage vendor catalogue, customer and employee access, policies, sales and operations; provision tenant employees | Create platform identities or grant platform-wide privileges |
| Vendor Sales User | Own vendor tenant | Manage assigned leads, customers, surveys, quotations and customer order follow-up according to granted permissions | Manage inventory, teams, or privileged users unless separately granted |
| Inventory Manager | Own vendor tenant | Manage warehouses, receipts, issues, transfers, adjustments and stock reservations | Access another vendor's inventory or approve unrelated privileged actions |
| Operations Manager / Service Manager | Own vendor tenant | Plan installations, assign eligible engineers, manage checklist templates, commissioning and warranty visibility | Access another tenant or change stock without inventory permission |
| Engineer / Installer / Technician | Assigned vendor tenant work | Execute assigned site work, update checklist evidence and submit commissioning details | View or modify unassigned installations or manage tenant configuration |
| Customer | Own vendor store membership and profile | Browse active products, submit enquiries, place product orders, decide quotations, track own orders/installations/warranties | Access another customer's or another vendor's records |

Permissions on the server are authoritative. Frontend navigation is only a usability layer. Tenant context is derived from the authenticated principal; a browser-provided tenant identifier is never an authorization source.

## 2. Tenant onboarding and identity

1. Platform Super Admin signs in to the platform workspace.
2. Platform Super Admin creates a vendor tenant and provisions its initial Vendor Admin.
3. The Vendor Admin signs in within the tenant context.
4. Vendor Admin provisions vendor employees using only the tenant's permitted employee roles.
5. Customers register through the vendor store slug. Registration binds the customer identity and customer profile to that vendor tenant.
6. Login, refresh, logout, password reset, account enabled state, current roles and tenant status are enforced by the backend.

## 3. Catalogue and sales lifecycle

1. Vendor Admin / authorized catalogue user creates categories and products.
2. Product record includes SKU, manufacturer, model, technology, description, purchase price, sale price, discount, GST percentage, HSN, warranty, unit, reorder threshold, image, datasheet and technical attributes.
3. Vendor publishes active products and configurable solar packages.
4. Customer browses the vendor catalogue or submits an enquiry.
5. Vendor Sales User qualifies the enquiry, links/creates the customer profile, assigns a same-tenant employee and tracks follow-up.
6. Vendor Sales User schedules and completes a site survey.
7. Vendor Sales User creates a quotation using catalogue-derived price and tax snapshots.
8. Discount above the tenant threshold requires an authorized discount approver.
9. Vendor sends a versioned quotation PDF to the customer.
10. Customer accepts or rejects the exact sent quotation revision.
11. Vendor converts an accepted quotation to one order using an idempotency key.

## 4. Direct catalogue product order

1. Customer opens an active product in the correct vendor store.
2. UI displays technical specifications, manufacturer/model, discount, GST, HSN, warranty, image and tax-inclusive price.
3. Customer chooses **Order now** and confirms the request.
4. Backend resolves the authenticated customer's profile and the product from the same tenant. Price, discount and GST are calculated from server-side catalogue data.
5. Backend creates an order with status `PENDING_VENDOR_ACCEPTANCE`, immutable line price/tax snapshot, and an order-history entry.
6. Vendor Admin or authorized Vendor Sales User opens Operations → Orders.
7. Vendor accepts with a reason/note or rejects with a mandatory reason.
8. Accept changes the order to `CONFIRMED`; reject changes it to `REJECTED`. Both decisions are recorded in order history with actor and timestamp.
9. Customer sees the current order status in Customer Portal → My Orders.
10. Only accepted orders may proceed to stock reservation and fulfilment.

## 5. Inventory and fulfilment

1. Inventory Manager configures warehouses.
2. Inventory Manager records supplier receipts and stock returns, and records issues, damage, transfers or adjustments with reasons.
3. Each movement appends an immutable inventory transaction. The balance is updated in the same transaction.
4. For a confirmed order, Inventory Manager selects a warehouse and requests reservation.
5. Backend derives required products from the order and required package components, locks the warehouse and stock balances, verifies availability and creates reservation records.
6. Concurrent reservation requests serialize against the stock lock; stock cannot be reserved below zero.
7. Reservations may be released before installation scheduling. Once installation has been scheduled, the current workflow requires operations to resolve the installation state before releasing stock.
8. Inventory ledger is append-only; database triggers reject update/delete attempts.

## 6. Installation and commissioning

1. Operations Manager configures installation teams with enabled same-tenant engineers/installers.
2. Operations Manager configures mandatory and optional checklist templates.
3. Operations Manager schedules an installation only for an order with reserved materials.
4. The assigned engineer opens only their own assigned installation unless they have an elevated operations role.
5. Engineer records checklist completion, notes and evidence.
6. First field checklist progress moves the order to `INSTALLATION_IN_PROGRESS`.
7. Installation cannot be marked complete while mandatory checklist items are incomplete.
8. Completion consumes reserved stock and moves the order to `INSTALLED`.
9. Engineer records commissioning notes and customer sign-off. Serialized products require serial numbers for every ordered unit.
10. Successful commissioning moves the installation and order to `COMMISSIONED` and activates product warranty records.
11. Customer can view their own installation and active warranties.

## 7. Core state transitions

### Customer product order
`PENDING_VENDOR_ACCEPTANCE → CONFIRMED` (vendor accepts)

`PENDING_VENDOR_ACCEPTANCE → REJECTED` (vendor rejects)

### Confirmed order and operations
`CONFIRMED → PAYMENT_PENDING / PAID / ON_HOLD / CANCELLED`

`PAID → MATERIAL_RESERVED`

`MATERIAL_RESERVED → INSTALLATION_SCHEDULED`

`INSTALLATION_SCHEDULED → INSTALLATION_IN_PROGRESS`

`INSTALLATION_IN_PROGRESS → INSTALLED`

`INSTALLED → COMMISSIONED → CLOSED`

Every state change must be validated server-side and written to order history. A rejected order cannot be reserved or scheduled. Cancellation with active reservations requires a controlled release first.

## 8. Tenant and authorization invariants

- Every vendor-owned record is scoped by the authenticated tenant.
- Every cross-reference (product, customer, employee, order, warehouse, installation, team, checklist and warranty) must belong to that same tenant.
- Customer actions resolve the customer profile from authenticated user ID and tenant; the caller cannot select another customer profile.
- Vendor decisions resolve the order from authenticated vendor tenant.
- Only enabled and eligible same-tenant engineers may be assigned.
- Inventory mutations require inventory permission; team/template administration requires operations-manager authority.
- Customer views return only the authenticated customer's orders, enquiries, installations and warranties.
- Database uniqueness, foreign keys, checks, row locks and immutable-ledger triggers complement application authorization; they do not replace it.

## 9. Operational controls and limitations

- Demo product attributes and photographs are illustrative. Verify manufacturer specifications, HSN/GST, certification and warranty documents before selling.
- Payment collection and payment-gateway callbacks are outside this Release 3 direct-order flow.
- Product order UI currently submits one catalogue product per order; a multi-product cart is a later enhancement.
- Abandoned reservation expiry/reconciliation requires a future scheduled job or controlled operations runbook.
- Production readiness requires green Maven, frontend, MySQL migration, Testcontainers concurrency, cross-tenant authorization and browser end-to-end tests.
