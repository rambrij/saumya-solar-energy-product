#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-backend/src/main/java}"
if [[ ! -d "$ROOT" ]]; then
  echo "ERROR: source root not found: $ROOT" >&2
  exit 2
fi

echo "# Repository asynchronous and scheduled execution inventory"
echo "# Root: $ROOT"
echo "# Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo
echo "## Scheduled triggers"
rg -n -C 2 --glob '*.java' '@Scheduled' "$ROOT" || true
echo
echo "## Async annotations and executor declarations/submissions"
rg -n -C 2 --glob '*.java' '@Async|ExecutorService|ScheduledExecutorService|ThreadPoolTaskExecutor|TaskExecutor|TaskScheduler|CompletableFuture|ForkJoinPool|new Thread\(|\.submit\(|\.execute\(' "$ROOT" || true
echo
echo "## Application event producers and listeners"
rg -n -C 2 --glob '*.java' '@EventListener|@TransactionalEventListener|ApplicationEventPublisher|publishEvent\(' "$ROOT" || true
echo
echo "## Messaging and queue consumers/producers"
rg -n -C 2 --glob '*.java' '@KafkaListener|@RabbitListener|@JmsListener|KafkaTemplate|RabbitTemplate|JmsTemplate|MessageListener|Queue|Topic' "$ROOT" || true
echo
echo "## Startup runners (review for one-shot data mutation and bootstrap behavior)"
rg -n -C 2 --glob '*.java' 'CommandLineRunner|ApplicationRunner' "$ROOT" || true
echo
echo "## Non-Java workflow/scheduler definitions"
for directory in backend/src/main/resources .github/workflows; do
  if [[ -d "$directory" ]]; then
    rg -n -C 1 --glob '*.yml' --glob '*.yaml' --glob '*.properties' --glob '*.xml' 'schedule:|fixedDelay|fixedRate|cron:|scheduler|worker|queue|consumer' "$directory" || true
  fi
done
