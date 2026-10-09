#!/usr/bin/env bash
# Runs Maestro flows against the installed debug app, with the test site served.
#
# Usage: maestro/run.sh [flow.yaml ...]    default: every flow in maestro/flows
set -euo pipefail
cd "$(dirname "$0")"

export MAESTRO_CLI_NO_ANALYTICS=1
export MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true
MAESTRO="${MAESTRO:-$HOME/.maestro/bin/maestro}"

python3 serve.py >/dev/null 2>&1 &
server=$!
trap 'kill $server' EXIT

# The emulator's own 127.0.0.1:8765 reaches the server too, as a second site.
adb reverse tcp:8765 tcp:8765 >/dev/null

mkdir -p build
if [ $# -eq 0 ]; then set -- flows; fi
"$MAESTRO" test --format=junit --output=build/report.xml "$@"
