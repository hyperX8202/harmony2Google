#!/usr/bin/env bash
# Builds the modified Aurora Store used by Gclick from the OFFICIAL Aurora Store 4.8.4 (hw) APK.
#
#   official APK -> apktool decode -> apply patches/aurora-4.8.4-hw-gclick.patch
#                -> add assets/easyabroad.txt -> compile helper/ into classes3.dex
#                -> apktool build -> sign with YOUR key -> out/astore.apk
#
# Get the official APK first (certificate is verified there):  ./setup.sh download --variant hw
# Usage: aurora-mod/build.sh [official-apk]
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
. "$HERE/common.sh"

OFFICIAL_SHA256="9fe9c3c6a64998da7550de6cb1a4ef9c0f60d4142eaf33448c58d68e7b6e6863"
OFFICIAL_APK="${1:-$HOME/.aurora-setup/downloads/AuroraStore-hw-4.8.4.apk}"
OUT="${OUT:-$HERE/out/astore.apk}"

[ -f "$OFFICIAL_APK" ] || die "official APK not found: $OFFICIAL_APK (run ./setup.sh download --variant hw)"
[ "$(sha256 "$OFFICIAL_APK")" = "$OFFICIAL_SHA256" ] \
  || die "$OFFICIAL_APK is not the Aurora Store 4.8.4 (hw) release this patch was made for"

ensure_tools
ensure_android_jar
need javac
ensure_keystore astore

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "==> decoding the official APK"
apktool d -f -o "$WORK/dec" "$OFFICIAL_APK" >/dev/null

echo "==> applying the patch"
patch -s -p1 -d "$WORK/dec" <"$HERE/patches/aurora-4.8.4-hw-gclick.patch"
cp "$HERE/assets/easyabroad.txt" "$WORK/dec/assets/easyabroad.txt"

echo "==> compiling the helper classes (classes3.dex)"
mkdir -p "$WORK/cls" "$WORK/dex"
javac -nowarn -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -d "$WORK/cls" \
  "$HERE"/helper/com/ab/hook/*.java 2>&1 | grep -v '^Note\|warning' || true
d8 --release --min-api 23 --lib "$ANDROID_JAR" --output "$WORK/dex" $(find "$WORK/cls" -name '*.class')

echo "==> building"
apktool b -f "$WORK/dec" -o "$WORK/unsigned.apk" >/dev/null
cp "$WORK/dex/classes.dex" "$WORK/classes3.dex"
(cd "$WORK" && zip -q unsigned.apk classes3.dex)

echo "==> signing with your key"
sign_apk "$WORK/unsigned.apk" "$OUT"
echo "done: $OUT"
echo "Updates must be signed with the same key ($KS_FILE) - back it up."
