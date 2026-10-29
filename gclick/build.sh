#!/usr/bin/env bash
# Builds the Gclick APK (com.ab.gclick) from source, with no Android SDK or Gradle.
#
#   src/ -> javac -> D8 -> classes.dex
#   proj/ (manifest, resources) + the modified Aurora APK as assets/astore.apk
#        -> apktool build -> sign with YOUR key -> out/Gclick_1.0.apk
#
# Build the modified Aurora first:  aurora-mod/build.sh
# Usage: gclick/build.sh [path/to/astore.apk]
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../aurora-mod/common.sh
. "$HERE/../aurora-mod/common.sh"

ASTORE_APK="${1:-$HERE/../aurora-mod/out/astore.apk}"
OUT="${OUT:-$HERE/out/Gclick_1.0.apk}"

[ -f "$ASTORE_APK" ] || die "modified Aurora APK not found: $ASTORE_APK (run aurora-mod/build.sh first)"

ensure_tools
ensure_android_jar
need javac
ensure_keystore gclick

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "==> compiling"
mkdir -p "$WORK/cls" "$WORK/dex"
javac -nowarn -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -d "$WORK/cls" \
  "$HERE"/src/com/ab/oneclick/*.java 2>&1 | grep -v '^Note\|warning' || true
d8 --release --min-api 26 --lib "$ANDROID_JAR" --output "$WORK/dex" $(find "$WORK/cls" -name '*.class')

echo "==> assembling the project"
cp -R "$HERE/proj" "$WORK/proj"
mkdir -p "$WORK/proj/assets"
cp "$ASTORE_APK" "$WORK/proj/assets/astore.apk"
cp "$WORK/dex/classes.dex" "$WORK/proj/classes.dex"
# Optional: put a Google Calculator APK at gclick/assets/calc.apk to use the hidden test page's V1 button.
[ -f "$HERE/assets/calc.apk" ] && cp "$HERE/assets/calc.apk" "$WORK/proj/assets/calc.apk"

echo "==> building"
apktool b -f "$WORK/proj" -o "$WORK/unsigned.apk" >/dev/null

echo "==> signing with your key"
sign_apk "$WORK/unsigned.apk" "$OUT"
echo "done: $OUT"
echo "Updates must be signed with the same key ($KS_FILE) - back it up."
