# Subscription plan management

## 1. Feature purpose
Allow Platform Super Admins to configure SaaS plans and feature/usage limits used by tenant entitlement enforcement.

## 2. Supported roles and permissions
Platform Super Admin with `SUBSCRIPTION_MANAGE`; Vendor users can view only their current subscription/usage.

## 3. Preconditions
Flyway migrations V37, V45 and V46 must be applied; platform principal must carry `PLATFORM_SUPER_ADMIN` and permission.

## 4. Business rules
Plan code is unique; billing cycle is MONTHLY/YEARLY; price and limits validated; entitlements must be valid JSON; plans with subscription history cannot be physically deleted and must be deactivated.

## 5. Detailed user flow
Platform lists plans, creates or edits configuration, activates/deactivates plans and deletes only unassigned plans; assigned plan history remains intact.

## 6. Frontend routes and screens
SPA `/` → Platform Subscription Administration panel.

## 7. Backend APIs
`GET /api/v1/platform/subscription-plans`; `GET /api/v1/platform/subscription-plans/{planId}`; `POST /api/v1/platform/subscription-plans`; `PUT /api/v1/platform/subscription-plans/{planId}`; `POST .../{planId}/activate|deactivate`; `DELETE /api/v1/platform/subscription-plans/{planId}`. Public/vendor plan list remains `GET /api/v1/subscription-plans`.

## 8. Request and response examples
Example create body: `{"planCode":"GROWTH","name":"Growth","billingCycle":"MONTHLY","price":4999,"currency":"INR","maxUsers":30,"maxBranches":5,"maxWarehouses":5,"maxStorageMb":10240,"maxProducts":2000,"maxCustomers":2000,"trialPeriodDays":14,"displayOrder":4,"entitlementsJson":"{\\"branches\\":true}"}`.

## 9. Database entities and relationships
`SubscriptionPlan` is referenced by `TenantSubscription`; `subscription_plan.plan_code` has a unique constraint; V46 adds product/customer limits, trial days, display order and assignment-history table.

## 10. Validation rules
Plan code/name required; cycle enum; non-negative price/storage/trial/display order; positive user/branch/warehouse/product/customer limits; valid JSON entitlements.

## 11. Status transitions
Plan status Active ↔ Inactive. Physical deletion is allowed only if no `tenant_subscription` references the plan.

## 12. Error and edge cases
As Platform Super Admin create a plan; verify it appears; edit price/limits; deactivate and confirm it disappears from assignment choices; reactivate; attempt deletion while assigned and expect conflict; delete an unused plan.

## 13. Manual testing instructions
Plan limits and entitlements are persisted and consumed by existing subscription enforcement; assignment history is not deleted when a plan is deactivated.

## 14. Expected results
```mermaid\nflowchart TD\n A[Platform opens plans] --> B[Create or edit plan]\n B --> C[Validate unique code and limits]\n C --> D[Persist plan] \n D --> E[Activate or deactivate]\n E --> F[Assign active plan to tenant]\n```

## 15. Business flow diagram
Plan billing is configuration only; no claim is made that recurring payment collection or invoice generation is automatically performed.

## 16. Known limitations
undefined
