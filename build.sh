#!/usr/bin/env bash
# Checks launcher.json, runs the unit tests, then builds the debug APK. Prints the APK's path on success.
set -euo pipefail
source "$(dirname "$0")/tools/lib.sh"
cd "$ROOT"

[ -n "$JDK_FOUND" ] || { echo "No JDK found (a JRE isn't enough). Run tools/doctor.sh for the fix." >&2; exit 1; }
[ -n "$SDK_FOUND" ] || { echo "No Android SDK found. Run tools/doctor.sh for the fix." >&2; exit 1; }

log="$(mktemp)"
trap 'rm -f "$log"' EXIT
./gradlew -q :app:testDebugUnitTest 2>"$log" || {
  problems="$(config_problems)"
  failures="$(test_failures)"
  if [ -n "$problems" ]; then
    echo "launcher.json has a problem. Fix it and run ./build.sh again:" >&2
    echo "$problems" | sed 's/^/  - /' >&2
  elif [ -n "$failures" ]; then
    echo "Unit tests failed. Fix them and run ./build.sh again:" >&2
    echo "$failures" | sed 's/^/  - /' >&2
  else
    cat "$log" >&2 # most likely code that doesn't compile
  fi
  exit 1
}
./gradlew -q :app:assembleDebug
echo "$APK"
