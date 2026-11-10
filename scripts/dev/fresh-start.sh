#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

for command in docker curl; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Required command not found: $command" >&2
    exit 1
  fi
done

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is required." >&2
  exit 1
fi

PROJECT_NAME="$(docker compose config --format json | python3 -c 'import json,sys; print(json.load(sys.stdin).get("name", ""))')"
if [[ -z "$PROJECT_NAME" ]]; then
  echo "Could not determine the Docker Compose project name." >&2
  exit 1
fi

cat <<'WARNING'
This will permanently delete the local MySQL database volume for this Compose project.
No SQL backup/export will be created.
Uploaded files, private attachments, and export-file volumes are preserved.
WARNING

read -r -p "Type RESET to remove the MySQL database and recreate it from V1__baseline.sql: " CONFIRMATION
if [[ "$CONFIRMATION" != "RESET" ]]; then
  echo "Fresh start cancelled; no containers or volumes were changed."
  exit 0
fi

docker compose down --remove-orphans

mapfile -t MYSQL_VOLUMES < <(
  docker volume ls \
    --filter "label=com.docker.compose.project=$PROJECT_NAME" \
    --filter "label=com.docker.compose.volume=mysql_data" \
    --format '{{.Name}}'
)

if (( ${#MYSQL_VOLUMES[@]} > 1 )); then
  echo "More than one mysql_data volume was found for project $PROJECT_NAME; refusing to guess." >&2
  printf '  %s\n' "${MYSQL_VOLUMES[@]}" >&2
  exit 1
fi

if (( ${#MYSQL_VOLUMES[@]} == 1 )); then
  docker volume rm "${MYSQL_VOLUMES[0]}"
else
  echo "No existing MySQL volume found; Docker will create a new one."
fi

docker compose up --build -d mysql mailpit

MYSQL_READY=false
for attempt in $(seq 1 60); do
  if docker compose exec -T mysql mysqladmin ping -h 127.0.0.1 -uroot -p"${MYSQL_ROOT_PASSWORD:-rootpass}" --silent >/dev/null 2>&1; then
    MYSQL_READY=true
    break
  fi
  sleep 2
done
if [[ "$MYSQL_READY" != "true" ]]; then
  docker compose logs mysql
  echo "MySQL did not become ready." >&2
  exit 1
fi

docker compose up --build -d backend frontend

BACKEND_READY=false
for attempt in $(seq 1 90); do
  if curl --fail --silent http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; then
    BACKEND_READY=true
    break
  fi
  if [[ "$(docker compose ps --status running -q backend)" == "" ]]; then
    docker compose logs backend
    echo "Backend container stopped before becoming healthy." >&2
    exit 1
  fi
  sleep 2
done
if [[ "$BACKEND_READY" != "true" ]]; then
  docker compose logs backend
  echo "Backend did not become healthy." >&2
  exit 1
fi

FRONTEND_READY=false
for attempt in $(seq 1 45); do
  if curl --fail --silent http://127.0.0.1:3000/ >/dev/null; then
    FRONTEND_READY=true
    break
  fi
  sleep 2
done
if [[ "$FRONTEND_READY" != "true" ]]; then
  docker compose logs frontend
  echo "Frontend did not become ready." >&2
  exit 1
fi

echo "Fresh start completed."
echo "MySQL: fresh database; Flyway V1 baseline applied by Spring Boot."
echo "Backend: http://localhost:8080 (health: http://localhost:8080/actuator/health)"
echo "Frontend: http://localhost:3000"
echo "Mailpit: http://localhost:8025"
