#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-backend/src/main/java}"
if [[ ! -d "$ROOT" ]]; then
  echo "ERROR: source root not found: $ROOT" >&2
  exit 2
fi

scan_java() {
  local title="$1"
  local pattern="$2"
  echo "## $title"
  grep -RInE --include='*.java' "$pattern" "$ROOT" || true
  echo
}

echo "# Repository asynchronous and scheduled execution inventory"
echo "# Root: $ROOT"
echo "# Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo
scan_java "Scheduled triggers" '@Scheduled'
scan_java "Async annotations and executor declarations/submissions" '@Async|ExecutorService|ScheduledExecutorService|ThreadPoolTaskExecutor|TaskExecutor|TaskScheduler|CompletableFuture|ForkJoinPool|new Thread\(|(executor|taskExecutor|threadPool|pool)\.(submit|execute)\('
scan_java "Application event producers and listeners" '@EventListener|@TransactionalEventListener|ApplicationEventPublisher|publishEvent\('
scan_java "Startup runners" 'CommandLineRunner|ApplicationRunner'

echo "## Non-Java workflow/scheduler definitions"
for directory in backend/src/main/resources .github/workflows; do
  if [[ -d "$directory" ]]; then
    grep -RInE --include='*.yml' --include='*.yaml' --include='*.properties' --include='*.xml' 'schedule:|fixedDelay|fixedRate|cron:|scheduler|worker' "$directory" || true
  fi
done
