# Customer enquiry

## 1. Feature purpose

Capture customer interest in solar products and preserve a traceable entry point into lead and quotation workflows.

## 2. Supported roles and permissions

Customer can submit own enquiry; Vendor Admin/Sales roles can capture and manage enquiries; Platform roles do not
operate tenant enquiries.

## 3. Preconditions

Customer must be authenticated or submit through an active public store slug. Vendor access requires same-tenant
permissions.

## 4. Business rules

Enquiries are tenant-scoped; customer identity is resolved from authenticated profile where applicable; conversion is
one-time; public store slug must resolve to an active tenant.

## 5. Detailed user flow

Customer selects product/package and submits contact/site/capacity details. Vendor reviews the enquiry and converts it
to a lead when qualified.

## 6. Frontend routes and screens

SPA `/` → Customer Request/Enquiry area or Vendor Enquiries tab; public store enquiry form.

## 7. Backend APIs

`POST /api/v1/customer/enquiries`; `POST /api/v1/public/{storeSlug}/enquiries`; vendor generic create
`POST /api/v1/{type}` with `type=enquiries`; conversion `POST /api/v1/enquiries/{id}/convert`.

## 8. Request and response examples

Illustrative customer request:
`{"subject":"5 kW rooftop solar","message":"Please share a quotation","monthlyBill":4500}`. Use the request DTO in the
API source as the authoritative schema.

Response shape illustration:
`{"id":"<enquiry-uuid>","tenantId":"<tenant-uuid>","customerId":"<customer-uuid>","status":"NEW"}`.

## 9. Database entities and relationships

`Enquiry` references tenant and customer; product/package references are validated against the same tenant; conversion
creates/links `SalesLead`.

## 10. Validation rules

Required contact/message fields are validated; referenced products/packages must be active and tenant-owned; duplicate
conversion is rejected.

## 11. Status transitions

New → reviewed/qualified → converted to lead; unsupported transitions are rejected by backend service rules.

## 12. Error and edge cases

Submit enquiry as customer; verify it appears in vendor tenant inbox; attempt foreign tenant access; convert once;
repeat conversion and expect conflict; verify customer enquiry remains linked to lead.

## 13. Manual testing instructions

Vendor sees the enquiry under its tenant and a single lead is created/linked on conversion.

## 14. Expected results

```mermaid\nflowchart TD\n A[Customer selects product] --> B[Submit enquiry]\n B --> C[Vendor reviews enquiry]\n C --> D{Qualified?}\n D -- No --> E[Follow up / close]\n D -- Yes --> F[Convert to lead]\n```

## 15. Business flow diagram

Email notification delivery depends on configured mail transport and notification settings; do not assume a message was
delivered solely because the enquiry API succeeded.

## 16. Known limitations

undefined
