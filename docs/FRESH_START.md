# Fresh local environment

The repository is pre-production and uses a single clean-install Flyway migration:

`backend/src/main/resources/db/migration/V1__baseline.sql`

The baseline contains final-state `CREATE TABLE` definitions. It has no `ALTER TABLE` or standalone `CREATE INDEX` statements. Spring Boot applies it automatically on the first backend startup; Hibernate then validates the mapped schema.

## Reset the local database and restart the stack

From any directory in the repository:

```bash
bash scripts/dev/fresh-start.sh
```

The script asks for the exact confirmation `RESET` before deleting the Compose-managed `mysql_data` volume. It then starts MySQL and Mailpit, starts the backend and frontend, and waits for backend health and the frontend HTTP response.

**Destructive scope:** all local MySQL database contents are permanently removed. No SQL backup/export is made. The script deliberately preserves Compose volumes for uploads, private service/survey/warranty attachments, and export files. It does not touch any remote database or cloud environment.

## URLs

- Frontend: http://localhost:3000
- Backend health: http://localhost:8080/actuator/health
- Mailpit inbox: http://localhost:8025

## Manual alternative

```bash
docker compose down --remove-orphans
# Remove only this Compose project's mysql_data volume (not uploads or private-file volumes).
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-$(basename "$PWD" | tr '[:upper:]' '[:lower:]')}"
MYSQL_VOLUME="$(docker volume ls --filter "label=com.docker.compose.project=$PROJECT_NAME" --filter "label=com.docker.compose.volume=mysql_data" --format '{{.Name}}')"
if [ -n "$MYSQL_VOLUME" ]; then docker volume rm "$MYSQL_VOLUME"; fi
docker compose up --build -d
```

