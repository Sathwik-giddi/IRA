# IRA Permissions and Data Flow

**App:** IRA  
**Android package:** `com.voicepoco.app`  
**Last updated:** 28 October 2026  
**Version:** 1.0-draft

This document explains what IRA can access, why it asks, where the data goes, and how you can control it. It supplements the Privacy Policy.

## 1. Runtime and special permissions

| Permission | Why IRA asks | What is processed | Where it goes | How to control |
| --- | --- | --- | --- | --- |
| `RECORD_AUDIO` | Wake word, command transcription, barge-in | Wake-word and barge-in audio; most command audio | Wake word and barge-in are processed by Vosk on the device. Most command audio goes to Android's speech recognition service, which may use a network provider such as Google. Short call prompts use Vosk on the device. IRA does not store or upload audio itself. | Revoke Microphone in Android Settings, or freeze IRA |
| `POST_NOTIFICATIONS` | Persistent notification for the foreground microphone service | Service status | Device only | Android notification settings |
| `FOREGROUND_SERVICE` | Keep the wake service running | Service state | Device only | Stop the service or freeze IRA |
| `FOREGROUND_SERVICE_MICROPHONE` | Declare microphone use in the foreground service | Service state | Device only | Stop the service or freeze IRA |
| `SYSTEM_ALERT_WINDOW` | Floating IRA bubble over other apps | Bubble state and short status text | Device only | Disable "Display over other apps" in Android Settings |
| `INTERNET` | Cloud chat, web research, model downloads | Request text, search query, provider responses | Sent over HTTPS to Pollinations, Google Gemini, DuckDuckGo, or GitHub as applicable | Turn off cloud chat, avoid web research, or block network access |
| `ACCESS_NETWORK_STATE` | Check whether the device is online before a cloud call | Network availability | Device only | Android network controls |
| `QUERY_ALL_PACKAGES` | List launchable apps and open an app by name | App labels and package names | Device only. Not uploaded by IRA. | Android does not provide a simple per-app toggle. Uninstall IRA to remove access. |
| `READ_CONTACTS` | Resolve a contact name to a number and show caller names | Names and phone numbers | Device only. The spoken name can be sent to a cloud provider if it is part of your request. | Revoke Contacts in Android Settings |
| `READ_PHONE_STATE` | Detect ringing and idle call states | Call state, and on some Android versions the incoming number | Device only | Revoke Phone in Android Settings |
| `READ_CALL_LOG` | Caller identification support on some Android versions | Incoming caller information, if the platform provides it | Device only. IRA does not read, store, or upload call history. | Revoke Call Log in Android Settings |
| `ANSWER_PHONE_CALLS` | Answer or decline a ringing call | Call control state | Device and telecom system | Revoke Phone in Android Settings |
| `CALL_PHONE` | Place a call directly | Contact name and phone number | Passed to Android Telecom and your carrier | Revoke Phone in Android Settings, or use the dialer-only path |
| `SEND_SMS` | Send an SMS you request | Recipient number and message text | Passed to Android SMS manager and your carrier | Revoke SMS in Android Settings |
| `WRITE_SETTINGS` | Change brightness and screen timeout | Brightness or timeout value | Device settings | Disable "Modify system settings" in Android Settings |
| `MODIFY_AUDIO_SETTINGS` | Change volume, ringer mode, and speakerphone state | Audio settings | Device only | Revoke or change settings manually |
| Accessibility service | Read screen labels, tap, scroll, type, go back, go home, take a screenshot, operate WhatsApp composition | Visible screen text and labels, action targets | Device memory. Not uploaded by IRA. Spoken text can be sent to a cloud provider if it is part of your request. | Disable IRA in Android Accessibility settings |
| Notification listener | Read notifications when you ask | Notification title, text, and app label, last 20 in memory | Device memory. Not uploaded by IRA. | Disable Notification Access in Android Settings |
| `BIND_ACCESSIBILITY_SERVICE` | Allow Android to bind the accessibility service | Service binding | Device only | Disable the accessibility service |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Allow Android to bind the notification listener | Service binding | Device only | Disable Notification Access |

IRA also declares `android.hardware.telephony` as not required, so the app can install on devices without telephony. Call and SMS features will not work on those devices.

## 2. Data that leaves the device

IRA does not operate a developer backend. The following data can leave the device when you use the related feature.

| Feature | Data sent | Destination | Notes |
| --- | --- | --- | --- |
| Cloud chat | Current request, up to 14 recent conversation messages, current time, tool list | Pollinations or Google Gemini | Cloud chat is on by default in this build. You can turn it off. |
| Cloud tool decision | Same as cloud chat | Pollinations or Google Gemini | The model returns a tool choice. The tool runs on the device. |
| Web research | Search query, then result titles and snippets | DuckDuckGo, then Pollinations or Google Gemini | IRA does not fetch every linked page. |
| Command transcription | Most command audio, language, and partial results | Android speech recognition service, often Google | This can happen even when cloud chat is off. The speech service is selected by Android. Short call prompts use Vosk on the device. |
| Spoken replies | Reply text | Android text-to-speech engine, often Google | This can happen even when cloud chat is off. The TTS engine is selected by Android. |
| Gemini key use | API key in the HTTPS authorization header | Google Gemini | The key is stored in app-private SharedPreferences. |
| App or model download | Standard HTTP request data | GitHub | Subject to GitHub's policies. |
| Opening a link or app | The data that app or website normally receives | The third-party app or website | Subject to that third party's policies. |

Cloud and platform providers may also receive your IP address, approximate network location, device type, and request metadata as part of a normal network request. Their retention and logging practices are governed by their own policies.

## 3. Data that stays on the device

| Data | Storage | Retention |
| --- | --- | --- |
| Audio | Device memory and the Android speech service | IRA does not store audio. The speech provider may retain it under its own policy. |
| Transcripts | Memory | Until the process ends or conversation reset |
| Conversation history | Memory, up to 14 messages | Until the process ends or conversation reset |
| Notification strings | Memory, up to 20 | Until the process ends |
| Screen nodes | Memory | Until replaced, cleared, or the process ends |
| Preferences | SharedPreferences `vp_prefs` | Until cleared or uninstalled |
| Personal notes | SharedPreferences `vp_memory`, up to 50 notes | Until cleared or uninstalled |
| Gemini API key | SharedPreferences `vp_prefs` | Until removed, cleared, or uninstalled |
| On-device models | App-private files | Until deleted, cleared, or uninstalled |
| Debug logs | Android system log buffer | Until Android clears the buffer or the device is wiped |

IRA disables Android backup (`allowBackup=false`). IRA does not write audio files, a notification database, a contact database, or a message database.

## 4. Data IRA does not request

IRA does not request:

- location;
- camera;
- storage or media library;
- calendar read or write;
- `READ_SMS`;
- `RECEIVE_SMS`;
- `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, or `READ_MEDIA_AUDIO`;
- `BIND_DEVICE_ADMIN`;
- `REQUEST_INSTALL_PACKAGES`;
- `MANAGE_EXTERNAL_STORAGE`.

If a future version adds a new permission or data flow, this document and the Privacy Policy must be updated before release.

## 5. Debug build warning

The current private build is a debug build. It can write sensitive text to Android's local log, including command text, contact names, phone numbers, message text, model output, and spoken replies. Those logs are not sent to the developer by IRA, but they can be read by someone with device or ADB access.

Before public distribution, produce a release build and disable or redact sensitive diagnostic logging. Do not ship a debug build to public users.

## 6. Delete your data

To remove on-device data:

1. Remove the Gemini key in the IRA cloud settings.
2. Clear personal notes by clearing app data, or ask IRA to manage notes if a delete-note feature is added.
3. Revoke microphone, contacts, phone, SMS, accessibility, notification access, overlay, and modify-system-settings access in Android Settings.
4. Clear IRA app data in Android Settings.
5. Uninstall IRA.

Uninstalling IRA does not delete data held by Pollinations, Google, DuckDuckGo, GitHub, your carrier, or apps you interacted with. Use those providers' controls or contact them directly.

## 7. Play Store disclosure notes

Before distributing IRA through Google Play, review these restricted or high-risk areas:

- **Accessibility API:** must be core to the app's functionality and disclosed in the Play Console. The app must not use Accessibility for surveillance or covert data collection.
- **SMS and Call Log:** restricted permissions. Google Play requires a declaration and an approved use case. `READ_CALL_LOG` is declared but not used to read call history by the current code. Remove it or justify it before review.
- **`QUERY_ALL_PACKAGES`:** restricted. Google Play requires a declaration and an approved use case.
- **`SYSTEM_ALERT_WINDOW`:** must be disclosed and used only for the bubble.
- **Notification listener:** must be disclosed and used only for the notification-reading feature.
- **Foreground microphone service:** must show a clear persistent notification and comply with foreground service rules.
- **Privacy policy:** Google Play requires a public HTTPS URL. A private repository URL is not sufficient.

## 8. Contact

Questions about permissions or data:

**Email:** [privacy email]  
**Postal address:** [Postal address]
