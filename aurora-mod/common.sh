#!/usr/bin/env bash
# Shared helpers for aurora-mod/build.sh and gclick/build.sh (sourced, not executed).
set -euo pipefail

# Build tools live in ~/.aurora-setup/tools (same place as `./setup.sh tools`). Missing ones are
# downloaded here and checked against pinned SHA-256 hashes.
TOOLS="${TOOLS:-$HOME/.aurora-setup/tools}"
KEY_DIR="${KEY_DIR:-$HOME/.gclick-keys}"
ANDROID_JAR="${ANDROID_JAR:-$HOME/.gclick-keys/android.jar}"
# Public platform stub used only to compile against (never shipped).
ANDROID_JAR_URL="https://github.com/Sable/android-platforms/raw/master/android-33/android.jar"

die() { echo "error: $*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "missing tool: $1"; }

sha256() { if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }

# fetch_tool <file> <url> <sha256>
fetch_tool() {
  local file="$TOOLS/$1" url="$2" sum="$3"
  if [ -f "$file" ] && [ "$(sha256 "$file")" = "$sum" ]; then return 0; fi
  mkdir -p "$TOOLS"
  echo "==> downloading $1"
  curl -fsSL -o "$file.part" "$url"
  [ "$(sha256 "$file.part")" = "$sum" ] || { rm -f "$file.part"; die "SHA-256 mismatch for $1"; }
  mv "$file.part" "$file"
}

ensure_tools() {
  need java; need curl; need patch; need zip
  fetch_tool apktool.jar "https://github.com/iBotPeaches/Apktool/releases/download/v3.0.3/apktool_3.0.3.jar" \
    dbf930b076c6b9be08d57c449cacefc3bdd6b71ebd59b3066fc0e1f5b14f9423
  fetch_tool uber-apk-signer.jar "https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar" \
    e1299fd6fcf4da527dd53735b56127e8ea922a321128123b9c32d619bba1d835
  fetch_tool r8.jar "https://maven.google.com/com/android/tools/r8/8.2.42/r8-8.2.42.jar" \
    a1d7f902d56cfa8d1e8cd64aa250e63549359fcd096eb793286787b1a1e76db1
}

apktool() { java -jar "$TOOLS/apktool.jar" "$@"; }
d8() { java -cp "$TOOLS/r8.jar" com.android.tools.r8.D8 "$@"; }

ensure_android_jar() {
  [ -f "$ANDROID_JAR" ] && return 0
  mkdir -p "$(dirname "$ANDROID_JAR")"
  echo "==> downloading android.jar (compile-time stub) to $ANDROID_JAR"
  curl -fsSL -o "$ANDROID_JAR" "$ANDROID_JAR_URL"
}

# ensure_keystore <name>: sets KS_FILE, KS_ALIAS, KS_PASS. Creates a new key on first use.
# Override with KEYSTORE / KEYSTORE_ALIAS / KEYSTORE_PASS to use your own.
ensure_keystore() {
  local name="$1"
  if [ -n "${KEYSTORE:-}" ]; then
    KS_FILE="$KEYSTORE"; KS_ALIAS="${KEYSTORE_ALIAS:-$name}"; KS_PASS="${KEYSTORE_PASS:?set KEYSTORE_PASS}"
    return 0
  fi
  mkdir -p "$KEY_DIR"; chmod 700 "$KEY_DIR"
  KS_FILE="$KEY_DIR/$name.jks"; KS_ALIAS="$name"
  local passfile="$KEY_DIR/$name.pass"
  if [ ! -f "$KS_FILE" ]; then
    need keytool; need openssl
    echo "==> creating a new signing key: $KS_FILE (back it up: updates must use the same key)"
    (umask 077; openssl rand -hex 16 >"$passfile")
    keytool -genkeypair -keystore "$KS_FILE" -alias "$KS_ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
      -storepass "$(cat "$passfile")" -keypass "$(cat "$passfile")" -dname "CN=$name, O=TeamHX" >/dev/null 2>&1
  fi
  KS_PASS="$(cat "$passfile")"
}

# sign_apk <in.apk> <out.apk>
sign_apk() {
  local in="$1" out="$2" tmp
  tmp="$(mktemp -d)"
  java -jar "$TOOLS/uber-apk-signer.jar" -a "$in" --ks "$KS_FILE" --ksAlias "$KS_ALIAS" \
    --ksPass "$KS_PASS" --ksKeyPass "$KS_PASS" --out "$tmp" >/dev/null
  mkdir -p "$(dirname "$out")"
  mv "$tmp"/*-aligned-signed.apk "$out"
}
