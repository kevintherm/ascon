#!/usr/bin/env bash
# Refreshes the filter lists shipped in the app. Run before a release; installed apps
# also download newer copies weekly.
set -euo pipefail
cd "$(dirname "$0")/../engine/adblock/src/main/assets/adblock"
for list in easylist easyprivacy; do
  curl -sSfL -o "$list.txt" "https://easylist.to/easylist/$list.txt"
  echo "$list: $(sed -n 's/^! Version: //p' "$list.txt" | head -1)"
done
