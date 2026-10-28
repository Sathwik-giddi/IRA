# Play Data Safety Working Notes

**Status:** internal working draft, not a submitted Play Console form  
**Last updated:** 28 October 2026  
**App version covered:** `v0.1.0-m0` private debug build  
**Internal only:** Do not publish this file with the public legal pages. It contains unresolved classification questions.  
**Not legal advice.** Verify every answer against the current Google Play Data Safety definitions and the code that is actually shipping.

## Why this file exists

The Play Console Data Safety form is separate from the Privacy Policy. A policy can describe a data flow in prose while the form still requires a data-type answer. These notes map the current code to candidate answers so the form can be completed consistently.

Do not submit "no data collected" for this build. Free-form commands, conversation history, and web research text can leave the device through cloud providers. Voice command audio may also leave the device through the Android speech recognition service.

## Candidate answers

| Play data type | Candidate answer | Why | Open question |
| --- | --- | --- | --- |
| Personal info: Other | Likely yes | A free-form command can contain a name, phone number, address, or other personal detail, and that text is sent to Pollinations or Google Gemini when cloud chat is used. | Decide whether to classify free-form command text as Personal info, User content, or both. |
| Messages: Other | Likely yes | A dictated SMS or WhatsApp message can include its content in the command text sent to a cloud provider. IRA does not store the message in its own database. | Confirm how Play treats message text that is only transient in a prompt. |
| Audio: Voice or sound recordings | Unclear | Wake-word and barge-in audio stay on device with Vosk. Most command audio goes to Android `SpeechRecognizer`, which may send audio to Google. IRA does not receive or store the audio. | Confirm whether a platform speech service counts as app collection for this form. |
| App activity: Other user-generated content | Likely yes | Commands and up to 14 recent conversation messages are sent to cloud providers. | Confirm whether notes in `vp_memory` count. They stay on device. |
| App activity: App interactions | No | No analytics, advertising, or crash SDK was found. | Recheck after any SDK is added. |
| Contacts | No | The contact list stays on device. A contact name can still appear inside command text sent to a cloud provider. | Decide whether to disclose names inside free-form text under Personal info instead. |
| Device or other IDs | No | IRA does not read or send an advertising ID, Android ID, or IMEI. Providers receive normal network metadata such as IP address. | Confirm how to treat provider-side IP metadata. |
| App info and performance: Diagnostics | No | Debug logs stay in Android logcat. No crash-reporting SDK is present. | Recheck before release logging changes. |
| Location | No | IRA does not request location permission or read location. | None. |
| Financial info | No | IRA has no payments or financial features. | None. |
| Health and fitness | No | IRA has no health features. | None. |
| Photos and videos | No | IRA does not request camera or photo access. | The torch tool uses the system torch API without a camera permission. |
| Files and docs | No | IRA does not request storage access. | None. |
| Calendar | No | IRA can open a calendar insert intent, but it does not read the calendar. | Confirm the intent does not return calendar data to IRA. |
| Web browsing | No | Web research uses DuckDuckGo HTML search, not the user's browser history. | Confirm search queries are covered under App activity or User content. |

## Sharing

If the form asks whether data is shared:

- Candidate answer: Yes.
- Recipients: Pollinations, Google Gemini, DuckDuckGo, and the user's selected Android speech and text-to-speech providers.
- Purpose: App functionality.
- Optional: Cloud chat can be turned off, but Android speech recognition and TTS are separate platform services and are not controlled by that toggle.

Do not claim that data is never shared. The app sends prompts directly to third-party providers when cloud features are used.

## Security

- HTTPS is used for Pollinations, Gemini, DuckDuckGo, and GitHub requests.
- Android speech recognition and TTS transport security depends on the selected platform provider.
- `allowBackup` is disabled.
- Preferences and the Gemini key are not encrypted by IRA.
- Debug builds can write sensitive text to Android logcat.

Do not claim encryption at rest. Do not claim that all data stays on device.

## Deletion

- There is no IRA account.
- Users can clear app data or uninstall IRA.
- Users can remove personal notes and revoke permissions.
- Users can rotate or remove the Gemini key.
- Cloud provider copies are not deleted by uninstalling IRA. Users must use the provider's controls or contact the provider.

If Play requires a deletion request URL, decide whether an account-less app needs one. Do not publish a deletion URL that does not work.

## Before submitting the form

1. Confirm the release build, not the debug build, is the one being described.
2. Re-audit cloud endpoints and logging after release cleanup.
3. Confirm whether Android `SpeechRecognizer` audio must be disclosed as Audio.
4. Confirm whether free-form command text must be disclosed as Personal info, Messages, User content, or a combination.
5. Confirm each provider's current retention terms.
6. Make the Privacy Policy, this worksheet, and the Play Console answers agree.
7. Have a qualified lawyer or Play policy specialist review the final answers.
