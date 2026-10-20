#!/usr/bin/env bash
set -euo pipefail
OUT=${1:-screenshots}
mkdir -p "$OUT"
adb shell am start -n com.shutixing.app/.MainActivity >/dev/null
sleep 2
adb exec-out screencap -p > "$OUT/01-home.png"
echo "saved $OUT/01-home.png"
