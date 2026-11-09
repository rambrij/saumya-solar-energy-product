# Site survey

## 1. Feature purpose
Schedule, assess and approve the customer's installation site before committing the technical design and order.

## 2. Supported roles and permissions
Vendor Admin, Sales/Survey roles, assigned surveyors/engineers; customer sees only authorized customer-facing survey data where exposed.

## 3. Preconditions
A same-tenant lead/customer and assigned enabled employee must exist; survey schedule must be valid.

## 4. Business rules
Survey lifecycle actions are permission-controlled; history is retained; survey cannot be approved/rejected from invalid states; attachments use private storage authorization.

## 5. Detailed user flow
Vendor schedules survey from lead; surveyor starts assessment, records findings and submits; reviewer approves/rejects; quotation/order can reference approved survey.

## 6. Frontend routes and screens
SPA `/` → Vendor Site Surveys tab and survey detail; lifecycle actions are under `/api/v1/surveys`.

## 7. Backend APIs
`POST /api/v1/surveys`; `GET /api/v1/surveys/{id}`; `PATCH /api/v1/surveys/{id}`; `GET /api/v1/surveys/{id}/history`; `POST /api/v1/surveys/{id}/start|submit|approve|reject|cancel`.

## 8. Request and response examples
Illustrative create: `{"leadId":"<same-tenant-lead-uuid>","scheduledAt":"2026-12-02T09:30:00Z","assignedTo":"<same-tenant-user-uuid>"}`. Use DTO fields as authoritative.

Response shape illustration: `{"id":"<survey-uuid>","leadId":"<lead-uuid>","status":"SCHEDULED","scheduledAt":"2026-12-02T09:30:00Z"}`.

## 9. Database entities and relationships
`SiteSurvey` references tenant, lead/customer and assigned employee; `SiteSurveyHistory` records transitions; `SiteSurveyAttachment` stores private evidence metadata.

## 10. Validation rules
Lead and employee must be same-tenant; dates and status transitions are validated; attachment downloads re-check survey scope.

## 11. Status transitions
Scheduled → in progress → submitted → approved/rejected; cancellation is allowed only from permitted states.

## 12. Error and edge cases
Create survey from lead; start; update technical assessment; submit; approve; inspect history; attempt foreign tenant survey ID and expect not found/denied.

## 13. Manual testing instructions
Approved survey is available for downstream quotation/order planning and its transition history remains queryable.

## 14. Expected results
```mermaid\nflowchart TD\n A[Lead qualified] --> B[Schedule survey]\n B --> C[Surveyor starts visit]\n C --> D[Record technical findings]\n D --> E[Submit for review]\n E --> F{Approved?}\n F -- Yes --> G[Quotation / order planning]\n F -- No --> H[Rework or cancel]\n```

## 15. Business flow diagram
Full calendar scheduling and offline evidence synchronization depend on the enabled operations modules.

## 16. Known limitations
undefined
