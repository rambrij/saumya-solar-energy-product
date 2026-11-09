# Quotation management

## 1. Feature purpose

Prepare controlled solar quotations with catalogue-derived pricing, tax snapshots, approvals, revisions and customer
decisions.

## 2. Supported roles and permissions

Vendor Admin and authorized Sales/Quotation roles create and manage quotations; approvers need `DISCOUNT_APPROVE`;
Customer can view and accept/reject own quotations.

## 3. Preconditions

Customer and product references must belong to tenant; an optional lead/survey must be same-tenant; catalogue price/tax
is server-derived.

## 4. Business rules

Server computes line pricing/tax; discount threshold is tenant-configurable; approval is required above threshold;
revision snapshots are immutable; customer decisions apply to sent valid quotation revision.

## 5. Detailed user flow

Vendor selects customer, products, quantities and optional lead/survey; system calculates totals; authorized approver
approves/rejects if needed; vendor sends; customer accepts/rejects; accepted quote can convert to order.

## 6. Frontend routes and screens

SPA `/` → Vendor Quotations tab; Customer Quotations area; PDF action from quotation detail.

## 7. Backend APIs

`POST /api/v1/quotations`; `POST /api/v1/quotations/{id}/{approve|reject-approval|send|accept|reject}`;
`GET /api/v1/quotations/{id}/revisions`; `POST /api/v1/quotations/{id}/revisions`; `GET /api/v1/quotations/{id}/pdf`;
`POST /api/v1/quotations/{id}/convert-to-order`.

## 8. Request and response examples

Illustrative create payload:
`{"customerId":"<uuid>","leadId":"<optional-uuid>","surveyId":"<optional-uuid>","items":[{"productId":"<uuid>","quantity":10}]}`.
Backend calculates price and tax; do not send trusted totals from the browser.

Response shape illustration:
`{"id":"<quotation-uuid>","quotationNumber":"QUO-...","status":"DRAFT","grandTotal":218300.00,"revision":1}`.

## 9. Database entities and relationships

`Quotation` 1—N `QuotationItem`; revisions store immutable item/financial snapshots; quotation references
tenant/customer and optionally lead/survey; accepted quotation links to `CustomerOrder`.

## 10. Validation rules

Quantity must be positive; referenced products active and same-tenant; customer ownership verified; action must be valid
for current status and validity period; approval permission required.

## 11. Status transitions

Draft → pending approval (when threshold exceeded) → approved → sent → accepted/rejected; accepted → order conversion.
Exact state checks are enforced server-side.

## 12. Error and edge cases

Create quotation; confirm calculated GST; test threshold requiring approval; reject without permission; approve; send;
login as customer and accept; inspect revisions/PDF; convert to order twice using idempotency key and verify single
order.

## 13. Manual testing instructions

Customer decision and financial snapshot remain attached to the exact quotation revision; repeated order conversion does
not create a second order.

## 14. Expected results

```mermaid\nflowchart TD\n A[Vendor drafts quotation] --> B[Server prices from catalogue]\n B --> C{Discount exceeds threshold?}\n C -- Yes --> D[Approval decision]\n C -- No --> E[Send quotation]\n D --> E\n E --> F[Customer accepts or rejects]\n F -- Accepted --> G[Convert to order]\n```

## 15. Business flow diagram

PDF branding/legal terms are limited to the fields currently implemented. Payment schedule and configurable legal
clauses may require later enhancement.

## 16. Known limitations

undefined
