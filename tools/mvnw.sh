#!/usr/bin/env bash
# =============================================================================
# 项目内置 Maven 包装器
#
# 作用：让整个项目自带构建工具，不依赖机器上是否装过 Maven。
#   - Maven 主程序：tools/apache-maven-3.9.11/
#   - 本地仓库    ：tools/maven-repo/       （依赖全部落在项目内，不污染 ~/.m2）
#
# 用法（在 backend/ 目录下）：
#   ../tools/mvnw.sh clean compile
#   ../tools/mvnw.sh spring-boot:run
#   ../tools/mvnw.sh clean package -DskipTests
#
# 用法（在项目根目录）：
#   tools/mvnw.sh -f backend/pom.xml clean compile
# =============================================================================
set -e

TOOLS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MVN_HOME="$TOOLS_DIR/apache-maven-3.9.11"
MVN_REPO="$TOOLS_DIR/maven-repo"

if [ ! -x "$MVN_HOME/bin/mvn" ]; then
  echo "[错误] 未找到内置 Maven：$MVN_HOME/bin/mvn" >&2
  echo "       请确认 tools/apache-maven-3.9.11 目录完整。" >&2
  exit 1
fi

# 自动定位 JDK：优先使用环境变量，其次用 macOS 的 java_home，最后退回 PATH 中的 java
if [ -z "$JAVA_HOME" ]; then
  if [ -x /usr/libexec/java_home ]; then
    JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
  fi
fi
if [ -n "$JAVA_HOME" ]; then
  export JAVA_HOME
fi

mkdir -p "$MVN_REPO"

exec "$MVN_HOME/bin/mvn" \
  -Dmaven.repo.local="$MVN_REPO" \
  -Dfile.encoding=UTF-8 \
  "$@"
