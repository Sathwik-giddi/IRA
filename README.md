# IRA

IRA is a voice-first assistant for Android. It runs a wake-word loop, executes device commands, reads notifications when allowed, and can use an on-device or cloud model for natural-language decisions.

This repository is private.

## What is included

- Android app source in `app/`
- Vosk wake-word and command model in `app/src/main/assets/model/`
- Native llama.cpp bridge in `app/src/main/cpp/`
- llama.cpp as a pinned git submodule at `.llama/llama.cpp`
- Command-line harness in `harness/`
- Engineering notes in `doing.md` and `gap-analysis.md`

The two GGUF language models are attached to the private `models-v1` GitHub release. See [Models](#models).

## For coding agents

Read [`AGENTS.md`](AGENTS.md) before changing or installing anything. For a first-time install, run:

```bash
./scripts/agent-install.sh
```

Claude Code can start from [`CLAUDE.md`](CLAUDE.md), which points to the same runbook. The script builds, installs, pushes the right local model for the device, and launches the app. It prints OEM-specific setup for known brands and generic Android setup for every other OEM. Legal consent and Android permission screens remain human-only steps.

## Device support

IRA targets Android phones with `arm64-v8a` or `armeabi-v7a` and API 26 or newer. All Android OEMs are covered. 4GB RAM is recommended for the local 270M model, but cloud mode works on less. Apple iOS is not supported. See [`docs/DEVICE_SUPPORT.md`](docs/DEVICE_SUPPORT.md) for the OEM matrix, install gates, permission paths, model guidance, and official source links.

## Requirements

- macOS or Linux
- JDK 17
- Android SDK 34
- Android NDK `27.2.12479018`
- CMake `3.22.1`
- An ARMv7 or ARM64 Android phone or emulator
- Git

## Setup

```bash
git clone --recurse-submodules <repo-url> IRA
cd IRA
```

If the repository was cloned without submodules:

```bash
git submodule update --init --recursive
```

Create `local.properties` with your Android SDK path:

```properties
sdk.dir=/path/to/android-sdk
```

Build the debug app:

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Runtime permissions

IRA asks for permissions only when a feature needs them. The main permissions are:

| Permission | Why it is used |
| --- | --- |
| Microphone | Wake word and voice commands |
| Notifications | Foreground service status |
| Overlay | Floating bubble above other apps |
| Accessibility | Reading the screen and performing taps or scrolling when enabled by the user |
| Notification access | Reading notifications when enabled by the user |
| Contacts | Resolving names for calls and messages |
| Phone and call log | Call announcements, answering, and declining |
| SMS | Sending messages on request |

The freeze switch stops the wake service and makes the accessibility and notification hooks inert. Android does not allow an app to silently re-enable accessibility after it has been disabled, so those hooks remain registered but do nothing until resume.

## Models

The Vosk model is committed because it is small enough for normal Git.

Both GGUF models are attached to the private `models-v1` release:

- `functiongemma-270m-it-Q4_K_M.gguf` (241 MB)
- `Qwen2.5-1.5B-Instruct-Q4_K_M.gguf` (940 MB)

Download them with GitHub CLI:

```bash
./scripts/fetch-models.sh
```

Or directly:

```bash
gh release download models-v1 -R Sathwik-giddi/IRA -D models --pattern '*.gguf'
```

The app expects the files in its private files directory:

```text
/data/user/0/com.voicepoco.app/files/models/functiongemma-270m-it-Q4_K_M.gguf
/data/user/0/com.voicepoco.app/files/models/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf
```

The installer uses `auto` mode by default: 270M on every ARM phone, plus 1.5B only on 64-bit devices with 5.5GB or more RAM. Override it with `--models small`, `--models big`, `--models both`, or `--models none`. If both files are installed, the `Local model` row switches between them and applies after IRA restarts.

See `models/README.md` for checksums and the on-device install commands. The model files are release assets, not Git objects, because they exceed the free LFS storage limit.

## Secrets

Never commit API keys or tokens. The app reads its keys from local preferences at runtime. Use `.env.example` as a naming reference only.

If a key is ever committed, revoke it immediately and rewrite the history before publishing.

## Privacy and terms

- [Privacy Policy](legal/PRIVACY_POLICY.md)
- [Terms of Service](legal/TERMS_OF_SERVICE.md)
- [Acceptable Use Policy](legal/ACCEPTABLE_USE.md)
- [Permissions and Data Flow](legal/PERMISSIONS_AND_DATA.md)

These are drafts. Replace the bracketed operator, contact, jurisdiction, and address placeholders before publishing. Google Play requires a public HTTPS privacy policy URL; a private repository URL is not sufficient. See [legal/README.md](legal/README.md) for the publication checklist.

The app bundles offline copies and shows a first-launch consent dialog. Use the Privacy and terms row in the app to read the full documents. After editing a public legal document, run `./scripts/sync-legal-assets.sh` and bump `LegalDocs.VERSION`.

## Status

Private development build. Not a production release.
