#!/usr/bin/env bash
# Copies photos from a Mac folder to the TV for the Channel surf screensaver (D-024).
# Usage: tools/push_photos.sh ~/Pictures/TV
# Photos land in the launcher's own folder on the TV, so no storage permission is needed.
# Each is resized to fit 1920×1080 first (sips), so the TV never decodes a 12 MP original.
set -euo pipefail
SRC="${1:?Usage: push_photos.sh <folder>}"
export ANDROID_SERIAL="${ANDROID_SERIAL:?Set ANDROID_SERIAL=<tv-ip>:5555 first}"
DEST=/sdcard/Android/data/com.aditya.cabletv/files/photos
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

adb shell mkdir -p "$DEST"
n=0
find "$SRC" -maxdepth 1 -type f \( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o -iname '*.webp' -o -iname '*.heic' \) -print0 |
  while IFS= read -r -d '' f; do
    out="$TMP/$(basename "${f%.*}").jpg"
    sips -s format jpeg -Z 1920 "$f" --out "$out" >/dev/null
    adb push "$out" "$DEST/" >/dev/null
    n=$((n + 1)); echo "pushed $n: $(basename "$f")"
  done
echo "Photos on TV: $(adb shell ls "$DEST" | wc -l | tr -d ' ')"
