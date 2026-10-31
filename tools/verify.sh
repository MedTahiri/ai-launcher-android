#!/usr/bin/env bash
# Builds the launcher, installs it on a device, opens it and checks that it really works. Saves screenshots for the
# agent or person to look at, and exits non-zero with the reason when something's wrong.
#
# On the device it installs over any existing copy, wakes the screen, asks to dismiss a lock screen that has no PIN, and
# opens and force-stops this launcher, pressing Back and tapping within it. It reads the system log and clears nothing.
#
#   tools/verify.sh [-s DEVICE] [--set-default] [--test-safe-mode]
#
#   -s DEVICE         which device, when more than one is connected (see: adb devices)
#   --set-default     also make it the default home screen. On a real phone, ask the owner first
#   --test-safe-mode  also crash it on purpose (debug builds only) and check that safe mode opens
set -uo pipefail
source "$(dirname "$0")/lib.sh"
cd "$ROOT"

serial="" set_default=0 test_safe=0
while [ $# -gt 0 ]; do
  case "$1" in
    -s) serial="${2:-}"; shift 2 ;;
    --set-default) set_default=1; shift ;;
    --test-safe-mode) test_safe=1; shift ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

fail() { echo "FAIL: $1" >&2; [ -n "${2:-}" ] && echo "$2" >&2; exit 1; }
[ -n "$ADB" ] || fail "adb not found" "Run tools/doctor.sh for the fix."

if [ -z "$serial" ]; then
  devices="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')"
  [ -n "$devices" ] || fail "no device connected" "Connect a phone or start an emulator. Run tools/doctor.sh for how."
  # A phone on wireless debugging can be listed twice, by address and by name: that's still one device.
  physical="$(for d in $devices; do "$ADB" -s "$d" shell getprop ro.serialno </dev/null | tr -d '\r'; done | sort -u | wc -l)"
  [ "$physical" -eq 1 ] || fail "more than one device: choose with -s" "$devices"
  serial="$(echo "$devices" | head -1)"
fi
adb_() { "$ADB" -s "$serial" "$@"; }
shell() { adb_ shell "$@" | tr -d '\r'; }

./build.sh >/dev/null || fail "the build failed: see the message above"

output="$(adb_ install -r "$APK" 2>&1)" || {
  case "$output" in
    *INSTALL_FAILED_UPDATE_INCOMPATIBLE*)
      fail "this phone has a copy signed with a different key" "Uninstalling it removes its settings, so ask the owner first: adb -s $serial uninstall $PACKAGE" ;;
    *) fail "the install failed" "$output" ;;
  esac
}

shell input keyevent KEYCODE_WAKEUP
shell wm dismiss-keyguard 2>/dev/null
if shell dumpsys window | grep -qE 'mDreamingLockscreen=true|isKeyguardShowing=true|mShowingLockscreen=true'; then
  fail "the phone is locked" "Unlock it and run this again."
fi

if [ "$set_default" -eq 1 ]; then
  shell cmd role add-role-holder --user 0 android.app.role.HOME "$PACKAGE" >/dev/null ||
    fail "couldn't set it as the home screen" "Set it by hand: Settings > Apps > Default apps > Home app."
fi

since=""
crash_report() { # any crash of this app since start_clean: the phone's own logs are left alone
  adb_ logcat -d -b crash -T "$since" 2>/dev/null | tr -d '\r' | grep -A 25 "Process: $PACKAGE" | head -40
}

start_clean() {
  shell am force-stop "$PACKAGE"
  since="$(shell "date '+%m-%d %H:%M:%S.000'")"
  shell am start -W -n "$HOME_ACTIVITY" "$@" >/dev/null
}

resumed() { shell dumpsys activity activities | grep -m 1 -E 'topResumedActivity|mResumedActivity|ResumedActivity:'; }

ui_dump() { # the screen as XML, with no file left on the device; retried, since a changing screen can refuse
  local i xml
  for i in 1 2 3 4 5; do
    xml="$(adb_ exec-out uiautomator dump /dev/tty 2>/dev/null)"
    case "$xml" in *"<hierarchy"*) echo "$xml"; return 0 ;; esac
    sleep 1
  done
  return 1
}

# Puts the center of the element with this test tag in $tap_x and $tap_y.
element_center() {
  local bounds
  bounds="$(echo "$2" | grep -oE "resource-id=\"$1\"[^>]*bounds=\"[^\"]*\"" | grep -oE 'bounds="[^"]*"' | head -1 | grep -oE '[0-9]+')"
  [ -n "$bounds" ] || return 1
  set -- $bounds
  tap_x=$((($1 + $3) / 2)) tap_y=$((($2 + $4) / 2))
}

mkdir -p .verify
rm -f .verify/home.png .verify/drawer.png .verify/safe-mode.png

# 1. The home screen opens and renders.
adb_ shell run-as "$PACKAGE" rm -f files/crashes.txt 2>/dev/null
start_clean
sleep 3
crashes="$(crash_report)"
[ -z "$crashes" ] || fail "the launcher crashed on start" "$crashes"
now="$(resumed)"
case "$now" in
  *SafeModeActivity*) fail "the launcher is in safe mode" "It crashed repeatedly. Fix the crash, then run this again." ;;
  *"$PACKAGE"*) ;;
  *) fail "the launcher isn't on screen" "On screen instead: $now" ;;
esac
xml="$(ui_dump)" || fail "couldn't read the screen" "Run this again; if it repeats, look at the screenshot in .verify/."
case "$xml" in
  *'launcher-home'*) ;;
  *) adb_ exec-out screencap -p > .verify/home.png; fail "the home screen didn't render" "See .verify/home.png" ;;
esac
adb_ exec-out screencap -p > .verify/home.png

# What launcher.json asks for must be there. A missing favorite is only a note: that app may not be on this phone.
notes=""
while read -r tag; do
  case "$xml" in
    *"\"$tag\""*) ;;
    *)
      case "$tag" in
        widget-*) fail "launcher.json asks for ${tag#widget-}, but it isn't on the home screen" "See .verify/home.png" ;;
        favorite-*) notes="$notes
note  favorite isn't on screen: it may not be installed here, or a rule in AppFilter.kt hides it: ${tag#favorite-}" ;;
      esac ;;
  esac
done < "$ROOT/app/build/launcher-expected.txt"

# 2. The drawer opens and renders.
element_center open-drawer "$xml" || fail "the All apps button is missing from the home screen" "Keep the \"open-drawer\" test tag on whatever opens the drawer."
shell input tap "$tap_x" "$tap_y"
sleep 2
drawer="$(ui_dump)" || fail "couldn't read the screen after opening the drawer"
case "$drawer" in
  *'app-drawer'*) ;;
  *) fail "the drawer didn't open" "$(crash_report)" ;;
esac
adb_ exec-out screencap -p > .verify/drawer.png
# Back until the drawer has closed: the first press may only hide the keyboard.
closed=0
for _ in 1 2 3; do
  shell input keyevent KEYCODE_BACK
  sleep 1
  after="$(ui_dump)" || continue
  case "$after" in
    *'app-drawer'*) ;;
    *) closed=1; break ;;
  esac
done
crashes="$(crash_report)"
[ -z "$crashes" ] || fail "the launcher crashed while using the drawer" "$crashes"
[ "$closed" -eq 1 ] || fail "Back didn't close the drawer"
case "$(resumed)" in
  *"$PACKAGE"*) ;;
  *) fail "Back left the launcher" "On screen instead: $(resumed)" ;;
esac

# 3. Optional: the way out of a crash loop.
if [ "$test_safe" -eq 1 ]; then
  for i in 1 2 3; do
    shell am force-stop "$PACKAGE"
    shell am start -n "$HOME_ACTIVITY" --ez crash_test true >/dev/null
    sleep 2
    shell input keyevent KEYCODE_BACK # dismisses the "keeps stopping" dialog, when there's one
  done
  shell am force-stop "$PACKAGE"
  shell am start -W -n "$HOME_ACTIVITY" >/dev/null
  sleep 3
  now="$(resumed)"
  adb_ exec-out screencap -p > .verify/safe-mode.png
  adb_ shell run-as "$PACKAGE" rm -f files/crashes.txt 2>/dev/null
  case "$now" in
    *SafeModeActivity*) ;;
    *) fail "safe mode didn't open after 3 crashes" "On screen instead: $now" ;;
  esac
  shell am force-stop "$PACKAGE"
  shell am start -W -n "$HOME_ACTIVITY" >/dev/null
  echo "ok    safe mode opens after 3 crashes"
fi

default="$(shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)"
echo "ok    installed, opened, no crashes on $serial"
echo "ok    home screen and drawer rendered, with every widget in launcher.json"
[ -z "$notes" ] || echo "${notes#?}"
case "$default" in
  "$PACKAGE"*) echo "ok    it's the default home screen" ;;
  *) echo "note  it isn't the default home screen yet: Settings > Apps > Default apps > Home app" ;;
esac
echo "PASS"
echo "Look at these before calling it done: $ROOT/.verify/home.png $ROOT/.verify/drawer.png"
