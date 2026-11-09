#!/usr/bin/env bash
# Lists the apps on the device as label, package name and category, separated by tabs. The launcher writes this list to
# its private storage in debug builds. It must be installed: tools/verify.sh does that. The list is private to the owner.
# With --widgets, it lists the home screen widgets that apps offer instead: app, widget name, provider (for the
# android_widget "provider" option) and smallest size. With --icon-packs, it lists installed icon packs, for theme.iconPack.
#
#   tools/apps.sh [-s DEVICE] [--widgets | --icon-packs]
set -uo pipefail
source "$(dirname "$0")/lib.sh"
target=() file=apps.tsv check=-s
while [ $# -gt 0 ]; do
  case "$1" in
    -s) target=(-s "${2:-}"); shift 2 ;;
    --widgets) file=widgets.tsv check=-e; shift ;; # a phone can have none of these
    --icon-packs) file=icon-packs.tsv check=-e; shift ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done
[ -n "$ADB" ] || { echo "adb not found. Run tools/doctor.sh for the fix." >&2; exit 1; }
"$ADB" "${target[@]}" shell am start -W -n "$HOME_ACTIVITY" >/dev/null || exit 1
for _ in 1 2 3 4 5; do
  if "$ADB" "${target[@]}" shell run-as "$PACKAGE" test $check files/$file; then
    "$ADB" "${target[@]}" exec-out run-as "$PACKAGE" cat files/$file
    exit 0
  fi
  sleep 1
done
echo "No app list yet. Install a debug build with tools/verify.sh, then run this again." >&2
exit 1
