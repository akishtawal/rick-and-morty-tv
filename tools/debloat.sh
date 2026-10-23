#!/usr/bin/env bash
# Phase 1 debloat (R-1, R-2, D-003): pm disable-user only, never uninstall; every change logged.
#   debloat.sh --plan                 show tiers and current state
#   debloat.sh --apply A|B|<pkg>...   disable a tier or named packages (≤5 at a time), then check the TV
#   debloat.sh --rollback <pkg>|all   re-enable one package, or everything this script disabled
set -uo pipefail
export ANDROID_SERIAL="${ANDROID_SERIAL:?Set ANDROID_SERIAL=<tv-ip>:5555 first}"
LOG="$(dirname "$0")/debloat-log.txt"
TIER_A="tv.anoki.acr.controller com.google.android.backdrop com.google.android.leanbacklauncher.partnercustomizer com.google.android.tvrecommendations"
TIER_B="com.cvte.tv.CastHelp com.cvte.tv.cvte_help com.google.android.play.games"
NEVER="com.google.android.tvlauncher com.google.android.katniss com.google.android.apps.mediashell com.google.android.gms com.google.android.gsf com.android.vending com.aditya.cabletv"

state() { adb shell pm list packages -d | grep -qx "package:$1" && echo disabled || echo enabled; }

case "${1:-}" in
  --plan)
    for t in A B; do
      eval "list=\$TIER_$t"; echo "Tier $t:"
      for p in $list; do echo "  $p  [$(state "$p")]"; done
    done ;;
  --apply)
    shift
    case "${1:-}" in A) set -- $TIER_A ;; B) set -- $TIER_B ;; esac
    [ "$#" -gt 5 ] && { echo "Max 5 per batch."; exit 1; }
    for p in "$@"; do
      if [[ " $NEVER " == *" $p "* ]]; then echo "REFUSED (keep list): $p"; continue; fi
      out=$(adb shell pm disable-user --user 0 "$p" 2>&1 | tr -d '\r')
      echo "$(date '+%F %T') disable $p :: $out" | tee -a "$LOG"
    done
    echo "Now check: remote, HDMI/CEC, audio, Home button. Undo: $0 --rollback <pkg>" ;;
  --rollback)
    shift
    if [ "${1:-}" = all ]; then
      set -- $(grep " disable " "$LOG" 2>/dev/null | awk '{print $4}' | sort -u)
    fi
    for p in "$@"; do
      out=$(adb shell pm enable "$p" 2>&1 | tr -d '\r')
      echo "$(date '+%F %T') enable $p :: $out" | tee -a "$LOG"
    done ;;
  *) sed -n '2,5p' "$0" ;;
esac
