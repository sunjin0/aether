#!/usr/bin/env bash
#
# 在部署机上检出/更新四个源码仓库，供 docker-compose.prod.yml 以本地目录作为构建上下文。
#
# 为什么不在 Compose 里用 Git 构建上下文：BuildKit 的 git 源不会读取宿主机的 git 配置，
# credential.helper、~/.git-credentials、~/.netrc、SSH 私钥一律不生效，私有仓库必然拉不下来。
# 改由本脚本用宿主机已配置好的 git 凭据（HTTPS credential helper 或 SSH key）检出源码，
# Compose 只负责从本地目录构建，两边职责分开。
#
# 用法：
#   ./scripts/fetch-sources.sh              # 检出 .env.prod 中配置的 ref
#   ./scripts/fetch-sources.sh --no-clean   # 保留未跟踪文件（默认会清掉）
#   ENV_FILE=/path/to/.env.prod ./scripts/fetch-sources.sh
#
# 注意：默认会对每个仓库执行 `git clean -ffdx`，即删除未跟踪且被忽略的文件（构建产物、
# 本地临时文件等），以保证构建上下文与提交内容严格一致。仓库目录由本脚本专用，不要放别的东西。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env.prod}"
DO_CLEAN=1

for arg in "$@"; do
  case "$arg" in
    --no-clean) DO_CLEAN=0 ;;
    -h|--help) sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'; exit 0 ;;
    *) echo "未知参数：$arg（可用 --no-clean）" >&2; exit 2 ;;
  esac
done

if [ ! -f "$ENV_FILE" ]; then
  echo "找不到环境文件：$ENV_FILE" >&2
  echo "先执行 cp .env.prod.example .env.prod 并填写。" >&2
  exit 1
fi

command -v git >/dev/null 2>&1 || { echo "未找到 git。" >&2; exit 1; }

# 读取 .env.prod。该文件是 Compose 环境文件，格式与 shell 变量赋值兼容；
# 若值中含 $ 或反引号会被 shell 展开，请对这些字符加引号或转义。
set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

SOURCE_ROOT="${AETHER_SOURCE_ROOT:-.sources}"
case "$SOURCE_ROOT" in
  /*) ;;
  *) SOURCE_ROOT="$ROOT_DIR/$SOURCE_ROOT" ;;
esac

# 仓库目录名 | 地址变量 | ref 变量 | 说明
REPOS=(
  "aether|AETHER_ADMIN_GIT_URL|AETHER_ADMIN_GIT_REF|Java Admin 与 Front"
  "aether-deep-agent-service|AETHER_DEEP_AGENT_GIT_URL|AETHER_DEEP_AGENT_GIT_REF|Python Deep Agent"
  "aether-mcp-server|AETHER_MCP_GIT_URL|AETHER_MCP_GIT_REF|MCP、Sandbox Runner 与两个沙箱运行时"
  "aether-dashboard|AETHER_DASHBOARD_GIT_URL|AETHER_DASHBOARD_GIT_REF|React 管理台"
)

mkdir -p "$SOURCE_ROOT"

summary=()

for entry in "${REPOS[@]}"; do
  IFS='|' read -r name url_var ref_var desc <<<"$entry"

  url="${!url_var:-}"
  ref="${!ref_var:-}"
  dir="$SOURCE_ROOT/$name"

  if [ -z "$url" ]; then
    echo "✗ $name：$url_var 未在 $ENV_FILE 中配置" >&2
    exit 1
  fi
  if [ -z "$ref" ]; then
    echo "✗ $name：$ref_var 未在 $ENV_FILE 中配置" >&2
    exit 1
  fi

  echo "── $name（$desc）"

  if [ -d "$dir/.git" ]; then
    echo "   拉取 $url"
    # 只抓分支/标签，不抓全部历史引用；--prune 清掉远端已删除的分支。
    git -C "$dir" remote set-url origin "$url"
    git -C "$dir" fetch --prune --tags --quiet origin
  elif [ -e "$dir" ]; then
    echo "✗ $dir 已存在但不是 git 仓库。请移走该目录后重试。" >&2
    exit 1
  else
    echo "   克隆 $url"
    git clone --quiet --no-checkout "$url" "$dir"
    git -C "$dir" fetch --prune --tags --quiet origin
  fi

  # 优先用远端分支，避免本地分支停留在旧提交；标签与提交 SHA 直接按原值解析。
  if git -C "$dir" rev-parse --verify --quiet "origin/$ref" >/dev/null; then
    target="origin/$ref"
  elif git -C "$dir" rev-parse --verify --quiet "$ref^{commit}" >/dev/null; then
    target="$ref"
  else
    echo "✗ $name：在远端找不到 ref「$ref」。若是提交 SHA，请确认它可从某个分支或标签到达。" >&2
    exit 1
  fi

  git -C "$dir" checkout --force --detach --quiet "$target"

  if [ "$DO_CLEAN" -eq 1 ]; then
    git -C "$dir" clean -ffdxq
  fi

  if [ -n "$(git -C "$dir" status --porcelain)" ]; then
    echo "⚠ $name：工作区仍有改动（已跳过 clean 时常见）；构建上下文将与提交内容不一致。" >&2
  fi

  sha="$(git -C "$dir" rev-parse HEAD)"
  summary+=("$(printf '%-28s %-40s %s' "$name" "$ref" "$sha")")
done

echo
echo "源码已就绪：$SOURCE_ROOT"
echo
printf '%s\n' "${summary[@]}"
echo
echo "下一步：docker compose --env-file $ENV_FILE -f docker-compose.prod.yml build"
