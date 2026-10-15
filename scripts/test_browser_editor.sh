#!/usr/bin/env bash
# Runs only inside a disposable Android emulator. Does not touch a user's Termux.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PROOF="$ROOT/build/browser-editor"
mkdir -p "$PROOF"
exec > >(tee "$PROOF/driver.log") 2>&1
set -x
# API level and device profile are explicit so CI can cover Android 11 through 16 and several
# screen classes. Android 11-12 have no accessibility keyboard session: they run NoKeyboardSessionEditorTest.
API="${ALIENFLOW_EMULATOR_API:-36}"
DEVICE="${ALIENFLOW_EMULATOR_DEVICE:-pixel_6}"
AVD="alienflow-browser-api${API}"
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
  # Older system images carry an SDK license prompt; answer it without a SIGPIPE'd `yes |` under pipefail.
  if timeout 360 "$SDKMANAGER" 'emulator' "system-images;android-${API};google_apis;x86_64" < <(yes); then
    SDK_READY=true; break
  fi
  echo "SDK installation attempt $attempt failed; the actual integration test has not run."
  sleep 5
done
test "$SDK_READY" = true
test -e /dev/kvm
sudo chmod 666 /dev/kvm
"$AVDMANAGER" create avd -n "$AVD" -k "system-images;android-${API};google_apis;x86_64" --device "$DEVICE" --path "$ANDROID_AVD_HOME/$AVD.avd" --force
"$AVDMANAGER" list avd
"$ANDROID_HOME/emulator/emulator" -list-avds | tee "$PROOF/available-avds.txt"
grep -Fx "$AVD" "$PROOF/available-avds.txt"
"$ANDROID_HOME/emulator/emulator" -avd "$AVD" -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect > "$PROOF/emulator.log" 2>&1 &
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
if [ "$API" -lt 33 ]; then
  "$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
  "$ADB" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  "$ADB" shell pm grant io.github.issisx.alienflow android.permission.RECORD_AUDIO
  timeout 300 "$ADB" shell am instrument -w -r -e disableAnalytics true -e class io.github.issisx.alienflow.NoKeyboardSessionEditorTest io.github.issisx.alienflow.test/androidx.test.runner.AndroidJUnitRunner | tee "$PROOF/instrumentation.txt"
  grep -F 'OK (1 test)' "$PROOF/instrumentation.txt"
  python3 - "$PROOF" "$API" <<'PY'
import json,sys,os,pathlib
root=pathlib.Path(sys.argv[1])
results=[json.loads(x.split('fixture_result=',1)[1]) for x in open(root/'instrumentation.txt') if x.startswith('INSTRUMENTATION_STATUS: fixture_result=')]
assert len(results)==1,results
r=results[0]
assert r['source']==os.environ['GITHUB_SHA'] and r['api']==int(sys.argv[2]) and r['empty_field_live'] and r['existing_text_refused'],r
path=root/'no-keyboard-session-proof/result.json';path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(r,indent=2))
print(json.dumps(r,indent=2))
PY
  exit 0
fi
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
# One instrumentation process preserves the bound accessibility service between
# the browser and Termux classes. Separate invocations force-stop the target app.
timeout 300 "$ADB" shell am instrument -w -r -e disableAnalytics true -e class io.github.issisx.alienflow.BrowserEditorTest,io.github.issisx.alienflow.InstalledTermuxTest io.github.issisx.alienflow.test/androidx.test.runner.AndroidJUnitRunner | tee "$PROOF/instrumentation.txt"
grep -F 'OK (2 tests)' "$PROOF/instrumentation.txt"
python3 - "$PROOF" "$API" <<'PY'
import json,sys,os,pathlib
root=pathlib.Path(sys.argv[1])
results=[json.loads(x.split('fixture_result=',1)[1]) for x in open(root/'instrumentation.txt') if x.startswith('INSTRUMENTATION_STATUS: fixture_result=')]
assert len(results)==2,results
for kind in ('browser','termux'):
 r=next(x for x in results if ('initial_node' in x)==(kind=='browser'))
 assert r['source']==os.environ['GITHUB_SHA'] and r['api']==int(sys.argv[2]),r
 if kind=='browser':assert r['delivered'] and r['selected_replacement'] and r['typing_continues_dictation'] and r['repeat_session'],r
 else:assert r['final_count']==1 and r['real_terminal_echo'] and r['ordinary_live_after_termux'],r
 path=root/f'{kind}-proof/result.json';path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(r,indent=2))
 print(json.dumps(r,indent=2))
PY
# Optional screenshots never determine delivery success; results above come
# directly from assertions and real editor observations in instrumentation.
timeout 15 "$ADB" pull /sdcard/Android/data/io.github.issisx.alienflow/files/browser-proof "$PROOF/" || true
timeout 15 "$ADB" pull /sdcard/Android/data/io.github.issisx.alienflow/files/termux-proof "$PROOF/" || true
