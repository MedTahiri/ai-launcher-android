# Shared by build.sh and the tools: finds the JDK, the Android SDK and adb without changing anything.
# Source it; it sets JAVA_HOME and ANDROID_HOME when it can, and ADB always.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="io.github.medtahiri.launcher"
HOME_ACTIVITY="$PACKAGE/.core.HomeActivity"

# Prints the first folder that has a full JDK (a JRE has no javac, and Gradle can't build with it).
find_jdk() {
  local d
  for d in "${JAVA_HOME:-}" \
    "$(dirname "$(dirname "$(readlink -f "$(command -v javac 2>/dev/null || echo /nonexistent)")")")" \
    "$HOME/.local/share/JetBrains/Toolbox/apps/android-studio/jbr" \
    "$HOME/android-studio/jbr" \
    /opt/android-studio/jbr \
    /snap/android-studio/current/jbr \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
    /usr/lib/jvm/*; do
    [ -n "$d" ] && [ -x "$d/bin/javac" ] && { echo "$d"; return 0; }
  done
  return 1
}

find_sdk() {
  local d
  for d in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" \
    "${LOCALAPPDATA:-}/Android/Sdk"; do
    [ -n "$d" ] && [ -d "$d/platforms" ] && { echo "$d"; return 0; }
  done
  return 1
}

JDK_FOUND="$(find_jdk || true)"
SDK_FOUND="$(find_sdk || true)"
[ -n "$JDK_FOUND" ] && export JAVA_HOME="$JDK_FOUND"
[ -n "$SDK_FOUND" ] && export ANDROID_HOME="$SDK_FOUND"
if [ -n "$SDK_FOUND" ] && [ -x "$SDK_FOUND/platform-tools/adb" ]; then
  ADB="$SDK_FOUND/platform-tools/adb"
else
  ADB="$(command -v adb 2>/dev/null || true)"
fi

# What LauncherConfigFileTest found wrong with launcher.json, from its report (quiet Gradle hides test messages).
config_problems() {
  sed -n 's/^  - //p' "$ROOT"/app/build/test-results/testDebugUnitTest/TEST-*LauncherConfigFileTest.xml 2>/dev/null |
    sed -e 's/&quot;/"/g' -e "s/&apos;/'/g" -e 's/&lt;/</g' -e 's/&gt;/>/g' -e 's/&amp;/\&/g' | sort -u
}

# The unit tests that failed, from their reports.
test_failures() {
  grep -h -A1 '<testcase' "$ROOT"/app/build/test-results/testDebugUnitTest/TEST-*.xml 2>/dev/null |
    sed -nE 's/.*<failure message="([^"]{0,200}).*/\1/p' | sed -e 's/&quot;/"/g' -e 's/&#10;/ /g' -e 's/&lt;/</g' -e 's/&gt;/>/g' -e 's/&amp;/\&/g' | sort -u
}
