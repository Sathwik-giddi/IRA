#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

APP_ID="com.voicepoco.app"
APK="app/build/outputs/apk/debug/app-debug.apk"
MODEL_SMALL="models/functiongemma-270m-it-Q4_K_M.gguf"
MODEL_BIG="models/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"

SKIP_BUILD=0
MODEL_MODE="auto"

usage() {
  cat <<'EOF'
Usage: ./scripts/agent-install.sh [options]

Build, install, and launch the IRA debug app on one connected Android device.

Options:
  --skip-build     Reuse app/build/outputs/apk/debug/app-debug.apk
  --models MODE    Push models: auto (default), small, big, both, or none
  --skip-models    Alias for --models none
  -h, --help       Show this help

Auto pushes the 270M model to every ARM phone and adds the 1.5B model only on
64-bit devices with 5.5GB+ RAM. Use --models both to override the RAM check.

The script is non-interactive. Legal consent and Android permission screens
remain human-only steps. See AGENTS.md for the full runbook.
EOF
}

info() {
  printf '==> %s\n' "$*"
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

need() {
  command -v "$1" >/dev/null 2>&1 || die "Missing $1. Install it and retry."
}

print_samsung_install_block() {
  cat >&2 <<'EOF'
Samsung blocked the install.

1. Settings > Security and privacy > Auto Blocker. Turn it off temporarily.
2. If Maximum restrictions is on, turn it off for the install.
3. Settings > Security and privacy > More security settings > Install unknown apps. Allow the source you use.
4. Retry this script. Turn Auto Blocker back on after setup.

Do not disable Knox, change the signing key, or use a security bypass.
EOF
}

print_xiaomi_install_block() {
  cat >&2 <<'EOF'
Xiaomi, Redmi, or Poco blocked the install.

1. Settings > About phone > tap OS or MIUI version seven times.
2. Settings > Additional settings > Developer options.
3. Enable USB debugging and Install via USB.
4. If Install via USB is unavailable, sign in to a Mi account and insert a SIM, then retry.
5. Settings > Privacy protection > Special permissions > Install unknown apps. Allow the source.

Then rerun this script. Do not use a bootloader unlock or a security bypass.
EOF
}

print_generic_install_block() {
  cat >&2 <<EOF
$OEM_LABEL blocked the install.

Enable Install unknown apps for the source you use, then retry.
On Android 13 and newer, open App info for the installer and allow restricted settings only if you trust the APK.
Do not change the signing key or use a security bypass.
EOF
}

print_oem_steps() {
  case "$OEM" in
    Samsung)
      cat <<'EOF'
Samsung setup:
- Accessibility: Settings > Accessibility > Installed apps > IRA Assistant.
- Notification access: Settings > Notifications > Advanced settings > Notification access > IRA.
- Overlay: Settings > Apps > three dots > Special access > Appear on top > IRA.
- Battery: Settings > Battery > Background usage limits > Never sleeping apps > add IRA.
- Also set Settings > Apps > IRA > Battery > Unrestricted.
- Turn Auto Blocker back on after setup if you disabled it for the install.
EOF
      ;;
    Xiaomi)
      cat <<'EOF'
Xiaomi, Redmi, Poco setup:
- Accessibility: Settings > Additional settings > Accessibility > Downloaded apps > IRA Assistant.
- Overlay: Settings > Apps > Manage apps > IRA > Other permissions > Display pop-up windows while running in background.
- Notification access: Settings > Apps > Special app access > Notification access > IRA.
- Autostart: Settings > Apps > Manage apps > IRA > Autostart. Turn it on.
- Battery: Settings > Battery > App battery saver > IRA > No restrictions.
EOF
      ;;
    OnePlus)
      cat <<'EOF'
OnePlus, Oppo, Realme setup:
- Accessibility: Settings > System settings > Accessibility > Downloaded apps > IRA Assistant.
- Notification access: Settings > Apps > Special app access > Notification access > IRA.
- Overlay: Settings > Apps > Special app access > Display over other apps > IRA.
- Auto launch: Settings > Apps > Auto launch > IRA. Turn it on.
- Battery: Settings > Battery > Battery optimization > IRA > Don't optimize.
EOF
      ;;
    Motorola)
      cat <<'EOF'
Motorola setup:
- Accessibility: Settings > Accessibility > Downloaded apps > IRA Assistant.
- Notification access: Settings > Apps > Special app access > Notification access > IRA.
- Overlay: Settings > Apps > Special app access > Display over other apps > IRA.
- Battery: Settings > Battery > Battery optimization > IRA > Don't optimize.
- Auto-start: Settings > Battery > App launch, or Manage how apps use battery > Allow apps to start automatically > IRA.
EOF
      ;;
    *)
      cat <<'EOF'
Android setup:
- Use the rows on the IRA main screen. They open the matching system panels.
- Enable Accessibility, Notification access, Display over other apps, and unrestricted battery.
EOF
      ;;
  esac
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --skip-build) SKIP_BUILD=1 ;;
    --models)
      [ "$#" -ge 2 ] || die "--models needs auto, small, big, both, or none."
      MODEL_MODE="$2"
      shift
      ;;
    --skip-models) MODEL_MODE="none" ;;
    -h|--help) usage; exit 0 ;;
    *) die "Unknown option: $1. Use --help." ;;
  esac
  shift
done

case "$MODEL_MODE" in
  auto|small|big|both|none) ;;
  *) die "Invalid --models value: $MODEL_MODE. Use auto, small, big, both, or none." ;;
esac

need adb
need java

java_major="$(java -version 2>&1 | awk -F'"' '/version/ {print $2}' | awk -F. '{print ($1 == "1" ? $2 : $1)}')"
if [ -n "$java_major" ] && [ "$java_major" -lt 17 ]; then
  die "JDK 17 or newer is required. Current major version: $java_major."
fi

if [ ! -f "$ROOT/local.properties" ]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [ -z "$sdk" ]; then
    die "local.properties is missing. Set ANDROID_HOME or create local.properties with sdk.dir=/path/to/android-sdk."
  fi
  printf 'sdk.dir=%s\n' "$sdk" > "$ROOT/local.properties"
  info "Created local.properties from ANDROID_HOME"
fi

sdk_dir="$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties" | head -n 1)"
if [ -n "$sdk_dir" ] && [ ! -d "$sdk_dir" ]; then
  die "local.properties points to a missing SDK: $sdk_dir"
fi

if [ ! -f "$ROOT/.llama/llama.cpp/CMakeLists.txt" ]; then
  need git
  info "Initializing llama.cpp submodule"
  git -C "$ROOT" submodule update --init --recursive
fi

adb devices >/dev/null 2>&1 || true
device_table="$(adb devices 2>/dev/null | awk 'NR > 1 && NF >= 2 {print $1, $2}')"

if printf '%s\n' "$device_table" | grep -q ' unauthorized$'; then
  die "Device is unauthorized. Unlock it and accept the USB debugging prompt."
fi

if [ -z "$device_table" ]; then
  die "No adb device. Connect the phone, enable USB debugging, and accept the RSA prompt."
fi

device_count="$(printf '%s\n' "$device_table" | awk '$2 == "device" {count++} END {print count + 0}')"
if [ "$device_count" -eq 0 ]; then
  printf '%s\n' "$device_table" >&2
  die "No authorized device. Accept the USB debugging prompt on the phone."
fi
if [ "$device_count" -gt 1 ] && [ -z "${ANDROID_SERIAL:-}" ]; then
  printf '%s\n' "$device_table" >&2
  die "Multiple devices are connected. Set ANDROID_SERIAL to one serial and retry."
fi

if [ -n "${ANDROID_SERIAL:-}" ]; then
  SERIAL="$ANDROID_SERIAL"
else
  SERIAL="$(printf '%s\n' "$device_table" | awk '$2 == "device" {print $1; exit}')"
fi

if ! printf '%s\n' "$device_table" | awk -v serial="$SERIAL" '$1 == serial && $2 == "device" {found = 1} END {exit(found ? 0 : 1)}'; then
  die "Device $SERIAL is not connected or not authorized."
fi

abi="$(adb -s "$SERIAL" shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r' | head -n 1)"
sdk="$(adb -s "$SERIAL" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' | head -n 1)"

case "$abi" in
  arm64-v8a|armeabi-v7a) ;;
  *) die "This APK supports arm64-v8a and armeabi-v7a. Device $SERIAL reports: ${abi:-unknown}." ;;
esac

case "$sdk" in
  ''|*[!0-9]*) die "Could not read the device API level." ;;
esac
if [ "$sdk" -lt 26 ]; then
  die "Android API 26 or newer is required. Device $SERIAL reports API $sdk."
fi

manufacturer="$(adb -s "$SERIAL" shell getprop ro.product.manufacturer 2>/dev/null | tr -d '\r' | head -n 1)"
model="$(adb -s "$SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r' | head -n 1)"
oem_key="$(printf '%s' "$manufacturer" | tr '[:upper:]' '[:lower:]')"

case "$oem_key" in
  samsung*) OEM="Samsung"; OEM_LABEL="Samsung" ;;
  xiaomi*|redmi*|poco*) OEM="Xiaomi"; OEM_LABEL="Xiaomi/Redmi/Poco" ;;
  oneplus*|oppo*|realme*) OEM="OnePlus"; OEM_LABEL="OnePlus/Oppo/Realme" ;;
  motorola*|moto*) OEM="Motorola"; OEM_LABEL="Motorola" ;;
  *) OEM="Other"; OEM_LABEL="${manufacturer:-Android device}" ;;
esac

ram_kb="$(adb -s "$SERIAL" shell cat /proc/meminfo 2>/dev/null | awk '/MemTotal/ {print $2; exit}' | tr -d '\r')"
case "$ram_kb" in
  ''|*[!0-9]*) RAM_MB=0 ;;
  *) RAM_MB=$((ram_kb / 1024)) ;;
esac

info "Device: $OEM_LABEL ${model:-unknown}, API $sdk, $abi, ${RAM_MB} MB RAM"

if [ "$RAM_MB" -gt 0 ] && [ "$RAM_MB" -lt 3500 ]; then
  info "Warning: 4GB RAM class or higher is recommended. Use the 270M model on this device."
fi

if [ "$SKIP_BUILD" -eq 0 ]; then
  info "Building debug APK"
  ./gradlew --console=plain :app:lintDebug :app:assembleDebug
fi

if [ ! -f "$APK" ]; then
  die "APK not found at $APK. Run without --skip-build."
fi

info "Installing $APP_ID on $OEM_LABEL ${model:-unknown} (API $sdk, $abi, ${RAM_MB} MB RAM)"
set +e
install_output="$(adb -s "$SERIAL" install -r "$APK" 2>&1)"
install_status=$?
set -e
printf '%s\n' "$install_output"

if [ "$install_status" -ne 0 ]; then
  case "$install_output" in
    *INSTALL_FAILED_USER_RESTRICTED*|*INSTALL_FAILED_VERIFICATION_FAILURE*)
      case "$OEM" in
        Samsung) print_samsung_install_block ;;
        Xiaomi) print_xiaomi_install_block ;;
        *) print_generic_install_block ;;
      esac
      exit 2
      ;;
    *INSTALL_FAILED_UPDATE_INCOMPATIBLE*)
      cat >&2 <<EOF
The installed app uses a different signing key.

Ask the user before deleting app data. Then run:
  adb -s $SERIAL uninstall $APP_ID
  ./scripts/agent-install.sh
EOF
      exit 3
      ;;
    *INSTALL_FAILED_VERSION_DOWNGRADE*)
      die "The installed build is newer. Ask the user before uninstalling it."
      ;;
    *INSTALL_FAILED_INSUFFICIENT_STORAGE*)
      die "Not enough free storage. Free at least 200 MB for the app, plus about 1.2 GB for both GGUF models."
      ;;
    *INSTALL_FAILED_NO_MATCHING_ABIS*)
      die "ABI mismatch. Use an ARM Android phone with arm64-v8a or armeabi-v7a."
      ;;
    *INSTALL_FAILED_OLDER_SDK*)
      die "Android API 26 or newer is required."
      ;;
    *)
      die "adb install failed. Read the error above and see AGENTS.md."
      ;;
  esac
fi

if ! adb -s "$SERIAL" shell pm path "$APP_ID" 2>/dev/null | grep -q '^package:'; then
  die "Install reported success, but $APP_ID is not present. Run: adb -s $SERIAL shell pm path $APP_ID"
fi

push_small=0
push_big=0
if [ "$MODEL_MODE" = "small" ] || [ "$MODEL_MODE" = "both" ] || [ "$MODEL_MODE" = "auto" ]; then
  if [ -f "$MODEL_SMALL" ]; then push_small=1; fi
fi
if [ "$MODEL_MODE" = "big" ] || [ "$MODEL_MODE" = "both" ]; then
  if [ -f "$MODEL_BIG" ]; then push_big=1; fi
fi
if [ "$MODEL_MODE" = "auto" ] && [ -f "$MODEL_BIG" ]; then
  if [ "$abi" = "armeabi-v7a" ] || [ "$RAM_MB" -le 0 ] || [ "$RAM_MB" -lt 5500 ]; then
    info "Skipping 1.5B model on 32-bit, unknown RAM, or under 5.5GB RAM. Use --models big to force it."
  else
    push_big=1
  fi
fi

if [ "$push_small" -eq 0 ] && [ "$push_big" -eq 0 ]; then
  if [ ! -f "$MODEL_SMALL" ] && [ ! -f "$MODEL_BIG" ]; then
    info "No GGUF models found. Skipping on-device model. Run ./scripts/fetch-models.sh to add them."
  else
    info "No GGUF models selected for this device. Local model stays off."
  fi
else
  info "Stopping app before model copy"
  adb -s "$SERIAL" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
  adb -s "$SERIAL" shell run-as "$APP_ID" mkdir -p files/models

  for model_file in "$MODEL_SMALL" "$MODEL_BIG"; do
    [ -f "$model_file" ] || continue
    case "$model_file" in
      "$MODEL_SMALL") [ "$push_small" -eq 1 ] || continue ;;
      "$MODEL_BIG") [ "$push_big" -eq 1 ] || continue ;;
    esac
    name="$(basename "$model_file")"
    info "Pushing $name"
    adb -s "$SERIAL" push "$model_file" "/data/local/tmp/$name" >/dev/null
    adb -s "$SERIAL" shell run-as "$APP_ID" cp "/data/local/tmp/$name" "files/models/$name"
    adb -s "$SERIAL" shell rm -f "/data/local/tmp/$name"
  done
fi

info "Launching $APP_ID"
adb -s "$SERIAL" shell am start -n "$APP_ID/.MainActivity" >/dev/null

cat <<EOF

Installed $APP_ID on $OEM_LABEL ${model:-unknown} ($SERIAL).

Human steps now:
1. Read and accept the legal consent dialog on the phone. Do not bypass it.
2. Grant microphone and notification prompts.
3. Open the IRA rows and enable:
   - Screen control: Accessibility > IRA Assistant
   - Floating bubble: Display over other apps > IRA
   - Notifications: Notification access > IRA
   - Calls and Messages: grant the prompts
4. Tap the orb to start the wake service.
5. Optional: add a Gemini key in Cloud key.

EOF
print_oem_steps
cat <<EOF

If an Accessibility or Notification access toggle is greyed out:
App info > IRA > three dots > Allow restricted settings.

Verify:
  adb -s $SERIAL shell pm path $APP_ID
  adb -s $SERIAL shell run-as $APP_ID ls -l files/models

No audio, TTS, or barge-in test was run by this script.
EOF
