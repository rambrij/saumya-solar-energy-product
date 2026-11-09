# Lead management

## 1. Feature purpose
Track qualified prospects, ownership, sales stage, follow-up dates and assignment history.

## 2. Supported roles and permissions
Vendor Admin, Sales Manager and authorized Sales Executive; customer has no lead-management access.

## 3. Preconditions
A same-tenant enquiry/customer must exist for conversion or lead association; user assignment must target an enabled same-tenant vendor user.

## 4. Business rules
Lead conversion is idempotent; assignments cannot cross tenant boundaries; stage and follow-up changes are recorded; role permissions govern reads and writes.

## 5. Detailed user flow
Vendor reviews enquiries, converts qualified records, assigns an owner, updates stage/follow-up and reviews assignment history before quotation.

## 6. Frontend routes and screens
SPA `/` → Vendor Leads tab and lead detail/history panel.

## 7. Backend APIs
`POST /api/v1/enquiries/{id}/convert`; `PATCH /api/v1/leads/{id}`; `GET /api/v1/leads/{id}/history`.

## 8. Request and response examples
Illustrative update: `{"stage":"QUALIFIED","followUpAt":"2026-12-01T10:00:00Z","assignedTo":"<same-tenant-user-uuid>"}`. Confirm exact DTO fields in `Controller.java` before integration.

Response shape illustration: `{"id":"<lead-uuid>","enquiryId":"<enquiry-uuid>","stage":"NEW","assignedUserId":"<same-tenant-user-uuid>"}`.

## 9. Database entities and relationships
`SalesLead` references tenant, customer and source enquiry; `LeadHistory` stores assignment/change history; user references are tenant-validated.

## 10. Validation rules
Lead stage must be supported; follow-up timestamp must be valid; assigned employee must be enabled and belong to the same tenant.

## 11. Status transitions
Lead stages are controlled by service validation; enquiry conversion is one-time; assignment/reassignment appends history.

## 12. Error and edge cases
Convert an enquiry; assign a Sales Executive; inspect history; reassign to another same-tenant employee; try assigning a user from another tenant; verify denial.

## 13. Manual testing instructions
Lead appears in the vendor pipeline, current owner is correct and every assignment is visible in history.

## 14. Expected results
```mermaid\nflowchart TD\n A[Enquiry converted] --> B[Lead created]\n B --> C[Assign sales owner]\n C --> D[Update stage and follow-up]\n D --> E[Review assignment history]\n E --> F[Create site survey or quotation]\n```

## 15. Business flow diagram
The current UI uses compact list/editor controls rather than a full kanban pipeline board.

## 16. Known limitations
undefined
