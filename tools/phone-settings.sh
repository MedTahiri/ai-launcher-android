#!/usr/bin/env bash
# Prints launcher.json as it is on the phone: with the changes the person made in the launcher's Customize screen. Copy
# it into launcher.json to keep those changes in the repo. Prints nothing and exits 1 if there are none. Debug builds
# only: tools/verify.sh installs one.
#
#   tools/phone-settings.sh [-s DEVICE]
set -uo pipefail
source "$(dirname "$0")/lib.sh"
target=()
while [ $# -gt 0 ]; do
  case "$1" in
    -s) target=(-s "${2:-}"); shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done
[ -n "$ADB" ] || { echo "adb not found. Run tools/doctor.sh for the fix." >&2; exit 1; }
"$ADB" "${target[@]}" shell am start -W -n "$HOME_ACTIVITY" >/dev/null || exit 1
if "$ADB" "${target[@]}" shell run-as "$PACKAGE" test -s files/launcher-effective.json; then
  "$ADB" "${target[@]}" exec-out run-as "$PACKAGE" cat files/launcher-effective.json
  echo
  exit 0
fi
echo "No changes were made on the phone." >&2
exit 1
