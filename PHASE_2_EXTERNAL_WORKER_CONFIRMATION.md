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
