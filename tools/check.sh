#!/usr/bin/env bash
# Everything that can be checked without a device, for contributors and CI: unit tests, the build, the permissions
# the app asks for, and the recipes.
set -uo pipefail
source "$(dirname "$0")/lib.sh"
cd "$ROOT"
failed=0
pass() { echo "ok    $1"; }
problem() { echo "FAIL  $1"; failed=1; }

check_permissions() {
  local aapt2 actual allowed extra
  aapt2="$(ls -d "$ANDROID_HOME"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)"
  [ -n "$aapt2" ] || { problem "aapt2 not found in the Android SDK's build-tools"; return; }
  actual="$("$aapt2" dump permissions "$APK" | sed -nE "s/^uses-permission: name='([^']+)'.*/\1/p" | sort -u)"
  allowed="$(grep -vE '^[[:space:]]*(#|$)' permissions.allow | sort -u)"
  extra="$(comm -23 <(echo "$actual") <(echo "$allowed"))"
  if [ -n "$extra" ]; then
    problem "permissions not in permissions.allow: $(echo "$extra" | tr '\n' ' ')(adding one is the user's decision: see AGENTS.md)"
  else
    pass "permissions match permissions.allow"
  fi
}

# Recipes are instructions an agent reads, so they stay design goals: no commands, no links, no surprise permissions.
# This catches the obvious; review (CODEOWNERS) is what really protects people.
check_recipes() {
  local f declared used p ok=1
  for f in recipes/*.md; do
    [ -e "$f" ] || continue
    [ "$(basename "$f")" = README.md ] && continue
    if [ "$(head -1 "$f")" != "---" ] || ! declared="$(awk 'NR>1 && /^---$/ {exit} /^permissions:/ {print}' "$f")" || [ -z "$declared" ]; then
      problem "$f: must start with front matter that has a permissions: line"; ok=0; continue
    fi
    if grep -nE '^[[:space:]]*(```|~~~)' "$f" | grep -vE '(```|~~~)(json)?[[:space:]]*$' | grep -q .; then
      problem "$f: only json code blocks are allowed"; ok=0
    fi
    if grep -qE '^[[:space:]]*(\$ |sudo |curl |wget |adb |bash |sh |pip |npm |chmod )' "$f" || grep -qE '`(sudo|curl|wget|adb|bash|sh|pip|npm|chmod) [^`]*`' "$f"; then
      problem "$f: recipes can't contain commands"; ok=0
    fi
    if grep -qiE '(https?|ftp)://|www\.' "$f"; then
      problem "$f: recipes can't contain links"; ok=0
    fi
    used="$(grep -oE 'android\.permission\.[A-Z_]+' "$f" | sort -u)"
    for p in $used; do
      case "$declared" in
        *"$p"*) ;;
        *) problem "$f: mentions $p without declaring it under permissions:"; ok=0 ;;
      esac
    done
  done
  [ "$ok" -eq 1 ] && pass "recipes follow the rules"
}

[ -n "$JDK_FOUND" ] && [ -n "$SDK_FOUND" ] || { echo "Missing tools: run tools/doctor.sh"; exit 1; }

if ./gradlew -q :app:testDebugUnitTest 2>/dev/null; then
  pass "unit tests and launcher.json"
else
  problem "unit tests or launcher.json. Details: app/build/reports/tests/testDebugUnitTest/index.html"
  config_problems | sed 's/^/        /'
fi
if ./gradlew -q :app:assembleDebug; then
  pass "the app builds"
  check_permissions
else
  problem "the build: see above"
fi
check_recipes

[ "$failed" -eq 0 ] && echo "All checks passed." || { echo "Some checks failed."; exit 1; }
