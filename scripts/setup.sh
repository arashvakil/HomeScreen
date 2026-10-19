#!/usr/bin/env bash
# Install the launcher and official Home on Fire companion on one authorized TV.
set -euo pipefail
if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <fire-tv-ip:5555> <signed-homescreen.apk>" >&2
  exit 1
fi
DEVICE="$1"
APK="$2"
[[ -f "$APK" ]] || { echo "APK not found: $APK" >&2; exit 1; }
ADB="${ADB:-adb}"
command -v "$ADB" >/dev/null
command -v curl >/dev/null
command -v python3 >/dev/null
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT
curl --fail --location --proto '=https' --proto-redir '=https' \
  'https://github.com/toolicious/home-on-fire/releases/download/v1.5.2/home-on-fire.apk' \
  --output "$TEMP_DIR/home-on-fire.apk"
python3 - "$TEMP_DIR/home-on-fire.apk" <<'PY'
import hashlib, pathlib, sys
actual = hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest()
if actual != '87403c961264cc2e36e53cf571777ec20705eb87f46794cb0ac667135a98d9fe':
    sys.exit('Home on Fire checksum mismatch; nothing installed')
PY
"$ADB" connect "$DEVICE"
"$ADB" -s "$DEVICE" get-state
"$ADB" -s "$DEVICE" install -r "$APK"
"$ADB" -s "$DEVICE" install -r "$TEMP_DIR/home-on-fire.apk"
"$ADB" -s "$DEVICE" shell pm grant io.github.toolicious.homeonfire android.permission.WRITE_SECURE_SETTINGS
"$ADB" -s "$DEVICE" shell am start -n io.github.toolicious.homeonfire/.MainActivity
echo 'On the TV: enable Home on Fire accessibility, choose Home as target, enable Replace Home and Launch on boot.'
echo 'Disable other launcher accessibility redirectors first. Hold Home to escape to Amazon home.'
echo 'For updates, allow Home in Developer options → Install unknown apps when prompted.'
