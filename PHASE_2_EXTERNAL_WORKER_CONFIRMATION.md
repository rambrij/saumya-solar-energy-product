# Phase 2 external worker and scheduler confirmation

## Purpose

The checked-in source scan is not proof that production has no external workers. This register is for the infrastructure owner to reconcile the repository inventory against every deployed account, cluster, project, queue and scheduler.

**Release gate:** keep Phase 2 production approval blocked until every applicable row is completed, evidence is archived, and the infrastructure owner signs the confirmation.

## Checked-in source baseline

The reviewed repository scan artifact from CI run 42915067542 reports:

- Seven scheduled triggers.
- Two `@Async` application listeners.
- One bounded `ThreadPoolTaskExecutor`.
- A database-backed integration queue.
- Two startup runners.
- No checked-in Kafka, RabbitMQ/AMQP, JMS, SQS/SNS/EventBridge worker client, Python/Go/Ruby worker, BullMQ/Agenda/node-cron worker, or separate Docker worker container.
- DataSeeder and PlatformAdminBootstrap are property-gated. Confirm `SEED_DATA_ENABLED=false` and `PLATFORM_BOOTSTRAP_ENABLED=false` in production unless an approved operational exception exists.

This is a source-only baseline; it must be refreshed against the latest release commit before sign-off.

## External runtime inventory

Complete one row per production account, region, cluster, namespace, project, queue or scheduled registration. Use “NONE — verified” only when the owner has checked the relevant control plane.

| Platform / control plane | Resource type to inspect | Resource identifier / region / namespace | Enabled? | Target service / command | Tenant-context / idempotency evidence | Owner | Evidence URL / export timestamp | Status |
|---|---|---|---|---|---|---|---|---|
| AWS | EventBridge rules / Scheduler schedules | | | | | | | PENDING |
| AWS | Lambda functions and event-source mappings | | | | | | | PENDING |
| AWS | ECS services, scheduled tasks and task definitions | | | | | | | PENDING |
| AWS | EKS CronJobs, Jobs, Deployments and consumers | | | | | | | PENDING |
| AWS | SQS queues, DLQs and Lambda consumers | | | | | | | PENDING |
| AWS | SNS subscriptions / EventBridge targets | | | | | | | PENDING |
| Kubernetes | CronJobs, Jobs, operators and external controllers | | | | | | | PENDING |
| GitHub | Scheduled workflows and repository dispatch hooks | | | | | | | PENDING |
| Render / other PaaS | Cron jobs, background workers and private services | | | | | | | PENDING |
| Vercel / other serverless | Cron jobs, workflows and queue consumers | | | | | | | PENDING |
| External vendors | Webhook delivery, retry services and managed queues | | | | | | | PENDING |
| Other | Any additional production control plane | | | | | | | PENDING |

## Required verification

For every discovered worker or scheduled registration, confirm:

1. The deployed artifact/image and commit correspond to the approved release.
2. The trigger, cadence, timezone, retry policy, timeout and concurrency limit are documented.
3. Every tenant-owned operation establishes and clears tenant context at the worker boundary; no request-thread context is assumed.
4. Queue claims and state transitions are tenant-qualified and safe under concurrent delivery.
5. Idempotency keys and retry behavior prevent duplicate business records or side effects.
6. Failed jobs, poison messages and DLQs have monitored ownership and a replay procedure.
7. Shutdown drains or safely releases in-flight work.
8. Production seeding/bootstrap runners are disabled unless explicitly approved.
9. Secrets and queue permissions are least-privilege and environment-scoped.
10. The deployment schedule and source-code inventory agree; unexplained registrations are release blockers.

## Owner sign-off

- Production environment/account(s):
- Application release commit:
- Infrastructure inventory export timestamp:
- Infrastructure owner:
- Security reviewer:
- Open exceptions and ticket references:
- Confirmation: `I confirm all production worker, scheduler, queue, event-source and external retry registrations have been inventoried and reconciled against this release.`
- Owner approval / date:

**Current status: NOT CONFIRMED.** No production cloud account, cluster, queue or scheduler registration was queried during the repository audit. Do not convert the source-only scan into an external-runtime PASS.


## Connected provider inspection — 2026-11-11

The repository audit session inspected the provider accounts exposed through the connected Render and Vercel integrations. This is useful evidence about the connected workspaces only; it is not proof that all production accounts, organizations or cloud projects were connected.

| Provider | Connected scope inspected | Observed resources | Worker / schedule finding | Target repository match | Status |
|---|---|---|---|---|---|
| Render | Workspace **My Workspace** (tea-dbkn6fvf3r2c73fcnu80) | One service: **soumya-solar-api**, type **web_service**, branch **main**, repository **rambrij/soumya-solar-energy-solution**, region Singapore | The service inventory returned no separate cron job, background worker, private worker or queue consumer in this accessible workspace. The listed resource is a web service only. | **No** — the service points to the older soumya-solar-energy-solution repository, not saumya-solar-energy-product. | OBSERVED — not target-project sign-off |
| Vercel | Team **rambrij2010-2011's projects** (team_hs3I9BOSOsksJMvAnrYVIS26) | One Git-linked project: **soumya-solar-energy-solution** (prj_KDK4keKw5NWqkmJdDxjEp6LZ6Guk), Vite; project detail reports live=false and a latest deployment with production target READY. | The project detail response does not expose cron registration inventory. No positive or negative claim about Vercel Cron is made from this response. Inspect the Vercel project's Cron Jobs dashboard/API and repository vercel.json/function config before closure. | **No** — the linked project points to the older soumya-solar-energy-solution repository. | PARTIAL — schedule evidence still pending |
| AWS | No AWS account/organization connection was available in this session | Not queried | EventBridge/Scheduler, Lambda event-source mappings, ECS scheduled tasks, EKS CronJobs/Jobs, SQS/DLQs, SNS subscriptions and other AWS resources remain unverified. | Not established | PENDING — AWS owner confirmation required |
| Kubernetes / other cloud / vendors | No additional connected control plane was available | Not queried | Production namespaces, provider cron jobs, managed queues, webhooks/retries and any external worker deployment remain unverified. | Not established | PENDING — infrastructure owner confirmation required |

**Evidence boundary:** Render's service listing covered the accessible workspace, not every possible Render workspace. Vercel's linked-project listing covered the connected team; the project metadata did not enumerate Cron Jobs. Both observed projects point to the older repository, so neither establishes the deployment topology for saumya-solar-energy-product. The checked-in source inventory remains complete for repository code/config only.

**Release gate:** keep external worker discovery NOT CONFIRMED until the infrastructure owner supplies the production AWS/other cloud account and Vercel/Render schedule exports for the environment actually serving saumya-solar-energy-product, or signs an explicit NONE — verified for each control plane. Attach export timestamps, resource identifiers, owner and tenant-context/idempotency evidence to the table above.


## Refreshed source and connected-provider inventory — 2026-11-11

**Source scan checkpoint:** GitHub Actions run [42967893781](https://github.com/rambrij/saumya-solar-energy-product/actions/runs/42967893781), commit `10287b76fc75e6bbf21eaee65f70d048b074cd1b`. The CI scanner was run again at this commit. It found:

- **Seven Spring scheduled triggers:** authentication rate-limit cleanup, export recovery/expiry, subscription expiry, service SLA evaluation, notification retry, warranty expiry and integration sync dispatch.
- **Two `@Async` application listeners:** business notification delivery and export event processing.
- **One bounded `ThreadPoolTaskExecutor`** for application events.
- **Two startup runners:** DataSeeder and PlatformAdminBootstrap; both are configuration/property gated and must remain disabled in production unless explicitly approved.
- **No checked-in broker/managed-queue client or separate worker implementation** for Kafka, RabbitMQ/AMQP, JMS, SQS/SNS/EventBridge, Python/Go/Ruby task workers, BullMQ/Agenda/node-cron, or separate Docker worker containers. The browser-side `setTimeout` in the export panel is UI polling, not a server-side worker.
- The integration sync queue is database-backed and dispatched by the checked-in scheduler. The CI inventory remains a source/config scan, not a production cloud control-plane export.

### Connected workspace refresh performed during this audit

| Provider | Exact connected scope inspected | Result | Acceptance interpretation |
|---|---|---|---|
| Render | Workspace `tea-dbkn6fvf3r2c73fcnu80` (My Workspace); `list_services` including previews | Exactly one resource returned: `soumya-solar-api`, type `web_service`, branch `main`, repository `rambrij/soumya-solar-energy-solution`, Singapore. No Cron Job/background-worker service appeared in this workspace listing. | **Not target-project confirmation.** The service is wired to the older repository, not `saumya-solar-energy-product`. Other Render workspaces are not proven absent. |
| Vercel | Team `team_hs3I9BOSOsksJMvAnrYVIS26` and its project list/detail | Exactly one project: `soumya-solar-energy-solution`, linked to `rambrij/soumya-solar-energy-solution`; project detail reports `live=false` and latest production deployment `READY`. | **Not target-project confirmation.** The connected project points to the older repository. The connected Vercel tools available here did not expose a project Cron Job inventory, so no claim that Vercel Cron is absent is made. |
| AWS | No AWS account/organization connector exposed in this audit | EventBridge/Scheduler, Lambda event sources, ECS scheduled tasks, EKS CronJobs, SQS/DLQs and SNS subscriptions were not queried. | **PENDING** — AWS infrastructure owner must provide account/region exports or explicitly sign `NONE — verified`. |
| Kubernetes / other cloud / providers | No additional applicable production control plane was connected | Not queried. | **PENDING** — infrastructure owner must identify every production cluster, PaaS, scheduler, webhook relay and managed queue. |

**Reconciliation result:** checked-in source/config discovery is refreshed and MET for the repository snapshot above. The accessible Render/Vercel workspaces were inspected, but they point to the older repository. There is therefore no proven mapping from those workspaces to the production environment serving `saumya-solar-energy-product`. External worker registration confirmation remains **NOT CONFIRMED** until the production infrastructure owner supplies the target environment inventory and signs this register.
