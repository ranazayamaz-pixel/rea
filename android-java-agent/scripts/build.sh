#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
TOOLS="$ROOT/.toolchain"
BUILD="$ROOT/build"
OUT="$ROOT/java-agent-debug.apk"

if [ -x "$TOOLS/jre/bin/javac" ]; then
  JAVA="$TOOLS/jre/bin/java"; JAVAC="$TOOLS/jre/bin/javac"; JAR="$TOOLS/jre/bin/jar"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
  JAVA="$JAVA_HOME/bin/java"; JAVAC="$JAVA_HOME/bin/javac"; JAR="$JAVA_HOME/bin/jar"
elif command -v javac >/dev/null 2>&1; then
  JAVA=java; JAVAC=javac; JAR=jar
else
  echo "Java not found. Run scripts/setup-toolchain.sh first." >&2; exit 1
fi
AAPT2=${AAPT2:-$TOOLS/aapt2}
ANDROID_JAR=${ANDROID_JAR:-$TOOLS/android.jar}
D8_JAR=${D8_JAR:-$TOOLS/d8.jar}
APKSIGNER_JAR=${APKSIGNER_JAR:-$TOOLS/apksigner.jar}
DEBUG_KEYSTORE=${DEBUG_KEYSTORE:-$TOOLS/debug.keystore}
for required in "$AAPT2" "$ANDROID_JAR" "$D8_JAR" "$APKSIGNER_JAR" "$DEBUG_KEYSTORE"; do
  if [ ! -f "$required" ]; then echo "Missing $required. Run scripts/setup-toolchain.sh first." >&2; exit 1; fi
done

rm -rf "$BUILD"
mkdir -p "$BUILD/generated" "$BUILD/classes" "$BUILD/dex"
"$AAPT2" compile --dir "$ROOT/src/main/res" -o "$BUILD/resources.zip"
"$AAPT2" link -o "$BUILD/unsigned.apk" -I "$ANDROID_JAR" \
  --manifest "$ROOT/src/main/AndroidManifest.xml" --java "$BUILD/generated" \
  --min-sdk-version 23 --target-sdk-version 35 --version-code 1 --version-name 1.0.0 \
  "$BUILD/resources.zip"
find "$ROOT/src/main/java" "$BUILD/generated" -name '*.java' | sort > "$BUILD/sources.txt"
"$JAVAC" -source 8 -target 8 -encoding UTF-8 -classpath "$ANDROID_JAR" \
  -d "$BUILD/classes" @"$BUILD/sources.txt"
"$JAR" cf "$BUILD/classes.jar" -C "$BUILD/classes" .
"$JAVA" -cp "$D8_JAR" com.android.tools.r8.D8 --release --min-api 23 \
  --lib "$ANDROID_JAR" --output "$BUILD/dex" "$BUILD/classes.jar"
cp "$BUILD/unsigned.apk" "$BUILD/with-dex.apk"
python3 - "$BUILD/with-dex.apk" "$BUILD/dex/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1:]
with zipfile.ZipFile(apk, "a", compression=zipfile.ZIP_STORED) as output:
    output.write(dex, "classes.dex")
PY
# Re-pack before signing so every uncompressed Android payload is 4-byte aligned.
python3 - "$BUILD/with-dex.apk" "$BUILD/packaged.apk" <<'PY'
import struct, sys, zipfile
source, destination = sys.argv[1:]
with zipfile.ZipFile(source, "r") as original, zipfile.ZipFile(destination, "w") as aligned:
    for entry in original.infolist():
        data = original.read(entry.filename)
        info = zipfile.ZipInfo(entry.filename, entry.date_time)
        info.compress_type = entry.compress_type
        info.comment = entry.comment
        info.external_attr = entry.external_attr
        info.internal_attr = entry.internal_attr
        info.create_system = entry.create_system
        if entry.compress_type == zipfile.ZIP_STORED:
            name_length = len(entry.filename.encode("utf-8"))
            needed = (-(aligned.fp.tell() + 30 + name_length)) % 4
            if needed:
                # ZIP extra fields have a four-byte header; preserve the requested modulo.
                length = needed + 4
                info.extra = struct.pack("<HH", 0xFFFF, length - 4) + bytes(length - 4)
        aligned.writestr(info, data)
PY
rm -f "$OUT" "$OUT.idsig"
"$JAVA" -cp "$APKSIGNER_JAR" com.android.apksigner.ApkSignerTool sign \
  --ks "$DEBUG_KEYSTORE" --ks-key-alias androiddebugkey --v4-signing-enabled false \
  --ks-pass pass:android --key-pass pass:android --out "$OUT" "$BUILD/packaged.apk"
"$JAVA" -cp "$APKSIGNER_JAR" com.android.apksigner.ApkSignerTool verify --verbose "$OUT"
"$AAPT2" dump badging "$OUT" | head -12
printf '\nBuilt: %s (%s bytes)\n' "$OUT" "$(wc -c < "$OUT")"
printf 'Debug-signed only; use your own release key for distribution.\n'
