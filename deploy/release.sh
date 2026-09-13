#!/usr/bin/env bash
# 仅操作传入项目的服务；所有项目共用部署根目录的 .env。
set -euo pipefail
component="${1:?component required}"
tag="${2:?tag required}"
project_dir="${3:?project directory required}"
release_id="${4:?release id required}"

[[ "$tag" =~ ^v[A-Za-z0-9_.-]+$ ]] || { echo "Invalid release tag" >&2; exit 1; }
[[ "$release_id" =~ ^[0-9]{8}T[0-9]{6}Z-v[A-Za-z0-9_.-]+$ ]] || { echo "Invalid release id" >&2; exit 1; }
case "$component" in
  aether) services=(admin front); running=(admin front); artifact=admin ;;
  dashboard) services=(dashboard); running=(dashboard); artifact=dashboard ;;
  deep-agent) services=(deep-agent); running=(deep-agent); artifact=deep-agent ;;
  mcp) services=(mcp sandbox-runner sandbox-runtime-python sandbox-runtime-node); running=(mcp sandbox-runner); artifact=mcp ;;
  *) echo "Unknown component: $component" >&2; exit 1 ;;
esac

cd "$project_dir"
env_file="$(dirname "$PWD")/.env"
release_root="$PWD/releases/$release_id"
overlay="$release_root/$artifact/deploy/compose.yml"
test -f "$env_file" || { echo "Missing shared environment file: $env_file" >&2; exit 1; }
test -f "$overlay" || { echo "Missing release overlay: $overlay" >&2; exit 1; }

# 跨仓库部署串行，避免同时修改共享 Docker 资源。
exec 9>"${AETHER_DEPLOY_LOCK_FILE:-${HOME:?}/.aether-deploy.lock}"
flock -w 1800 9
export AETHER_RELEASE_TAG="$tag"
export AETHER_RELEASE_ROOT="$release_root"
export COMPOSE_IGNORE_ORPHANS=true
compose=(docker compose --env-file "$env_file" -f "$overlay")
"${compose[@]}" config --quiet

# PostgreSQL、Redis 和 MinIO 是 Aether 的共享运行资源。已有容器可能由较早的
# 发布版本创建，未必带有当前 Compose 的标签；此时交给 Compose 会尝试创建同名
# 容器并失败。发布时只启动、等待已有容器，缺失时才由当前发布创建。
ensure_shared_service() {
  local service="$1"
  local container="$2"
  local status

  if docker container inspect "$container" >/dev/null 2>&1; then
    if [[ "$(docker inspect --format '{{.State.Running}}' "$container")" != "true" ]]; then
      docker start "$container" >/dev/null
    fi

    for _ in {1..150}; do
      status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{if .State.Running}}running{{else}}stopped{{end}}{{end}}' "$container")"
      case "$status" in
        healthy|running) return 0 ;;
        unhealthy|exited|dead) echo "Shared container $container is $status" >&2; return 1 ;;
      esac
      sleep 2
    done
    echo "Timed out waiting for shared container $container" >&2
    return 1
  fi

  "${compose[@]}" up -d --no-deps --no-recreate --wait --wait-timeout 300 "$service"
}

if [[ "${AETHER_PREBUILT_IMAGES:-false}" == true ]]; then
  for service in "${services[@]}"; do
    image=$("${compose[@]}" config --format json | python3 -c 'import json,sys; print(json.load(sys.stdin)["services"][sys.argv[1]]["image"])' "$service")
    docker image inspect "$image" >/dev/null
  done
else
  "${compose[@]}" --profile build build "${services[@]}"
fi
if [[ "$component" == aether ]]; then
  pg_image=$("${compose[@]}" config --format json | python3 -c 'import json,sys; print(json.load(sys.stdin)["services"]["postgres"]["image"])')
  [[ "$pg_image" == *pg16* ]] || { echo "Expected pg16 for the existing production data volume" >&2; exit 1; }
  ensure_shared_service postgres aether-postgres
  ensure_shared_service redis aether-redis
  if "${compose[@]}" config --services | grep -qx minio; then
    ensure_shared_service minio aether-minio
  fi
fi
if [[ "$component" == aether ]]; then
  "${compose[@]}" up -d --no-deps --no-build --wait --wait-timeout 300 admin
  "${compose[@]}" up -d --no-deps --no-build --wait --wait-timeout 300 front
else
  "${compose[@]}" up -d --no-deps --no-build --wait --wait-timeout 300 "${running[@]}"
fi
if [[ "$component" == dashboard ]]; then "${compose[@]}" exec -T dashboard wget -q -O /dev/null http://127.0.0.1/; fi

mkdir -p .releases
state=".releases/$component.current"
if [[ -f "$state" ]]; then cp "$state" ".releases/$component.previous"; fi
{ printf 'release_id=%s\n' "$release_id"; printf 'tag=%s\n' "$tag"; } > "$state.tmp"
mv "$state.tmp" "$state"
"${compose[@]}" ps "${running[@]}"
echo "Released $component: $release_id"
echo "Rollback: source .releases/$component.previous && bash \"releases/\$release_id/$artifact/deploy/release.sh\" $component \"\$tag\" \"$PWD\" \"\$release_id\""
