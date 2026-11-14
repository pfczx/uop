#!/usr/bin/env bash
set -e

echo "============================================================"
echo "  UOP DEV ENVIRONMENT"
echo "============================================================"

# Load .env if present
if [ -f .env ]; then
  set -a
  . .env
  set +a
fi

export POSTGRES_DB="${POSTGRES_DB:-uop}"
export POSTGRES_USER="${POSTGRES_USER:-postgres}"
export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-postgres}"
export POSTGRES_PORT=5433

echo "[1/3] Starting Docker Postgres (detached)..."
docker compose -f docker-compose.dev.yml down -v 2>/dev/null || true
docker compose -f docker-compose.dev.yml up -d

echo "[2/3] Waiting for Postgres to be healthy..."
for i in $(seq 1 30); do
  if docker compose -f docker-compose.dev.yml exec -T postgres pg_isready -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" >/dev/null 2>&1; then
    echo "    Postgres is ready."
    break
  fi
  echo "    waiting... ($i/30)"
  sleep 2
done

echo "[3/3] Starting Spring Boot with dev profile..."
echo ""
echo "============================================================"
echo "  Dev endpoints:"
echo "    App:           http://localhost:8080"
echo "    Swagger UI:    http://localhost:8080/swagger-ui.html"
echo "    Dev API:       http://localhost:8080/api/dev"
echo "    Actuator:      http://localhost:8081/actuator/health"
echo "============================================================"
echo ""

./mvnw -pl uop-backend spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.jvmArguments="-Dfile.encoding=UTF-8"
