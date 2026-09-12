#!/usr/bin/env bash
# 四个仓库保留相同部署入口；仅操作传入项目的服务，不启动或重建依赖。
set -euo pipefail
component="${1:?component required}"
tag="${2:?tag required}"
cd "${3:?deploy path required}"
[[ "$tag" =~ ^v[A-Za-z0-9_.-]+$ ]] || { echo "Invalid release tag" >&2; exit 1; }
case "$component" in
  aether) services=(admin front); running=(admin front); artifact=admin ;;
  dashboard) services=(dashboard); running=(dashboard); artifact=dashboard ;;
  deep-agent) services=(deep-agent); running=(deep-agent); artifact=deep-agent ;;
  mcp) services=(mcp sandbox-runner sandbox-runtime-python sandbox-runtime-node); running=(mcp sandbox-runner); artifact=mcp ;;
  *) echo "Unknown component: $component" >&2; exit 1 ;;
esac
# GitHub concurrency 只在单个仓库内生效；跨仓库的 Compose 操作用宿主机锁串行执行。
exec 9>"${AETHER_DEPLOY_LOCK_FILE:-${HOME:?}/.aether-prod-deploy.lock}"
flock -w 1800 9
export AETHER_RELEASE_TAG="$tag"
export AETHER_RELEASE_ROOT="$PWD/release/$tag"
export COMPOSE_IGNORE_ORPHANS=true
test -f deploy/.env || { echo "Prepare $PWD/deploy/.env from deploy/.env.example" >&2; exit 1; }
overlay="release/$tag/$artifact/deploy/compose.yml"
test -f "$overlay"
if [[ "$component" == aether && -f "release/$tag/admin/release-manifest.txt" ]]; then
  cat "release/$tag/admin/release-manifest.txt"
fi
compose=(docker compose --env-file deploy/.env -f "$overlay")
"${compose[@]}" config --quiet
# 先完成本项目构建，构建失败时不动容器。
"${compose[@]}" --profile build build "${services[@]}"
if [[ "$component" == aether ]]; then
  pg_image=$("${compose[@]}" config --format json | python3 -c 'import json,sys; print(json.load(sys.stdin)["services"]["postgres"]["image"])')
  [[ "$pg_image" == *pg18* ]] || { echo "Expected pg18; migrate database before publishing" >&2; exit 1; }
  # 仅首次创建基础设施；后续 tag 不替换现有数据库/Redis。
  "${compose[@]}" up -d --no-deps --no-recreate --wait --wait-timeout 300 postgres redis
  if "${compose[@]}" config --services | grep -qx minio; then
    "${compose[@]}" up -d --no-deps --no-recreate --wait --wait-timeout 300 minio
  fi
fi
# 不包含一次性 runtime 容器；它们只需 build，Runner 直接使用对应镜像。
# --no-deps 保证不更新其他仓库或基础设施；任何等待失败均保留失败状态。
if [[ "$component" == aether ]]; then
  # Admin 完成 Flyway 且健康后，才启动 Front。
  "${compose[@]}" up -d --no-deps --no-build --wait --wait-timeout 300 admin
  "${compose[@]}" up -d --no-deps --no-build --wait --wait-timeout 300 front
else
  "${compose[@]}" up -d --no-deps --no-build --wait --wait-timeout 300 "${running[@]}"
fi
if [[ "$component" == dashboard ]]; then
  "${compose[@]}" exec -T dashboard wget -q -O /dev/null http://127.0.0.1/
fi
mkdir -p .releases
state=".releases/$component.tag"
if [[ -f "$state" && "$(cat "$state")" != "$tag" ]]; then
  cp "$state" ".releases/$component.previous.tag"
fi
printf '%s\n' "$tag" > "$state.tmp"
mv "$state.tmp" "$state"
"${compose[@]}" ps "${running[@]}"
echo "Released $component: $tag"
echo "Rollback: bash release/<previous-tag>/$artifact/deploy/release.sh $component <previous-tag> '$PWD'"
