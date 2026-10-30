#!/usr/bin/env bash
# WMS v4 演示数据一键生成器（gen-demo-data.py 的薄封装）
#
#   ./tools/gen-demo.sh                 续写数据到现在（已是最新则不做任何事）
#   ./tools/gen-demo.sh --days 3        补最近 3 天
#   ./tools/gen-demo.sh --dry-run       只打印 SQL，不写库
#   ./tools/gen-demo.sh --start "2026-10-21 09:00:00" --end "2026-10-25 18:00:00"
#   ./tools/gen-demo.sh --no-verify     跳过内置校验（不推荐）
#
# 说明：无论从哪个目录调用，都会自动切到项目根目录，脚本内的相对路径
# （application.properties、seed.sql 等）始终有效。

set -euo pipefail

SELF="${BASH_SOURCE[0]}"
while [ -L "$SELF" ]; do
  LINK="$(readlink "$SELF")"
  case "$LINK" in
    /*) SELF="$LINK" ;;
    *)  SELF="$(cd "$(dirname "$SELF")" && pwd)/$LINK" ;;
  esac
done
TOOLS_DIR="$(cd "$(dirname "$SELF")" && pwd)"
ROOT_DIR="$(cd "$TOOLS_DIR/.." && pwd)"

cd "$ROOT_DIR"

PY="${PYTHON:-}"
if [ -z "$PY" ]; then
  for cand in python3 python; do
    if command -v "$cand" >/dev/null 2>&1; then PY="$cand"; break; fi
  done
fi
if [ -z "$PY" ]; then
  echo "未找到 python3，请先安装 Python 3.8+（或用 PYTHON=/path/to/python3 指定）" >&2
  exit 127
fi

exec "$PY" "$TOOLS_DIR/gen-demo-data.py" "$@"
