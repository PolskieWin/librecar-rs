#!/usr/bin/env bash
set -u

mkdir -p e2e-artifacts

echo "=== Installing Debug APK ==="
./gradlew installDebug --no-daemon --stacktrace || exit 1

PKG="com.librecar.rust.debug"
ACT="$PKG/com.librecar.rust.MainActivity"

echo "=== Cold Start Speedtest (5x am start -W -S) ===" > e2e-artifacts/speedtest.txt
for i in 1 2 3 4 5; do
  echo "--- Run $i ---" >> e2e-artifacts/speedtest.txt
  adb shell am start -W -S "$ACT" >> e2e-artifacts/speedtest.txt 2>&1
  sleep 1
done

echo "=== Warm Start Speedtest ===" >> e2e-artifacts/speedtest.txt
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

adb pull /sdcard/librecar_shots e2e-artifacts/shots || true
adb pull /sdcard/e2e_recording.mp4 e2e-artifacts/e2e_recording.mp4 || true
adb logcat -d > e2e-artifacts/logcat.txt || true

grep -E "LIBRECAR_BENCH|LIBRECAR_FLAPPY_LIVE" e2e-artifacts/logcat.txt >> e2e-artifacts/speedtest.txt || true

echo "=== SPEEDTEST SUMMARY ==="
cat e2e-artifacts/speedtest.txt
echo "=== ARTIFACTS ==="
ls -lh e2e-artifacts e2e-artifacts/shots || true

exit $TEST_RC
