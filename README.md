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

Large GGUF language models are not stored in the repository. See [Models](#models).

## Requirements

- macOS or Linux
- JDK 17
- Android SDK 34
- Android NDK `27.2.12479018`
- CMake `3.22.1`
- An arm64 Android device or emulator
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

The optional GGUF models are expected at the app's private files directory:

```text
/data/user/0/com.voicepoco.app/files/models/functiongemma-270m-it-Q4_K_M.gguf
/data/user/0/com.voicepoco.app/files/models/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf
```

Large model files should be stored with Git LFS or downloaded separately. Do not commit a GGUF file above GitHub's 100 MB per-file limit without LFS.

## Secrets

Never commit API keys or tokens. The app reads its keys from local preferences at runtime. Use `.env.example` as a naming reference only.

If a key is ever committed, revoke it immediately and rewrite the history before publishing.

## Privacy and terms

Privacy and terms documents are not bundled in this revision. Add them before distributing the app publicly.

## Status

Private development build. Not a production release.
