#!/usr/bin/env bash
# Runs only inside a disposable Android emulator. Does not touch a user's Termux.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
PROOF="$ROOT/build/installed-termux"
mkdir -p "$PROOF"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
ADB="$ANDROID_HOME/platform-tools/adb"
"$SDKMANAGER" 'emulator' 'system-images;android-36;google_apis;x86_64'
test -e /dev/kvm
sudo chmod 666 /dev/kvm
printf 'no\n' | "$AVDMANAGER" create avd -n alienflow-chat-api36 -k 'system-images;android-36;google_apis;x86_64' --force
"$ANDROID_HOME/emulator/emulator" -avd alienflow-chat-api36 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect > "$PROOF/emulator.log" 2>&1 &
EMULATOR_PID=$!
trap '"$ADB" logcat -d -t 2000 > "$PROOF/logcat.txt" 2>&1 || true; kill "$EMULATOR_PID" 2>/dev/null || true' EXIT
"$ADB" wait-for-device
for _ in $(seq 1 120); do
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
"$ADB" shell am instrument -w -r -e disableAnalytics true -e class io.github.issisx.alienflow.InstalledTermuxTest io.github.issisx.alienflow.test/androidx.test.runner.AndroidJUnitRunner | tee "$PROOF/instrumentation.txt"
grep -F 'OK (1 test)' "$PROOF/instrumentation.txt"
"$ADB" pull /sdcard/Android/data/io.github.issisx.alienflow/files/termux-proof "$PROOF/"
python3 - "$PROOF/termux-proof/result.json" <<'PY'
import json,sys,os
r=json.load(open(sys.argv[1])); assert r['source']==os.environ['GITHUB_SHA'] and r['final_count']==1 and r['real_terminal_echo'],r
print(json.dumps(r,indent=2))
PY
