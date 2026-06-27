#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSET_DIR="$ROOT/app/src/main/assets/runtime"
JNILIBS_DIR="$ROOT/app/src/main/jniLibs/arm64-v8a"
JRE_URL="https://github.com/QuestCraftPlusPlus/android-openjdk-build-multiarch/releases/latest/download/JRE-21.zip"
ZIP_NAME="jre21-aarch64.zip"

mkdir -p "$ASSET_DIR" "$JNILIBS_DIR"

if [[ ! -f "$ASSET_DIR/$ZIP_NAME" ]] || [[ $(stat -f%z "$ASSET_DIR/$ZIP_NAME" 2>/dev/null || stat -c%s "$ASSET_DIR/$ZIP_NAME") -lt 10485760 ]]; then
  echo "Downloading JRE 21 aarch64..."
  curl -L "$JRE_URL" -o "/tmp/JRE-21.zip"
  cp "/tmp/JRE-21.zip" "$ASSET_DIR/$ZIP_NAME"
fi

TMP_EXTRACT=$(mktemp -d)
unzip -q "$ASSET_DIR/$ZIP_NAME" -d "$TMP_EXTRACT"

JRE_ROOT=$(find "$TMP_EXTRACT" -maxdepth 3 -type d -name "lib" | head -1 | xargs dirname)
if [[ -z "$JRE_ROOT" || ! -d "$JRE_ROOT/lib" ]]; then
  echo "Could not locate JRE root in archive"
  exit 1
fi

for lib in libjvm.so libjli.so libjsig.so; do
  found=$(find "$JRE_ROOT" -name "$lib" | head -1 || true)
  if [[ -n "$found" ]]; then
    cp "$found" "$JNILIBS_DIR/$lib"
    echo "Copied $lib"
  fi
done

rm -rf "$TMP_EXTRACT"
echo "Runtime setup complete: $ASSET_DIR/$ZIP_NAME"

LOCX_URL="https://api.localxpose.io/api/v2/downloads/loclx-linux-arm64.zip"
LOCX_SO="$JNILIBS_DIR/libloclx.so"
if [[ ! -f "$LOCX_SO" ]] || [[ $(stat -f%z "$LOCX_SO" 2>/dev/null || stat -c%s "$LOCX_SO") -lt 1000000 ]]; then
  echo "Downloading LocalXpose CLI (libloclx.so)…"
  curl -L "$LOCX_URL" -o "/tmp/loclx.zip"
  unzip -p /tmp/loclx.zip loclx > "$LOCX_SO"
  chmod +x "$LOCX_SO"
  echo "Installed $LOCX_SO ($(stat -f%z "$LOCX_SO" 2>/dev/null || stat -c%s "$LOCX_SO") bytes)"
else
  echo "LocalXpose CLI already present: $LOCX_SO"
fi
