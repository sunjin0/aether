#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$SCRIPT_DIR"

cp -n .env.example .env 2>/dev/null || true
source .env

echo "=== Building Java apps ==="
echo "Building admin..."
mvn clean package -pl admin -am -DskipTests -q
echo "Building front..."
mvn clean package -pl front -am -DskipTests -q

echo "=== Starting services with Docker Compose ==="
docker compose up -d --build

echo "=== Services ==="
echo "  Admin:  http://localhost:${ADMIN_PORT:-8080}"
echo "  Front:  http://localhost:${FRONT_PORT:-8081}"
echo "  MinIO:  http://localhost:${MINIO_PORT:-9000}"
echo "  MinIO Console: http://localhost:${MINIO_CONSOLE_PORT:-9001}"
echo "  MCP:    http://localhost:${MCP_PORT:-8000}"
echo ""
echo "To tail logs: docker compose logs -f"
echo "To stop:      docker compose down"
