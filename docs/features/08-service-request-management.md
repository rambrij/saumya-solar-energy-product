# Service request management

## 1. Feature purpose

Track post-installation support, service visits, evidence and warranty-related work for solar systems.

## 2. Supported roles and permissions

Vendor Service Manager/Technician and authorized service roles; Customer can create/view own service requests where
enabled.

## 3. Preconditions

Customer/system must be in tenant scope; installation/warranty context should be supplied when relevant; private
attachments require authorized access.

## 4. Business rules

Service request ownership is tenant-scoped; comments/events/attachments have their own authorization; warranty claims
use explicit approve/reject/resolve lifecycle.

## 5. Detailed user flow

Customer raises a service issue; service manager triages and assigns; technician visits and records resolution; customer
follows status; warranty claim is handled separately if applicable.

## 6. Frontend routes and screens

SPA `/` → Vendor Service/Operations tab and Customer service area when enabled. Confirm actual visible tab for role in
current frontend.

## 7. Backend APIs

Service and service-request controller routes are exposed through the operations API; warranty claim APIs verified:
`GET /api/v1/warranty-claims`, `GET/POST /api/v1/customer/warranty-claims`,
`POST /api/v1/warranty-claims/{id}/approve|reject|resolve`; private service attachments are served by
`ServiceAttachmentController`.

## 8. Request and response examples

Illustrative customer issue:
`{"subject":"Inverter showing fault code","description":"System stopped generating today","installationId":"<own-installation-uuid>"}`.
Check active request DTO for exact field names.

Response shape illustration:
`{"id":"<service-request-uuid>","ticketNumber":"SR-...","status":"OPEN","priority":"MEDIUM"}`.

## 9. Database entities and relationships

`ServiceRequest` references tenant/customer and optional installation/warranty; `ServiceEvent`, `ServiceComment`,
`ServiceVisit`, `ServiceAttachment` retain activity and evidence; `WarrantyClaim` is a distinct workflow.

## 10. Validation rules

Customer must own referenced installation; attachments are private; resolve action requires allowed status and
permissions; claim resolution requires APPROVED status.

## 11. Status transitions

Service request status is governed by service workflow; warranty claim: submitted → approved/rejected → resolved (only
approved claims resolve).

## 12. Error and edge cases

Create a service request as customer; verify vendor sees it; assign technician; add comment/visit; attach evidence;
attempt foreign tenant attachment download; create warranty claim and test approve/reject/resolve preconditions.

## 13. Manual testing instructions

Service activity and private evidence remain scoped to the tenant and customer; warranty claim state transitions are
auditable.

## 14. Expected results

```mermaid\nflowchart TD\n A[Customer reports issue] --> B[Service triage]\n B --> C[Assign technician]\n C --> D[Visit and diagnosis]\n D --> E{Warranty claim?}\n E -- Yes --> F[Review claim]\n E -- No --> G[Resolve service request]\n F --> H[Approve or reject]\n H --> G\n```

## 15. Business flow diagram

Exact service-request endpoint inventory should be cross-checked against the deployed build; the verified Phase 2
controller currently exposes warranty-claim routes and private attachment controllers.

## 16. Known limitations

undefined
