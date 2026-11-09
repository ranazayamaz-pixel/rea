#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
TOOLS="$ROOT/.toolchain"
mkdir -p "$TOOLS/downloads"

if ! command -v npm >/dev/null 2>&1; then
  echo "npm is required to fetch the pinned, Gradle-free Android toolchain" >&2
  exit 1
fi
if [ "$(uname -s)" != Linux ] || [ "$(uname -m)" != x86_64 ]; then
  echo "Automatic setup supports Linux x86_64. Set JAVA_HOME, AAPT2, ANDROID_JAR, D8_JAR and APKSIGNER_JAR on other platforms." >&2
  exit 1
fi

cd "$TOOLS/downloads"
if [ ! -f minapk-0.5.1.tgz ]; then npm pack @drxiaozhi/minapk@0.5.1 --silent; mv drxiaozhi-minapk-0.5.1.tgz minapk-0.5.1.tgz; fi
if [ ! -f aaptjs3-2.0.2.tgz ]; then npm pack aaptjs3@2.0.2 --silent; fi

if [ ! -f "$TOOLS/android.jar" ]; then
  mkdir -p "$TOOLS/minapk"
  tar -xzf minapk-0.5.1.tgz -C "$TOOLS/minapk" --strip-components=1 \
    package/tools/android.jar package/tools/d8.jar package/tools/apksigner.jar package/tools/debug.keystore
  cp "$TOOLS/minapk/tools/android.jar" "$TOOLS/android.jar"
  cp "$TOOLS/minapk/tools/d8.jar" "$TOOLS/d8.jar"
  cp "$TOOLS/minapk/tools/apksigner.jar" "$TOOLS/apksigner.jar"
  cp "$TOOLS/minapk/tools/debug.keystore" "$TOOLS/debug.keystore"
fi
if [ ! -x "$TOOLS/aapt2" ]; then
  tar -xzf aaptjs3-2.0.2.tgz -C "$TOOLS" --strip-components=4 package/bin/x64/linux/aapt2
  chmod +x "$TOOLS/aapt2"
fi
if ! command -v javac >/dev/null 2>&1 && [ ! -x "$TOOLS/jre/bin/javac" ]; then
  if [ ! -f javajre-linux-64-17.0.8.tgz ]; then npm pack javajre-linux-64@17.0.8 --silent; fi
  mkdir -p "$TOOLS/jre"
  tar -xzf javajre-linux-64-17.0.8.tgz -C "$TOOLS/jre" --strip-components=2
fi

echo "Toolchain ready in $TOOLS"
