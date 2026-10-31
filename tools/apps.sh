#!/usr/bin/env bash
# Lists the apps on the device as label, package name and category, separated by tabs. The launcher writes this list to
# its private storage in debug builds. It must be installed: tools/verify.sh does that. The list is private to the owner.
#
#   tools/apps.sh [-s DEVICE]
set -uo pipefail
source "$(dirname "$0")/lib.sh"
target=()
[ "${1:-}" = "-s" ] && target=(-s "${2:-}")
[ -n "$ADB" ] || { echo "adb not found. Run tools/doctor.sh for the fix." >&2; exit 1; }
"$ADB" "${target[@]}" shell am start -W -n "$HOME_ACTIVITY" >/dev/null || exit 1
for _ in 1 2 3 4 5; do
  if "$ADB" "${target[@]}" shell run-as "$PACKAGE" test -s files/apps.tsv; then
    "$ADB" "${target[@]}" exec-out run-as "$PACKAGE" cat files/apps.tsv
    exit 0
  fi
  sleep 1
done
echo "No app list yet. Install a debug build with tools/verify.sh, then run this again." >&2
exit 1
