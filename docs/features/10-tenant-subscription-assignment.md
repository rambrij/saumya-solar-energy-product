# Tenant subscription assignment

## 1. Feature purpose
Assign and replace SaaS plans for tenants while retaining effective dates, trial settings, renewal configuration and immutable assignment history.

## 2. Supported roles and permissions
Platform Super Admin with `SUBSCRIPTION_MANAGE`; Vendor Admin can read current subscription and usage but cannot assign plans.

## 3. Preconditions
Tenant must exist; selected plan must be active; V46, V47 and V48 migrations must be applied.

## 4. Business rules
Tenant row is locked during assignment; start/end/trial dates validated; replacement closes/cancels previous active assignments and records replacement links/history; plan/tenant IDs are validated server-side.

## 5. Detailed user flow
Platform searches tenant by name/slug, selects active plan, reviews existing subscription, confirms replacement if applicable, configures dates/billing/auto-renew/notes and submits.

## 6. Frontend routes and screens
SPA `/` → Platform Subscription Administration → tenant search/assignment/history.

## 7. Backend APIs
`GET /api/v1/platform/tenants/search?q=...`; `GET /api/v1/platform/tenants/{tenantId}/subscriptions`; `GET /api/v1/platform/tenants/{tenantId}/subscription-history`; `POST /api/v1/platform/tenants/{tenantId}/subscriptions`; `GET/PUT /api/v1/platform/tenants/{tenantId}/subscriptions/{subscriptionId}`; cancellation `POST /api/v1/platform/tenants/{tenantId}/subscriptions/{subscriptionId}/cancel`.

## 8. Request and response examples
Example: `{"planId":"<active-plan-uuid>","status":"TRIAL","startsAt":"2026-12-01T00:00:00Z","trialEndsAt":"2026-12-15T00:00:00Z","billingCycle":"MONTHLY","autoRenew":true,"notes":"Pilot onboarding"}`.

Response shape illustration: `{"id":"<tenant-subscription-uuid>","tenantId":"<tenant-uuid>","planId":"<plan-uuid>","status":"TRIAL","billingCycle":"MONTHLY","autoRenew":true}`.

## 9. Database entities and relationships
`TenantSubscription` references tenant, plan and actor; `SubscriptionAssignmentHistory` stores action, previous/new status and plan, actor, JSON details and timestamp; replaced assignment links to successor UUID.

## 10. Validation rules
Tenant search requires 2+ characters; assignment requires active plan; end/trial end cannot precede start; billing cycle validated; tenant and plan access is Platform-only.

## 11. Status transitions
TRIAL/ACTIVE/Past-due assignments may be replaced/cancelled; each assignment/update/cancellation appends history. Effective subscription is derived from status and date window.

## 12. Error and edge cases
Search tenant; select; inspect current assignment; assign trial; verify vendor current subscription reflects it; replace with Professional and confirm dialog; inspect history; try duplicate active assignment through update API and expect conflict; attempt vendor access to platform routes and expect 403.

## 13. Manual testing instructions
Tenant's effective plan changes while all prior subscription rows and history remain available for audit.

## 14. Expected results
```mermaid\nflowchart TD\n A[Search tenant] --> B[Load current assignment and history]\n B --> C[Select active plan and dates]\n C --> D{Existing active assignment?}\n D -- Yes --> E[Confirm replacement]\n D -- No --> F[Assign plan]\n E --> F\n F --> G[Write assignment history]\n G --> H[Vendor dashboard reflects effective plan]\n```

## 15. Business flow diagram
The confirmation is a frontend safeguard; the backend also locks the tenant and applies replacement/history rules. Auto-renew is a stored setting and does not itself charge a payment method.

## 16. Known limitations
undefined
