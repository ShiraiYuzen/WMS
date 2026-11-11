#!/bin/bash
# WMS 演示数据一键生成（macOS）
# 编码：UTF-8，无 BOM。macOS 终端默认按 UTF-8 读取；加上 BOM 会让 shebang 失效。

set -u

# 保证中文按 UTF-8 解释。zh_CN.UTF-8 未安装时退回已有的 UTF-8 区域。
if locale -a 2>/dev/null | grep -qiE '^zh_CN\.(UTF-8|utf8)$'; then
  export LANG="zh_CN.UTF-8"
  export LC_ALL="zh_CN.UTF-8"
else
  case "${LANG:-}" in
    *.UTF-8|*.utf8) ;;
    *) export LANG="en_US.UTF-8" ;;
  esac
  export LC_ALL="${LANG}"
fi
export PYTHONIOENCODING="utf-8"
export PYTHONUTF8="1"

SELF="${BASH_SOURCE[0]}"
while [ -L "$SELF" ]; do
  LINK="$(readlink "$SELF")"
  case "$LINK" in
    /*) SELF="$LINK" ;;
    *) SELF="$(cd "$(dirname "$SELF")" && pwd)/$LINK" ;;
  esac
done
TOOLS_DIR="$(cd "$(dirname "$SELF")" && pwd)"
ROOT_DIR="$(cd "$TOOLS_DIR/.." && pwd)"
cd "$ROOT_DIR" || exit 1

# 在终端里等待任意键；没有终端时只打印提示，避免脚本一直卡住。
press_any_key() {
  printf '%s' "$1"
  if [ -t 0 ]; then
    IFS= read -r -n 1 -s _
  fi
  printf '\n'
}

printf '%s\n' "========================================"
printf '%s\n' "  WMS 演示数据一键生成"
printf '%s\n' "========================================"
printf '%s\n' "将从库里最后一张订单续写到现在。数据已是最新时不会改动。"
printf '\n'
press_any_key "请按任意键开始..."

PY="${PYTHON:-}"
if [ -z "$PY" ]; then
  for cand in python3 python; do
    if command -v "$cand" >/dev/null 2>&1; then
      PY="$cand"
      break
    fi
  done
fi

ec=0
if [ -z "$PY" ]; then
  printf '%s\n' "未找到 python3。请先安装 Python 3.8 或更高版本，或设置 PYTHON 为解释器路径。" >&2
  ec=127
else
  "$PY" "$TOOLS_DIR/gen-demo-data.py" "$@"
  ec=$?
fi

printf '\n'
if [ "$ec" -eq 0 ]; then
  printf '%s\n' "生成结束。"
else
  printf '%s\n' "生成失败，退出码：${ec}"
fi
press_any_key "请按任意键继续..."
exit "$ec"
