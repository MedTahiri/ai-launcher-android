#!/usr/bin/env bash
# Read-only: reports whether this computer can build and install the launcher, with the exact fix for each problem.
# Exits 0 when it can build; a missing device is a warning, since building doesn't need one.
source "$(dirname "$0")/lib.sh"
problems=0
ok() { echo "ok    $1"; }
bad() { echo "FIX   $1"; echo "      $2"; problems=$((problems + 1)); }
warn() { echo "WARN  $1"; echo "      $2"; }

case "$(uname -s)" in
  Darwin) jdk_fix="brew install --cask temurin@17 (or install Android Studio, which includes one)" ;;
  Linux) jdk_fix="Install OpenJDK 17 or newer with its compiler (Fedora: java-21-openjdk-devel; Debian/Ubuntu: openjdk-21-jdk), or install Android Studio" ;;
  *) jdk_fix="Install Android Studio, which includes one" ;;
esac

if [ -n "$JDK_FOUND" ]; then
  version="$("$JDK_FOUND/bin/javac" -version 2>&1 | awk '{print $2}')"
  if [ "${version%%.*}" -ge 17 ] 2>/dev/null; then
    ok "JDK $version at $JDK_FOUND"
  else
    bad "JDK $version is too old (17 or newer needed)" "$jdk_fix"
  fi
else
  bad "No JDK with a compiler found (a JRE isn't enough)" "$jdk_fix"
fi

if [ -n "$SDK_FOUND" ]; then
  ok "Android SDK at $SDK_FOUND"
  ls "$SDK_FOUND"/platforms/android-37*/android.jar >/dev/null 2>&1 && ok "Android platform 37" ||
    bad "Android platform 37 is missing" "sdkmanager \"platforms;android-37.0\" \"build-tools;37.0.0\""
else
  bad "No Android SDK found" "Install Android Studio and open it once, or install the command-line tools. Then set ANDROID_HOME if it's in an unusual place"
fi

if [ -n "$ADB" ]; then
  ok "adb at $ADB"
  devices="$("$ADB" devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1}')"
  if [ -n "$devices" ]; then
    # A phone on wireless debugging is listed twice, by address and by name: show each phone once.
    for d in $devices; do
      printf '%s %s\n' "$("$ADB" -s "$d" shell getprop ro.serialno </dev/null | tr -d '\r')" "$d"
    done | sort -u -k1,1 | while read -r _ d; do
      ok "Connected: $d ($("$ADB" -s "$d" shell getprop ro.product.model </dev/null | tr -d '\r'))"
    done
  else
    warn "No phone or emulator connected" "Phone: Settings > About phone > tap Build number 7 times (on some phones: Software information). Then in Developer options, either turn on USB debugging and plug it in, or use Wireless debugging > Pair device with pairing code, on the same Wi-Fi as this computer: adb mdns services shows the addresses, then adb pair ADDRESS CODE, then adb connect ADDRESS. Or start an emulator from Android Studio's Device Manager"
  fi
  "$ADB" devices 2>/dev/null | grep -q 'unauthorized' &&
    warn "A device is connected but not authorized" "Unlock the phone and accept \"Allow USB debugging?\""
else
  bad "adb not found" "sdkmanager \"platform-tools\", or install Android Studio"
fi

[ "$problems" -eq 0 ] && echo "Ready to build: ./build.sh" || { echo "$problems problem(s) to fix first."; exit 1; }
