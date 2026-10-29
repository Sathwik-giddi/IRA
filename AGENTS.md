# AGENTS.md

Instructions for coding agents working on IRA. Read this before changing code, building, or installing the app.

## First-time install

Run the install script from the repository root:

```bash
./scripts/agent-install.sh
```

The script is non-interactive. It checks the toolchain and one compatible device, builds the debug APK, installs `com.voicepoco.app`, pushes local GGUF models when present, and launches the app. It prints Samsung, Xiaomi/Redmi/Poco, OnePlus/Oppo/Realme, or Motorola setup when it recognizes the OEM. Every other Android OEM gets generic Android setup.

Device scope: any ARM Android phone with `arm64-v8a` or `armeabi-v7a`, Android API 26 or newer. 4GB RAM is recommended for the local 270M model, not required for cloud mode. Apple iOS is not supported. See `docs/DEVICE_SUPPORT.md` for the full matrix, official source links, and OEM permission paths.

Flags:

```bash
./scripts/agent-install.sh --skip-build          # reuse an existing APK
./scripts/agent-install.sh --models small        # push only the 270M model
./scripts/agent-install.sh --models both         # push both models, even on low RAM
./scripts/agent-install.sh --models none         # do not push GGUF files
./scripts/agent-install.sh --skip-models         # alias for --models none
./scripts/agent-install.sh --help
```

`--models auto` is the default. It pushes the 270M model to every ARM phone and adds the 1.5B model only on 64-bit devices with 5.5GB or more RAM.

If the script exits nonzero, use the exact error and the matching section below. Do not improvise a workaround.

## Human-only steps after install

The script stops before these on purpose:

1. Accept the legal consent dialog on the phone. The user must read and accept it. Never set `legal_accepted_version` through adb, `run-as`, or preferences.
2. Grant the microphone and notification prompts.
3. Open IRA and use the rows on the main screen:
   - `Screen control`: Accessibility > IRA Assistant
   - `Floating bubble`: Display over other apps > IRA
   - `Notifications`: Notification access > IRA
   - `Calls`: call and call log access
   - `Messages`: SMS and contacts access
4. Tap the orb to start the wake service.
5. Optional: add a Gemini key in `Cloud key`.

Android protects accessibility, notification access, overlay, SMS, and call log. Do not script around those protections. Do not use `settings put secure enabled_accessibility_services`, `cmd notification allow_listener`, or similar bypasses.

## Manual fallback

Use this only when the script cannot run. Run every command from the repository root.

```bash
git submodule update --init --recursive
./gradlew --console=plain :app:lintDebug :app:assembleDebug
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.voicepoco.app/.MainActivity
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. The package is `com.voicepoco.app`.

If `local.properties` is missing, create it with the Android SDK path:

```properties
sdk.dir=/absolute/path/to/android-sdk
```

## Models

The app works without GGUF files in cloud mode. On-device inference needs the private release assets:

```bash
./scripts/fetch-models.sh
```

That command needs `gh auth login` for the private `Sathwik-giddi/IRA` repo. The installer pushes the model files selected by `--models`:

```text
models/functiongemma-270m-it-Q4_K_M.gguf
models/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf
```

If both files are installed, the `Local model` row on the phone switches between 270M and 1.5B. The switch applies after IRA restarts. The app reads models from its private directory:

```text
/data/user/0/com.voicepoco.app/files/models/
```

See `models/README.md` for checksums. Do not commit GGUF files, APKs, or `local.properties`.

## Troubleshooting

### No device

Connect the phone with a data-capable USB cable. Enable `Developer options > USB debugging`, then accept the RSA fingerprint prompt on the phone.

### Device is unauthorized

Unlock the phone, tap `Allow` on the USB debugging prompt, then run:

```bash
adb kill-server
adb devices
```

### Samsung Auto Blocker blocks the install

`INSTALL_FAILED_USER_RESTRICTED` or `INSTALL_FAILED_VERIFICATION_FAILURE` on a Samsung device usually means Auto Blocker is on.

On the phone:

1. Settings > Security and privacy > Auto Blocker. Turn it off temporarily.
2. If `Maximum restrictions` is on, turn it off for the install.
3. Settings > Security and privacy > More security settings > Install unknown apps. Allow the source you use.
4. Rerun `./scripts/agent-install.sh`.
5. Turn Auto Blocker back on after setup.

Samsung enables Auto Blocker by default on devices released with One UI 6.1.1 and later. It is a user security setting. Do not use Knox or a signing-key bypass.

### Xiaomi, Redmi, or Poco blocks the install

`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user` is a Xiaomi restriction, not an APK bug.

On the phone:

1. Settings > About phone > tap `OS` or `MIUI version` seven times.
2. Settings > Additional settings > Developer options.
3. Enable `USB debugging` and `Install via USB`.
4. If `Install via USB` is unavailable, sign in to a Mi account and insert a SIM, then retry.
5. Settings > Privacy protection > Special permissions > Install unknown apps. Allow the source.

Then rerun `./scripts/agent-install.sh`. Do not use a bootloader unlock or a security bypass.

### Install fails because the signatures differ

`INSTALL_FAILED_UPDATE_INCOMPATIBLE` means the installed build used a different signing key.

Ask the user before deleting app data. Uninstalling removes preferences, consent state, and models. Then run:

```bash
adb uninstall com.voicepoco.app
./scripts/agent-install.sh
```

### ABI or SDK mismatch

The APK includes `arm64-v8a` and `armeabi-v7a` native libraries and supports Android API 26 or newer. Use an ARM Android phone or emulator. Do not add `x86` or `x86_64` filters unless the native llama.cpp build is verified for those ABIs.

### Install succeeds but the app cannot find a model

Run `./scripts/fetch-models.sh`, then rerun:

```bash
./scripts/agent-install.sh
```

The script pushes existing model files without rebuilding. Use `--skip-build` if the APK is already current.

### The legal dialog does not appear

Confirm the app data is fresh, or that the stored legal version is older than `LegalDocs.VERSION`:

```bash
adb shell pm clear com.voicepoco.app
adb shell am start -n com.voicepoco.app/.MainActivity
```

`pm clear` deletes app data, preferences, and models. Ask the user before running it.

### Accessibility or notification access stays off

Android does not allow an app to grant these silently. Open the settings from the IRA rows and enable them by hand. Do not use adb to flip the secure settings.

If the toggle is greyed out on Android 13 or newer, open App info > IRA > three dots > `Allow restricted settings`, then try again.

### Need runtime logs

```bash
adb logcat -d -s AndroidRuntime VP_BRAIN VP_TREE
```

Do not paste API keys, tokens, or personal notification contents into logs, commits, or issue reports.

## Verification

After install, check the package and models:

```bash
adb shell pm path com.voicepoco.app
adb shell dumpsys package com.voicepoco.app | grep versionName
adb shell run-as com.voicepoco.app ls -l files/models
```

Expected package path starts with `package:`. The version is `0.1.0-m0` for this build. The model directory is empty when `--skip-models` was used and the files are absent.

## Project rules

- Do not commit or push unless the user explicitly asks.
- Do not add hardcoded command ladders, canned replies, or scripted intent matching. The model and tool schema own the decision path.
- Use free models only.
- Do not bypass the legal consent dialog or Android permission screens.
- Treat the legal documents as drafts until the operator, address, contact, jurisdiction, and hosting placeholders are resolved.
- Do not uninstall the app or clear app data without explicit user permission.
- Do not run audio, TTS, or barge-in tests without explicit user permission.
- Do not commit secrets, API keys, `local.properties`, APKs, or GGUF files.
- Do not use `adb root`, modify device security, or install a second signing key.
- Use plain hyphens, not em dashes, in new text.
- Update `doing.md` with a timestamp for each work session.
