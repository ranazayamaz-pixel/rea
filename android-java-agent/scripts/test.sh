#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
TOOLS="$ROOT/.toolchain"
BUILD="$ROOT/build/tests"
if [ -x "$TOOLS/jre/bin/javac" ]; then
  JAVA="$TOOLS/jre/bin/java"; JAVAC="$TOOLS/jre/bin/javac"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
  JAVA="$JAVA_HOME/bin/java"; JAVAC="$JAVA_HOME/bin/javac"
else
  JAVA=java; JAVAC=javac
fi
mkdir -p "$BUILD"
"$JAVAC" -d "$BUILD" "$ROOT/src/main/java/com/javaagent/plugin/ExpressionEvaluator.java" \
  "$ROOT/tests/ExpressionEvaluatorTest.java"
"$JAVA" -cp "$BUILD" ExpressionEvaluatorTest
