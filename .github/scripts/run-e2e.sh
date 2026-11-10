#!/usr/bin/env bash
set -u

mkdir -p e2e-artifacts/shots

REL_APK=$(ls release-apks/*x86_64*.apk release-apks/*universal*.apk 2>/dev/null | head -n 1 || true)
echo "=== Release APK Speedtest (R8 Minified, 5x Cold + 1x Warm) ===" > e2e-artifacts/speedtest.txt
if [ -n "$REL_APK" ]; then
  adb install -r "$REL_APK" >> e2e-artifacts/speedtest.txt 2>&1 || true
  REL_ACT="com.librecar.rust/com.librecar.rust.MainActivity"
  for i in 1 2 3 4 5; do
    echo "--- Release Cold Run $i ---" >> e2e-artifacts/speedtest.txt
    adb shell am start -W -S "$REL_ACT" >> e2e-artifacts/speedtest.txt 2>&1
    sleep 1
  done
  adb shell input keyevent KEYCODE_HOME
  sleep 1
  echo "--- Release Warm Start ---" >> e2e-artifacts/speedtest.txt
  adb shell am start -W "$REL_ACT" >> e2e-artifacts/speedtest.txt 2>&1
  adb uninstall com.librecar.rust || true
fi

echo "=== Installing Debug APK ==="
./gradlew installDebug --no-daemon --stacktrace || exit 1

PKG="com.librecar.rust.debug"
ACT="$PKG/com.librecar.rust.MainActivity"

echo "=== Debug Cold Start Speedtest (5x am start -W -S) ===" >> e2e-artifacts/speedtest.txt
for i in 1 2 3 4 5; do
  echo "--- Debug Run $i ---" >> e2e-artifacts/speedtest.txt
  adb shell am start -W -S "$ACT" >> e2e-artifacts/speedtest.txt 2>&1
  sleep 1
done

echo "=== Debug Warm Start Speedtest ===" >> e2e-artifacts/speedtest.txt
adb shell input keyevent KEYCODE_HOME
sleep 1
adb shell am start -W "$ACT" >> e2e-artifacts/speedtest.txt 2>&1

adb logcat -c
adb shell screenrecord --bit-rate 4500000 --time-limit 120 /sdcard/e2e_recording.mp4 &
REC_PID=$!

./gradlew connectedDebugAndroidTest --no-daemon --stacktrace
TEST_RC=$?

adb shell pkill -2 screenrecord || true
sleep 3

mkdir -p e2e-artifacts/shots
adb pull /sdcard/Download/librecar_shots/. e2e-artifacts/shots/ || true
adb pull /sdcard/e2e_recording.mp4 e2e-artifacts/e2e_recording.mp4 || true
adb logcat -d > e2e-artifacts/logcat.txt || true

grep -E "LIBRECAR_BENCH|LIBRECAR_FLAPPY_LIVE" e2e-artifacts/logcat.txt >> e2e-artifacts/speedtest.txt || true

echo "=== SPEEDTEST SUMMARY ==="
cat e2e-artifacts/speedtest.txt
echo "=== ARTIFACTS ==="
ls -lh e2e-artifacts e2e-artifacts/shots || true

exit $TEST_RC
