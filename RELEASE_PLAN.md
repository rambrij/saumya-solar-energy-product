# Solar Energy Platform --- Release Plan

**Document type:** Product and engineering release plan\
**Version:** 1.0\
**Status:** Proposed\
**Planning approach:** Incremental, dependency-driven releases

## 1. Purpose

This document defines a five-release delivery plan for the solar energy
platform. The releases are sequenced to establish secure tenant identity
first, deliver the end-to-end sales lifecycle next, then add
installation and inventory operations, finance and service workflows,
and finally platform-wide capabilities and production hardening.

The plan is intended to be used for engineering estimation, sprint
planning, release readiness reviews, QA planning, and deployment
coordination.

## 2. Release strategy

  -----------------------------------------------------------------------
  Release                 Theme                   Primary outcome
  ----------------------- ----------------------- -----------------------
  Release 1               Identity and tenant     Secure multi-tenant
                          foundation              access and user
                                                  lifecycle

  Release 2               Sales lifecycle         Convert customer
                                                  interest into approved
                                                  orders

  Release 3               Operations              Fulfil orders through
                                                  installation and
                                                  commissioning

  Release 4               Finance and service     Manage billing,
                                                  payments, after-sales
                                                  service, and controls

  Release 5               Platform capabilities   Complete platform-wide
                                                  communication,
                                                  reporting, audit, and
                                                  production readiness
  -----------------------------------------------------------------------

### Delivery principles

1.  **Dependency first:** Build shared foundations before features that
    depend on them.
2.  **Tenant isolation by default:** Every tenant-owned read and write
    must be scoped to the authenticated tenant context.
3.  **API-first contracts:** Define and version API contracts before
    parallel frontend/backend implementation.
4.  **Traceable business changes:** Important state changes, approvals,
    financial actions, and inventory movements must be auditable.
5.  **Idempotent integrations:** Retried requests and duplicate provider
    callbacks must not create duplicate business transactions.
6.  **Incremental deployment:** Each release must be independently
    testable and deployable without breaking previously released
    functionality.
7.  **Production-safe configuration:** Secrets, environment-specific
    configuration, migrations, and operational procedures must be
    externalized and documented.

## 3. Cross-release engineering requirements

These requirements apply to every release, even where they are not
repeated in the feature list.

-   Authentication and authorization checks on every protected API.
-   Tenant scoping on all tenant-owned entities and database queries.
-   Input validation, consistent error responses, and API documentation.
-   Database migrations with forward deployment and a documented
    recovery strategy.
-   Unit tests for business rules and integration tests for persistence
    and external boundaries.
-   Structured logs with correlation/request IDs; never log passwords,
    tokens, payment secrets, or sensitive personal data.
-   Health/readiness checks and basic service metrics.
-   Secure handling of secrets and environment-specific configuration.
-   Responsive UI, loading/empty/error states, and accessibility basics.
-   Automated build and test pipeline for every pull request.
-   Backward-compatible API and database changes wherever practical.

## 4. Release 1 --- Identity and tenant foundation

**Objective:** Establish secure identity, tenant boundaries, and
role-based access for platform, vendor, and customer users.

### Scope

-   Tenant and vendor management
-   Customer registration
-   Access and refresh tokens
-   Logout and token revocation
-   Password reset
-   Roles and permissions
-   Vendor employee management
-   Platform, vendor, and customer layouts
-   Tenant-context enforcement
-   Cross-tenant integration tests

### Suggested implementation sequence

1.  Define tenant, vendor, user, customer, role, permission, and
    membership data models.
2.  Implement tenant/vendor provisioning and lifecycle rules.
3.  Implement customer registration and vendor employee onboarding.
4.  Implement login, access-token issuance, refresh-token rotation,
    logout, and revocation.
5.  Implement password reset using expiring, single-use reset tokens.
6.  Implement role/permission evaluation and server-side authorization.
7.  Add platform, vendor, and customer application layouts and
    navigation.
8.  Enforce tenant context in service and repository/data-access layers.
9.  Add negative tests proving that one tenant cannot access another
    tenant's data.

### Key design and security decisions

-   Derive tenant context from the authenticated identity and validated
    membership; do not trust a tenant ID supplied by the browser as
    authorization.
-   Use short-lived access tokens and securely stored refresh-token
    records. Rotate refresh tokens and detect reuse.
-   Revoke refresh-token sessions on logout, password reset, and other
    defined security events.
-   Store password hashes using a modern adaptive password-hashing
    algorithm; never store plaintext passwords.
-   Enforce permissions on the backend. Hiding a UI menu is not
    authorization.
-   Define which roles are platform-wide and which are tenant-scoped.
-   Keep privileged platform/vendor employee provisioning restricted to
    authorized administrative workflows.

### Acceptance criteria

-   A customer can register and access only the customer experience.
-   Authorized platform users can manage tenants and vendors.
-   Vendor administrators can manage employees within their own vendor
    tenant and assign only permitted roles.
-   Access tokens expire as configured; refresh-token rotation and
    revocation work.
-   Logout invalidates the relevant refresh-token session.
-   Password reset tokens expire, are single-use, and cannot be used to
    access another account.
-   Every protected endpoint enforces authentication, permission, and
    tenant scope.
-   Automated tests cover cross-tenant read, update, delete, and
    identifier-guessing attempts.
-   Platform, vendor, and customer layouts display only
    navigation/actions appropriate to the user's permissions.

### Release gate

**Do not proceed to Release 2 production rollout until tenant isolation,
token lifecycle, privileged-role controls, and cross-tenant negative
tests pass.**

## 5. Release 2 --- Sales lifecycle

**Objective:** Support the complete sales journey from product discovery
and lead capture through site survey, quotation, customer decision, and
order creation.

### Scope

-   Product categories
-   Products
-   Configurable solar packages
-   Customers
-   Enquiries and leads
-   Lead assignment
-   Site surveys
-   Versioned quotations
-   Discount approval
-   Quotation PDF
-   Customer acceptance/rejection
-   Idempotent quotation-to-order conversion

### Suggested implementation sequence

1.  Implement product categories, products, pricing, and active/inactive
    lifecycle.
2.  Define configurable package composition, optional components, and
    validation rules.
3.  Implement customer, enquiry, and lead records with source and
    ownership.
4.  Implement lead assignment and reassignment with history.
5.  Implement site-survey scheduling, findings, attachments, and
    completion status.
6.  Implement quotation creation and immutable/versioned revisions.
7.  Implement discount thresholds and approval workflow.
8.  Generate a quotation PDF from a specific quotation version.
9.  Provide customer acceptance/rejection with actor, timestamp, and
    recorded version.
10. Convert an accepted quotation to an order using an idempotency key
    and transactional safeguards.

### Key design decisions

-   A quotation revision must preserve its products, quantities, prices,
    taxes, discounts, and terms as they were when issued.
-   Editing an issued quotation creates a new version; it must not
    silently alter an earlier customer-facing document.
-   Discount approval must be based on configurable thresholds and the
    user's permission.
-   Customer acceptance must reference the exact quotation version
    accepted.
-   Order conversion must be atomic and safe under retries or concurrent
    requests.
-   Product/package pricing and tax calculations must be server-side and
    reproducible.

### Acceptance criteria

-   Authorized users can manage categories, products, and configurable
    solar packages.
-   Enquiries can be captured, assigned, reassigned, searched, and
    tracked.
-   Site-survey records can be completed and linked to the relevant
    customer/lead.
-   Quotation revisions are retained and individually retrievable.
-   Discounts above configured thresholds cannot be issued without
    approval.
-   Generated PDFs match the selected quotation version.
-   Acceptance/rejection records the customer decision against the
    correct version.
-   Repeated conversion requests create exactly one order.
-   Unauthorized users cannot view or modify another tenant's sales
    records.

### Release gate

**Release 2 is ready when a lead can be traced from enquiry through
survey and quotation to one correctly created order, with approval and
document history preserved.**

## 6. Release 3 --- Operations

**Objective:** Fulfil confirmed orders through inventory allocation,
installation planning, field execution, commissioning, and warranty
activation.

### Scope

-   Order state machine
-   Order history
-   Warehouses
-   Inventory balances
-   Immutable inventory transactions
-   Concurrent stock reservation
-   Installation scheduling
-   Engineer assignment
-   Installation checklist
-   Commissioning
-   Warranty activation

### Suggested implementation sequence

1.  Define order states, allowed transitions, transition guards, and
    cancellation paths.
2.  Record order-state history with actor, timestamp, and reason.
3.  Implement warehouse and stock-item models.
4.  Maintain inventory balances from immutable stock transactions.
5.  Implement reservation/release/consumption workflows with concurrency
    protection.
6.  Link required stock to an order or installation.
7.  Implement installation scheduling and engineer assignment.
8.  Implement configurable installation checklists and evidence capture.
9.  Record commissioning results and customer/site sign-off.
10. Activate warranty only after the required commissioning conditions
    are met.

### Key design decisions

-   Treat the order state machine as the authoritative source of valid
    order transitions.
-   Do not directly edit inventory balances as a substitute for
    recording stock movements.
-   Record receipts, reservations, releases, transfers, consumption, and
    adjustments as immutable transactions.
-   Use database transactions and appropriate locking or atomic
    conditional updates to prevent overselling.
-   Define reservation expiry and recovery for abandoned or failed
    installations.
-   Keep installation completion distinct from commissioning approval.
-   Store warranty start/end dates, covered items, terms, and activation
    evidence.

### Acceptance criteria

-   Invalid order transitions are rejected with a clear reason.
-   Every order state change is historically traceable.
-   Inventory balances reconcile to the transaction ledger.
-   Concurrent reservation tests prove that available stock cannot be
    over-reserved.
-   Reservations can be released safely when orders are cancelled or
    rescheduled.
-   Installations can be scheduled and assigned only to eligible
    engineers.
-   Checklist items and required evidence are retained.
-   Commissioning cannot be completed while mandatory checks remain
    incomplete.
-   Warranty activation follows configured commissioning rules and is
    linked to the installed equipment/order.

### Release gate

**Release 3 is ready when an accepted order can be fulfilled with
correct stock reservation, traceable field execution, approved
commissioning, and warranty activation.**

## 7. Release 4 --- Finance and service

**Objective:** Support invoicing and payment recording, reliable
payment-provider integration, and post-installation customer service.

### Scope

-   GST-ready invoices
-   Manual payments
-   Payment adapter
-   Payment webhook idempotency
-   Service tickets
-   Service visits
-   SLA tracking
-   Parts consumed
-   Customer confirmation
-   Refund and cancellation controls

### Suggested implementation sequence

1.  Define invoice numbering, tax configuration, line-item snapshots,
    and credit-note requirements.
2.  Generate GST-ready invoices using validated customer and business
    tax details.
3.  Implement manual payment recording with reference, amount, date,
    mode, and recorder.
4.  Define a provider-neutral payment adapter contract.
5.  Integrate the selected payment provider behind the adapter.
6.  Verify webhook signatures and persist provider event IDs before
    processing.
7.  Make payment event processing idempotent and safe under
    retries/out-of-order delivery.
8.  Implement service-ticket creation, categorization, priority,
    assignment, and status lifecycle.
9.  Implement service-visit scheduling, engineer notes, and resolution
    evidence.
10. Track SLA clocks, pauses, breaches, and escalation events.
11. Record parts consumed through the inventory transaction workflow.
12. Capture customer confirmation and implement controlled
    refund/cancellation workflows.

### Key design decisions

-   Confirm applicable GST rules and invoice fields with the
    business/accounting owner before production use.
-   Invoice records must preserve the tax and customer details used at
    issue time.
-   Manual payments must be distinguishable from provider-confirmed
    payments.
-   Payment webhooks must be authenticated, deduplicated, and reconciled
    against provider transaction status.
-   Do not treat a browser redirect as proof of payment.
-   Refunds and cancellations require explicit authorization, reason
    codes, and traceable state transitions.
-   Service parts consumption must update inventory through the same
    immutable transaction model used by operations.
-   SLA calculations must define business hours, holidays, pause
    conditions, and severity targets.

### Acceptance criteria

-   Invoices contain the approved GST/tax fields and use controlled
    numbering.
-   Manual payments are recorded with an audit trail and cannot silently
    overpay an invoice.
-   Provider integration can be replaced without rewriting core
    order/invoice business logic.
-   Invalid webhook signatures are rejected.
-   Duplicate webhook delivery does not duplicate payment records or
    financial side effects.
-   Payment status can be reconciled with provider records.
-   Service tickets can be assigned, scheduled, worked, and resolved.
-   SLA timers and breach states follow documented business rules.
-   Parts consumed during service are reflected in inventory.
-   Customer confirmation is recorded against the service visit/ticket.
-   Refunds and cancellations follow permission, amount, reason, and
    state controls.

### Release gate

**Release 4 is ready when invoice/payment states reconcile correctly and
service workflows preserve SLA, inventory, customer confirmation, and
financial audit history.**

## 8. Release 5 --- Platform capabilities

**Objective:** Complete shared platform capabilities, operational
visibility, security hardening, and production deployment readiness.

### Scope

-   CMS and blogs
-   Email and in-app notifications
-   SMS/WhatsApp provider interfaces
-   Reports and exports
-   Audit log
-   Platform dashboards
-   Vendor dashboards
-   Security hardening
-   Test suite
-   Production deployment files

### Suggested implementation sequence

1.  Implement CMS pages, publishing workflow, and blog management.
2.  Define notification templates, preferences, delivery status, and
    retry behavior.
3.  Implement email and in-app notification channels.
4.  Define provider interfaces for SMS and WhatsApp; integrate providers
    selected by the business.
5.  Implement tenant-scoped reports and export authorization.
6.  Implement append-only audit events for security-sensitive and
    business-critical actions.
7.  Build platform dashboards for tenant, sales, operations, finance,
    and service metrics.
8.  Build vendor dashboards with strict tenant scoping.
9.  Complete security review, dependency scanning, configuration review,
    and penetration testing.
10. Complete automated test coverage for critical workflows and
    regression scenarios.
11. Finalize production deployment manifests/configuration, migrations,
    backup/restore, monitoring, and runbooks.
12. Conduct release rehearsal and production go-live review.

### Key design decisions

-   Notifications should be asynchronous where appropriate, with retry
    limits and dead-letter/error handling.
-   Provider-specific SMS/WhatsApp logic must remain behind interfaces.
-   Reports and exports must enforce the same authorization and tenant
    rules as operational APIs.
-   Audit events should capture actor, action, target, timestamp, tenant
    context, correlation ID, and relevant change metadata without
    storing secrets.
-   Dashboards should use clearly defined metric definitions and time
    windows.
-   Production configuration must not contain committed credentials or
    default passwords.
-   Backups must be restorable; a successful backup job alone is not
    sufficient evidence.

### Acceptance criteria

-   CMS content can be drafted, reviewed, published, and unpublished by
    authorized users.
-   Notification delivery is traceable and retry-safe.
-   SMS/WhatsApp channels can be connected through documented provider
    adapters.
-   Reports and exports enforce role and tenant restrictions.
-   Audit records exist for defined privileged, financial, identity, and
    operational actions.
-   Platform dashboards show platform-wide metrics only to authorized
    platform users.
-   Vendor dashboards show only the vendor's permitted data.
-   Critical security findings are resolved or formally accepted with an
    owner and expiry.
-   Automated unit, integration, API, authorization, tenant-isolation,
    and end-to-end tests pass.
-   Production deployment files and environment-variable documentation
    are complete.
-   Backup/restore, monitoring, alerting, rollback, and incident
    runbooks are reviewed.
-   A production release rehearsal completes successfully.

### Release gate

**Release 5 is ready when the complete critical business journey is
tested end to end, tenant isolation is verified, production operations
are observable, and deployment/recovery procedures are rehearsed.**

## 9. Recommended end-to-end business journey

Use this journey as the primary cross-release acceptance scenario:

1.  A customer registers and signs in.
2.  A vendor receives an enquiry and assigns it to a sales employee.
3.  A site survey is scheduled and completed.
4.  Sales creates a quotation and obtains any required discount
    approval.
5.  The customer receives the quotation PDF and accepts the exact
    version.
6.  The accepted quotation is converted into one order, even if the
    request is retried.
7.  Operations reserves stock and schedules an engineer.
8.  The engineer completes the installation checklist and commissioning.
9.  Warranty is activated for the commissioned equipment.
10. Finance issues an invoice and records/reconciles payment.
11. The customer raises a service ticket.
12. A service visit is completed, parts are recorded, SLA is evaluated,
    and customer confirmation is captured.
13. Authorized users can review the relevant audit trail and dashboards.
14. A user from another tenant cannot access any of the customer's,
    vendor's, order's, payment's, or service records.

## 10. Test strategy

  -----------------------------------------------------------------------
  Test level                          Minimum coverage
  ----------------------------------- -----------------------------------
  Unit                                Domain rules, validators, state
                                      transitions, pricing/tax
                                      calculations, permission decisions

  Integration                         Database persistence, migrations,
                                      token lifecycle, inventory
                                      transactions, payment events,
                                      notification adapters

  API                                 Request validation, response
                                      contracts, authentication,
                                      authorization, pagination, error
                                      handling

  Tenant isolation                    Cross-tenant read/write/delete,
                                      guessed IDs, exports, background
                                      jobs, reports, attachments

  Concurrency                         Stock reservation, quotation
                                      conversion, webhook processing,
                                      duplicate requests

  End-to-end                          Customer-to-order,
                                      order-to-installation,
                                      invoice/payment, service-ticket
                                      resolution

  Security                            Token misuse, privilege escalation,
                                      password reset abuse, injection,
                                      rate limits, secret exposure

  Regression                          Previously released journeys and
                                      API contracts
  -----------------------------------------------------------------------

### Critical automated scenarios

-   A vendor user cannot access another vendor's records by changing an
    ID.
-   A customer cannot access another customer's quotation, invoice, or
    service ticket.
-   A refresh token cannot be reused after rotation or revocation.
-   A privileged role cannot be self-assigned through a public
    registration or ordinary user-management API.
-   Repeated quotation conversion creates one order.
-   Concurrent stock reservations never exceed available stock.
-   Duplicate payment webhooks create one financial effect.
-   Unauthorized exports and dashboard queries are denied.
-   Cancelled/refunded transactions retain their original history.

## 11. Deployment and release checklist

### Before deployment

-   [ ] Release scope and acceptance criteria approved.
-   [ ] Database migrations reviewed and tested against a
    production-like database.
-   [ ] Environment variables documented for the target environment.
-   [ ] Secrets stored in the deployment platform's secret
    manager/environment configuration.
-   [ ] Build, unit, integration, and security checks pass.
-   [ ] API and frontend compatibility verified.
-   [ ] Tenant-isolation and role-permission tests pass.
-   [ ] Backup completed and restore procedure verified for the release
    window.
-   [ ] External provider credentials and webhook endpoints configured.
-   [ ] Monitoring, alerts, and dashboards reviewed.
-   [ ] Rollback or forward-fix plan approved.

### During deployment

-   [ ] Deploy backward-compatible database changes first where
    required.
-   [ ] Deploy backend services and verify health/readiness.
-   [ ] Deploy frontend and verify configured API base URL.
-   [ ] Run smoke tests for authentication and critical workflows.
-   [ ] Verify background workers, scheduled jobs, and provider
    callbacks.
-   [ ] Monitor application errors, latency, database health, and queue
    failures.

### After deployment

-   [ ] Verify login and role-specific navigation.
-   [ ] Verify tenant-scoped access using test accounts from separate
    tenants.
-   [ ] Verify critical business journey.
-   [ ] Verify notifications and external integrations.
-   [ ] Verify audit events and operational dashboards.
-   [ ] Confirm no critical alerts or unexplained error spikes.
-   [ ] Record release version, migration version, deployment time, and
    approver.
-   [ ] Communicate release completion and known limitations.

## 12. Release governance

For each release, maintain:

-   **Product owner:** approves business scope and acceptance criteria.
-   **Technical owner:** approves architecture, API/data contracts, and
    technical readiness.
-   **Engineering owner:** coordinates implementation, code review, and
    delivery.
-   **QA owner:** approves test coverage, results, and defect
    disposition.
-   **Operations owner:** approves deployment, monitoring, backup, and
    recovery readiness.
-   **Business approver:** signs off business workflows and operational
    controls.

### Definition of done

A feature is complete only when:

1.  Acceptance criteria are met.
2.  Backend authorization and tenant enforcement are implemented.
3.  UI behavior and error states are complete.
4.  Unit and relevant integration/API tests pass.
5.  API documentation and operational notes are updated.
6.  Logs and audit events are added where required.
7.  No unresolved critical/high-severity defect remains without
    documented approval.
8.  Deployment configuration and migration impact are reviewed.

A release is complete only when all included features meet the
definition of done, release gates pass, and the deployment checklist is
signed off.

## 13. Risks and mitigations

  -----------------------------------------------------------------------
  Risk                    Impact                  Mitigation
  ----------------------- ----------------------- -----------------------
  Tenant context is       Cross-tenant data       Centralize tenant
  inconsistently enforced exposure                resolution; enforce at
                                                  service/data layer;
                                                  automate negative tests

  Role model grows        Privilege escalation    Use explicit permission
  without governance      and difficult           catalog, role matrix,
                          maintenance             and privileged-role
                                                  provisioning controls

  Quotation versions are  Disputes and incorrect  Snapshot issued
  mutable                 orders                  versions; accept/reject
                                                  by version ID

  Duplicate order         Duplicate fulfilment    Idempotency key, unique
  conversion              and financial records   constraints, and
                                                  transactional
                                                  conversion

  Inventory race          Overselling and         Atomic reservation,
  conditions              installation delays     locking/conditional
                                                  updates, concurrency
                                                  tests

  Payment callbacks are   Incorrect financial     Signature validation,
  duplicated or forged    state                   event deduplication,
                                                  reconciliation

  Tax assumptions are     Compliance and invoice  Validate tax rules and
  incorrect               rework                  invoice format with
                                                  qualified finance/tax
                                                  stakeholders

  Provider outages affect Missed notifications    Provider abstraction,
  customer communication                          retries, delivery
                                                  status, and operational
                                                  fallback

  Production              Deployment failure or   Environment templates,
  configuration drift     security exposure       secret management,
                                                  configuration
                                                  validation, release
                                                  rehearsal
  -----------------------------------------------------------------------

## 14. Planning notes and assumptions

-   Release boundaries are logical delivery milestones, not fixed
    calendar commitments.
-   Dates and sprint counts should be estimated after architecture
    review, backlog refinement, and team-capacity assessment.
-   Each release may be split into smaller deployable increments.
-   GST, invoice numbering, payment-provider, SMS, WhatsApp, and
    warranty policies require business/provider confirmation.
-   Data retention, privacy, consent, and applicable regulatory
    requirements must be reviewed before production rollout.
-   Existing application behavior and deployed integrations should be
    preserved unless a change is explicitly approved.
-   Any cross-release dependency discovered during implementation should
    be documented and resolved before the dependent feature is marked
    complete.

------------------------------------------------------------------------

**End of release plan**
