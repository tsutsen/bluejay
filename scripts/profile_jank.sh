#!/bin/bash
# Frame-jank profiler for bluejay on a connected device.
# Usage: ./scripts/profile_jank.sh [scenario]   scenario: morph | feed | player
set -u
PKG="com.tsutsen.platformplayer.d"
DEV="${ADB_DEVICE:-192.168.1.159:39473}"
SCENARIO="${1:-morph}"
ADB="adb -s $DEV"

$ADB shell am force-stop "$PKG" >/dev/null 2>&1
sleep 1
$ADB shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 8
$ADB shell input keyevent KEYCODE_HOME >/dev/null 2>&1
$ADB shell am start -n "$($ADB shell cmd package resolve-activity --brief "$PKG" | tail -1 | tr -d '\r')" >/dev/null 2>&1
sleep 3
$ADB shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1   # start counting AFTER app is foreground
sleep 1

case "$SCENARIO" in
  feed)
    for i in 1 2 3; do
      $ADB shell input swipe 630 2000 630 600 300; sleep 0.4
      $ADB shell input swipe 630 600 630 2000 300; sleep 0.4
    done
    ;;
  player|morph)
    $ADB shell input tap 630 900; sleep 6
    if [ "$SCENARIO" = "morph" ]; then
      $ADB shell input swipe 630 1400 630 2500 400; sleep 1.5
      $ADB shell input swipe 630 2500 630 1400 400; sleep 1.5
    fi
    ;;
esac

$ADB shell dumpsys gfxinfo "$PKG" framestats > /tmp/framestats.txt 2>/dev/null
python3 /home/leon/Projects/bluejay/scripts/parse_framestats.py "$SCENARIO" /tmp/framestats.txt
