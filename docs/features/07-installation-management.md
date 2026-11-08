# Installation management

## 1. Feature purpose
Plan, assign, execute and commission rooftop solar installations with checklists and schedule history.

## 2. Supported roles and permissions
Vendor Admin, authorized Operations/Installation Manager, Installer/Technician; customer can view own installation where enabled.

## 3. Preconditions
An eligible order/customer and same-tenant installation team/technician must exist; active checklist templates should be configured.

## 4. Business rules
Tenant scope is mandatory; checklist items and commissioning requirements are validated; schedule changes and completion are auditable.

## 5. Detailed user flow
Operations creates installation job, assigns team/date, technician completes mandatory checklist, manager completes and commissions installation.

## 6. Frontend routes and screens
SPA `/` → Vendor Installations/Operations tab; Customer installation view; checklist detail.

## 7. Backend APIs
`GET /api/v1/installations`; `POST /api/v1/installations`; `GET /api/v1/installations/{id}/checklist`; `PATCH /api/v1/installations/{id}/checklist/{itemId}`; `/reschedule`, `/cancel`, `/complete`, `/commission`; `GET /api/v1/installations/{id}/schedule-history`.

## 8. Request and response examples
Illustrative create body: `{"orderId":"<same-tenant-order-uuid>","scheduledAt":"2026-12-05T08:00:00Z","teamId":"<team-uuid>"}`; use controller `InstallationInput` for exact field contract.

## 9. Database entities and relationships
`InstallationJob` references tenant/order/team; `InstallationChecklistItem` references job/template; `InstallationScheduleHistory` records schedule changes; warranty is created/linked according to commissioning flow.

## 10. Validation rules
Order and team must be tenant-owned; mandatory checklist items must be complete before completion; schedule timestamps and status transitions validated.

## 11. Status transitions
Scheduled → in progress → completed → commissioned; reschedule/cancel operations record history and require valid states.

## 12. Error and edge cases
Create installation for order; inspect checklist; complete mandatory items; attempt completion with unchecked mandatory item; reschedule and inspect history; commission and verify warranty/customer visibility.

## 13. Manual testing instructions
Completed and commissioned installation is linked to its order and checklist/schedule history is retained.

## 14. Expected results
```mermaid\nflowchart TD\n A[Order ready] --> B[Create installation job]\n B --> C[Assign team and schedule]\n C --> D[Complete mandatory checklist]\n D --> E[Manager completes job]\n E --> F[Commission system]\n F --> G[Warranty / service lifecycle]\n```

## 15. Business flow diagram
Resource capacity planning and automated route optimization are not implied by the current schedule APIs.

## 16. Known limitations
undefined
