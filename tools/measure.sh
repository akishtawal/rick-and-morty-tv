#!/usr/bin/env bash
# M7: measure the PRD success metrics on the TV (warm Home, memory, frames, coverage).
# Never force-stops our package: that kills and switches off the Home-key accessibility service.
set -uo pipefail
export ANDROID_SERIAL="${ANDROID_SERIAL:?Set ANDROID_SERIAL=<tv-ip>:5555 first}"
PKG=com.aditya.cabletv
HOME_CMP=$PKG/.home.HomeActivity
PRIMARY="com.netflix.ninja com.google.android.youtube.tv com.amazon.amazonvideo.livingroom in.startv.hotstar com.mubi org.videolan.vlc tv.airtel.xstream.tvapp"

adb shell input keyevent KEYCODE_WAKEUP

echo "== Warm Home: from another app back to our home (am start -W TotalTime, ms)"
times=()
for i in 1 2 3 4 5; do
  adb shell monkey -p com.mubi -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1
  sleep 5
  t=$(adb shell am start -W -n "$HOME_CMP" | awk -F': ' '/TotalTime/{print $2}' | tr -d '\r')
  times+=("${t:-?}")
  sleep 3
done
echo "warm_home_ms: ${times[*]}"

sleep 8
echo "== Memory on the home screen"
adb shell dumpsys meminfo "$PKG" | awk '/TOTAL PSS:/{print "  "$0; exit}'

echo "== Frames while focus walks the row (portal spinning)"
adb shell dumpsys gfxinfo "$PKG" reset >/dev/null
for k in $(seq 7); do adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 0.4; done
for k in $(seq 7); do adb shell input keyevent KEYCODE_DPAD_LEFT; sleep 0.4; done
sleep 3
adb shell dumpsys gfxinfo "$PKG" | grep -E "Total frames rendered|Janky frames|50th|90th|95th|99th" | sed 's/^/  /'

echo "== Launch coverage: primary channels resolve to a TV launch activity"
ok=0
for p in $PRIMARY; do
  r=$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER "$p" | tail -1 | tr -d '\r')
  [[ "$r" == */* ]] && ok=$((ok + 1)) || echo "  MISSING: $p"
done
echo "  primary resolvable: $ok/7"
all=$(adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER | grep -c / )
echo "  launchable apps on TV (incl. this launcher): $all"
echo "== Home service: $(adb shell settings get secure enabled_accessibility_services)"
