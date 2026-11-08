# Order management

## 1. Feature purpose
Convert an accepted quotation into a controlled order and manage order status, reservations and financial traceability.

## 2. Supported roles and permissions
Vendor Admin and authorized order/operations roles; Customer sees own orders and may submit supported product orders.

## 3. Preconditions
Quotation must be accepted and valid; order conversion uses an idempotency key; products and customer must belong to tenant.

## 4. Business rules
Conversion is transactional and idempotent; financial snapshots are immutable; inventory reservation and order transitions are permission/state validated.

## 5. Detailed user flow
Vendor converts accepted quotation, reviews order, reserves stock if applicable and advances through fulfillment states; customer views own order.

## 6. Frontend routes and screens
SPA `/` → Vendor Orders tab; Customer My Orders; order history detail.

## 7. Backend APIs
`POST /api/v1/quotations/{id}/convert-to-order`; `GET /api/v1/customer/product-orders`; `POST /api/v1/customer/product-orders`; `POST /api/v1/orders/{id}/vendor-decision`; `POST /api/v1/orders/{id}/transition`; `GET /api/v1/orders/{id}/history`; inventory reservation endpoints under `/api/v1/orders/{id}/reserve`.

## 8. Request and response examples
Conversion request requires `Idempotency-Key` header; body is the quotation conversion DTO (often empty). Response returns the created order. Never retry with a new key after a timeout until checking the original order.

Response shape illustration: `{"id":"<order-uuid>","quotationId":"<quotation-uuid>","orderNumber":"ORD-...","status":"CONFIRMED","total":218300.00}`.

## 9. Database entities and relationships
`CustomerOrder` references tenant/customer/quotation; `OrderItem` stores product and financial snapshots; `OrderStatusHistory`, inventory reservation and payment records link to order.

## 10. Validation rules
Only accepted quotations can convert; duplicate key is bound to original quotation; quantities and transitions validated; customer can only access own order.

## 11. Status transitions
Quotation accepted → order created → vendor decision/fulfillment transitions → installation scheduling; exact order statuses are defined in OperationsService.

## 12. Error and edge cases
Accept a quotation; convert with a stable idempotency key; retry same key; verify one order; inspect order items/history; attempt cross-tenant order read; test invalid transition.

## 13. Manual testing instructions
A single order is linked to the accepted quotation and order history captures status changes.

## 14. Expected results
```mermaid\nflowchart TD\n A[Accepted quotation] --> B[Idempotent order conversion]\n B --> C[Order created with price snapshots]\n C --> D[Reserve inventory if required]\n D --> E[Vendor fulfillment decision]\n E --> F[Schedule installation]\n```

## 15. Business flow diagram
Payment gateway state is provider-neutral; successful order creation does not mean payment has been captured.

## 16. Known limitations
undefined
