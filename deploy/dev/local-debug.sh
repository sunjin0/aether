#!/usr/bin/env bash
# 在本机打包并启动隔离的 Aether Docker 调试环境，不连接部署服务器。
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="${AETHER_LOCAL_ENV_FILE:-$repo_root/deploy/dev/.env.local}"
release_tag="${AETHER_LOCAL_RELEASE_TAG:-local}"
release_id="$(date -u +%Y%m%dT%H%M%SZ)-$release_tag"
release_root="$repo_root/.local-debug/releases/$release_id"
compose=(docker compose --env-file "$env_file" -f "$repo_root/deploy/compose.yml" -f "$repo_root/deploy/dev/compose.yml")

[[ "$release_tag" =~ ^[A-Za-z0-9_.-]+$ ]] || { echo "Invalid local release tag: $release_tag" >&2; exit 1; }
test -f "$env_file" || {
  echo "Missing local environment file: $env_file" >&2
  echo "Create it with: cp deploy/.env.example deploy/dev/.env.local" >&2
  exit 1
}

stage_component() {
  local component="$1"
  local jar
  mkdir -p "$release_root/$component/deploy"
  jar="$(find "$repo_root/$component/target" -maxdepth 1 -type f -name "$component-*.jar" ! -name '*-sources.jar' ! -name '*-javadoc.jar')"
  test -n "$jar" || { echo "Missing $component JAR; Maven package did not produce it" >&2; exit 1; }
  cp "$jar" "$release_root/$component/$component.jar"
  cp "$repo_root/deploy/$component.Dockerfile" "$release_root/$component/deploy/"
  cp "$repo_root/deploy/.dockerignore" "$release_root/$component/.dockerignore"
}

cd "$repo_root"
echo "Packaging Admin and Front..."
mvn -B -ntp -pl admin,front -am -DskipTests package

stage_component admin
stage_component front

echo "Building local Docker images..."
docker build -f "$release_root/admin/deploy/admin.Dockerfile" -t "aether-admin:release-$release_tag" "$release_root/admin"
docker build -f "$release_root/front/deploy/front.Dockerfile" -t "aether-front:release-$release_tag" "$release_root/front"

export AETHER_RELEASE_TAG="$release_tag"
export AETHER_RELEASE_ROOT="$release_root"
"${compose[@]}" config --quiet
"${compose[@]}" up -d --no-build --wait --wait-timeout 300 postgres redis admin front
"${compose[@]}" ps postgres redis admin front

echo "Local Admin: http://127.0.0.1:${LOCAL_ADMIN_PORT:-18080}"
echo "Local Front: http://127.0.0.1:${LOCAL_FRONT_PORT:-18081}"
