#!/usr/bin/env bash
# Runs only inside a disposable Android emulator. Does not touch a user's Termux.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PROOF="$ROOT/build/browser-editor"
mkdir -p "$PROOF"
exec > >(tee "$PROOF/driver.log") 2>&1
set -x
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
ADB="$ANDROID_HOME/platform-tools/adb"
# Set one explicit state directory for both Java SDK tools and the native emulator.
# The previous runner's emulator could not find the AVD created by avdmanager.
export ANDROID_USER_HOME="${RUNNER_TEMP}/alienflow-browser-android"
export ANDROID_EMULATOR_HOME="$ANDROID_USER_HOME"
export ANDROID_AVD_HOME="$ANDROID_USER_HOME/avd"
mkdir -p "$ANDROID_AVD_HOME"
# Retry setup only for interrupted SDK downloads, never a failing integration test.
SDK_READY=false
for attempt in 1 2 3; do
  if timeout 360 "$SDKMANAGER" 'emulator' 'system-images;android-36;google_apis;x86_64'; then
    SDK_READY=true; break
  fi
  echo "SDK installation attempt $attempt failed; the actual integration test has not run."
  sleep 5
done
test "$SDK_READY" = true
test -e /dev/kvm
sudo chmod 666 /dev/kvm
"$AVDMANAGER" create avd -n alienflow-browser-api36 -k 'system-images;android-36;google_apis;x86_64' --device pixel_6 --path "$ANDROID_AVD_HOME/alienflow-browser-api36.avd" --force
"$AVDMANAGER" list avd
"$ANDROID_HOME/emulator/emulator" -list-avds | tee "$PROOF/available-avds.txt"
grep -Fx 'alienflow-browser-api36' "$PROOF/available-avds.txt"
"$ANDROID_HOME/emulator/emulator" -avd alienflow-browser-api36 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect > "$PROOF/emulator.log" 2>&1 &
EMULATOR_PID=$!
cleanup() {
  # Preserve in-process geometry/session evidence even if an assertion fails.
  timeout 10 "$ADB" pull /sdcard/Android/data/io.github.issisx.alienflow/files/browser-proof "$PROOF/" > "$PROOF/pull-on-exit.txt" 2>&1 || true
  timeout 10 "$ADB" pull /sdcard/Android/data/io.github.issisx.alienflow/files/termux-proof "$PROOF/" > "$PROOF/termux-pull-on-exit.txt" 2>&1 || true
  timeout 5 "$ADB" logcat -d -t 3000 > "$PROOF/logcat.txt" 2>&1 || true
  timeout 5 "$ADB" exec-out screencap -p > "$PROOF/last-screen.png" 2>/dev/null || true
  timeout 5 "$ADB" shell dumpsys accessibility > "$PROOF/accessibility.txt" 2>&1 || true
  timeout 5 "$ADB" shell dumpsys input_method > "$PROOF/input-method.txt" 2>&1 || true
  kill "$EMULATOR_PID" 2>/dev/null || true
}
trap cleanup EXIT
timeout 120 "$ADB" wait-for-device
for _ in $(seq 1 120); do
  kill -0 "$EMULATOR_PID" || { cat "$PROOF/emulator.log"; exit 1; }
  if [ "$("$ADB" shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; then break; fi
  sleep 2
done
test "$("$ADB" shell getprop sys.boot_completed | tr -d '\r')" = 1
test "$("$ADB" shell getprop ro.kernel.qemu | tr -d '\r')" = 1
"$ADB" shell input keyevent 82
"$ADB" shell settings put global window_animation_scale 0
"$ADB" shell settings put global transition_animation_scale 0
"$ADB" shell settings put global animator_duration_scale 0
TERMUX_FILE='termux-app_v0.118.3+github-debug_x86_64.apk'
cd "$PROOF"
curl --fail --location --retry 2 'https://github.com/termux/termux-app/releases/download/v0.118.3/termux-app_v0.118.3%2Bgithub-debug_x86_64.apk' -o "$TERMUX_FILE"
curl --fail --location --retry 2 'https://github.com/termux/termux-app/releases/download/v0.118.3/termux-app_v0.118.3%2Bgithub-debug_sha256sums' -o upstream-sha256sums.txt
grep -F "$TERMUX_FILE" upstream-sha256sums.txt | sha256sum --check --strict
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs "$TERMUX_FILE" > termux-signature.txt
"$ADB" install -r "$TERMUX_FILE"
rm "$TERMUX_FILE"
cd "$ROOT"
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
"$ADB" shell pm grant io.github.issisx.alienflow android.permission.RECORD_AUDIO
"$ADB" shell pm grant io.github.issisx.alienflow android.permission.POST_NOTIFICATIONS
"$ADB" shell appops set io.github.issisx.alienflow ACCESS_RESTRICTED_SETTINGS allow
timeout 300 "$ADB" shell am instrument -w -r -e disableAnalytics true -e class io.github.issisx.alienflow.BrowserEditorTest io.github.issisx.alienflow.test/androidx.test.runner.AndroidJUnitRunner | tee "$PROOF/instrumentation.txt"
grep -F 'OK (1 test)' "$PROOF/instrumentation.txt"
"$ADB" pull /sdcard/Android/data/io.github.issisx.alienflow/files/browser-proof "$PROOF/"
python3 - "$PROOF/browser-proof/result.json" <<'PY'
import json,sys,os
r=json.load(open(sys.argv[1])); assert r['source']==os.environ['GITHUB_SHA'],r
print(json.dumps(r,indent=2))
PY


timeout 300 "$ADB" shell am instrument -w -r -e disableAnalytics true -e class io.github.issisx.alienflow.InstalledTermuxTest io.github.issisx.alienflow.test/androidx.test.runner.AndroidJUnitRunner | tee "$PROOF/termux-instrumentation.txt"
grep -F 'OK (1 test)' "$PROOF/termux-instrumentation.txt"
"$ADB" pull /sdcard/Android/data/io.github.issisx.alienflow/files/termux-proof "$PROOF/"
python3 - "$PROOF/termux-proof/result.json" <<'PY'
import json,sys,os
r=json.load(open(sys.argv[1])); assert r['source']==os.environ['GITHUB_SHA'] and r['final_count']==1 and r['real_terminal_echo'],r
print(json.dumps(r,indent=2))
PY
