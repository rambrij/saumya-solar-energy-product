#!/usr/bin/env bash
set -euo pipefail

ROOT="$PWD"
if [[ $# -gt 0 ]]; then ROOT="$1"; fi
if [[ ! -d "$ROOT" ]]; then
  echo "ERROR: repository root not found: $ROOT" >&2
  exit 2
fi

grep_repo() {
  local title="$1"
  local includes="$2"
  local pattern="$3"
  echo "## $title"
  # Scan checked-in source and deployment configuration, excluding generated/dependency trees.
  # grep (rather than ripgrep) is available on the standard GitHub Actions runner.
  # shellcheck disable=SC2086
  grep -RInE --binary-files=without-match \
    --exclude-dir=.git --exclude-dir=node_modules --exclude-dir=target --exclude-dir=test \
    --exclude-dir=dist --exclude-dir=build --exclude-dir=coverage \
    --exclude-dir=.next --exclude-dir=.venv --exclude-dir=venv \
    --exclude='*lock*' --exclude='*.md' --exclude='*.svg' --exclude='*.min.js' --exclude='scan-async-workers.sh' \
    $includes "$pattern" "$ROOT" || true
  echo
}

echo "# Repository-wide asynchronous, scheduled and queue execution inventory"
echo "# Root: $ROOT"
echo "# Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo
grep_repo "Java/Spring scheduled triggers" "--include=*.java" '@Scheduled'
grep_repo "Java/Spring async, executor and thread boundaries" "--include=*.java" '@Async|ExecutorService|ScheduledExecutorService|ThreadPoolTaskExecutor|TaskExecutor|TaskScheduler|CompletableFuture|ForkJoinPool|new Thread\(|(executor|taskExecutor|threadPool|pool)\.(submit|execute)\('
grep_repo "Application events and messaging APIs" "--include=*.java" '@EventListener|@TransactionalEventListener|ApplicationEventPublisher|publishEvent\(|@KafkaListener|@RabbitListener|@JmsListener|KafkaTemplate|RabbitTemplate|JmsTemplate|MessageListener'
grep_repo "Java startup runners" "--include=*.java" 'CommandLineRunner|ApplicationRunner'

grep_repo "JavaScript/TypeScript timers, async workers and queue clients" "--include=*.js --include=*.jsx --include=*.ts --include=*.tsx --include=*.mjs --include=*.cjs" 'setInterval\(|setTimeout\(|scheduleJob\(|cron\.schedule|node-cron|bullmq|new Worker\(|new Queue\(|Agenda\(|worker_threads|Worker\('
grep_repo "Python/Go/other worker and task primitives" "--include=*.py --include=*.go --include=*.rb" 'celery|@shared_task|@app\.task|rq\.Queue|dramatiq|sidekiq|BackgroundTasks|time\.Sleep|go func\(|cron\.New|AddFunc\('
grep_repo "Cloud queue/event integrations across source" "--include=*.java --include=*.js --include=*.jsx --include=*.ts --include=*.tsx --include=*.py --include=*.go --include=*.rb" 'SQS|SNS|EventBridge|Kafka|RabbitMQ|AMQP|JMS|ServiceBus|PubSub|pubsub|QueueClient|sendMessage|receiveMessage|subscribe\('

grep_repo "Deployment and workflow scheduling/worker declarations" "--include=*.yml --include=*.yaml --include=*.json --include=*.toml --include=*.xml --include=*.properties --include=Dockerfile --include=docker-compose.yml --include=*.sh" 'schedule:|fixedDelay|fixedRate|cron:|CronJob|scheduler|worker|queue|consumer|replicas:|restart:|command:|entrypoint:'
