#!/usr/bin/env bash
# Sends launcher.json to the phone and applies it in a second or two, without building. For settings only: changes to
# the code, and to "name" (the label under the app icon), need tools/verify.sh. The next install drops what this sent.
# Prints what's wrong with the config if anything, and saves a screenshot to .verify/live.png. Debug builds only:
# tools/verify.sh installs one.
#
#   tools/push-config.sh [-s DEVICE]
set -uo pipefail
source "$(dirname "$0")/lib.sh"
cd "$ROOT"
target=()
while [ $# -gt 0 ]; do
  case "$1" in
    -s) target=(-s "${2:-}"); shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done
[ -n "$ADB" ] || { echo "adb not found. Run tools/doctor.sh for the fix." >&2; exit 1; }
adb_() { "$ADB" "${target[@]}" "$@"; }

python3 -c 'import json,sys; json.load(open(sys.argv[1]))' launcher.json 2>/dev/null ||
  { echo "FAIL: launcher.json isn't valid JSON" >&2; exit 1; }
[ "$(adb_ get-state 2>/dev/null)" = device ] ||
  { echo "FAIL: no device, or more than one: connect one, or choose with -s (see: adb devices)" >&2; exit 1; }
adb_ shell run-as "$PACKAGE" true 2>/dev/null ||
  { echo "FAIL: no debug build of the launcher on the device. Run tools/verify.sh once first." >&2; exit 1; }

adb_ exec-in run-as "$PACKAGE" sh -c 'cat > files/launcher-live.json' < launcher.json ||
  { echo "FAIL: couldn't send launcher.json" >&2; exit 1; }
adb_ shell run-as "$PACKAGE" rm -f files/config-status.txt
adb_ shell am force-stop "$PACKAGE"
adb_ shell am start -W -n "$HOME_ACTIVITY" >/dev/null || { echo "FAIL: the launcher didn't open" >&2; exit 1; }

status=""
for _ in 1 2 3 4 5 6 7 8 9 10; do
  if adb_ shell run-as "$PACKAGE" test -s files/config-status.txt 2>/dev/null; then
    status="$(adb_ exec-out run-as "$PACKAGE" cat files/config-status.txt)"
    break
  fi
  sleep 0.5
done
mkdir -p .verify
sleep 1
adb_ exec-out screencap -p > .verify/live.png 2>/dev/null
case "$status" in
  ok*) echo "ok    applied on the device. Look at .verify/live.png"; exit 0 ;;
  "") echo "FAIL: the launcher didn't report back. Check for a crash: adb logcat -b crash -d" >&2; exit 1 ;;
  *) echo "FAIL: the launcher rejected launcher.json, and shows its defaults:" >&2; echo "$status" >&2
     echo "A widget type or setting this build doesn't know needs tools/verify.sh, which builds the code too." >&2; exit 1 ;;
esac
