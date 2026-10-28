# IRA Legal Documents

**Status:** draft for review  
**Last updated:** 28 October 2026  
**App version covered:** `v0.1.0-m0` private debug build  
**Not legal advice.** Have a qualified lawyer review these documents before public distribution.

## Files

- `PRIVACY_POLICY.md`: what IRA processes, where data goes, retention, rights, and controls.
- `TERMS_OF_SERVICE.md`: license, device actions, AI limits, disclaimers, liability, and governing law.
- `ACCEPTABLE_USE.md`: prohibited uses and enforcement.
- `PERMISSIONS_AND_DATA.md`: permission-by-permission disclosure and data-flow tables.
- `PLAY_DATA_SAFETY.md`: internal working notes for the Play Console Data Safety form, including open classification questions. Do not publish this file.

## In-app copies

The app bundles the four public documents in `app/src/main/assets/legal/`. It shows a first-launch consent dialog and a Privacy and terms entry in the main screen.

Acceptance is versioned in `vp_prefs`. The current version gates voice service starts, tool intents, accessibility screen capture, notification reading, and the orb. If the version changes, the consent dialog appears again before those features resume.

After editing any public document:

1. Run `./scripts/sync-legal-assets.sh`.
2. Bump `LegalDocs.VERSION` in `app/src/main/java/com/voicepoco/app/LegalDocs.kt`.
3. Rebuild the app.

Bumping the version makes the consent dialog appear again for existing users so they can review the updated terms.

## Required placeholders

Replace every bracketed placeholder before publishing:

- `[Legal name]`
- `[Postal address]`
- `[privacy email]`
- `[legal contact email]`
- `[security contact email]`
- `[abuse contact email]`
- `[Grievance Officer]` name, email, and address
- `[Jurisdiction]`
- `[City, Jurisdiction]`

Use a business address or a registered agent address. Do not publish a home address unless you intend to make it public.

## Public hosting

Google Play requires a public HTTPS privacy policy URL. A private GitHub repository is not a public URL.

Recommended options:

1. Create a small public repository that contains only the four public legal documents: `PRIVACY_POLICY.md`, `TERMS_OF_SERVICE.md`, `ACCEPTABLE_USE.md`, and `PERMISSIONS_AND_DATA.md`. Do not publish `PLAY_DATA_SAFETY.md` or this checklist unless you intend to make the internal review notes public. Then enable GitHub Pages.
2. Publish the same files to a static host such as Cloudflare Pages, Netlify, or Vercel.
3. Host them on your own domain.

Suggested public URLs:

- `https://example.com/ira/privacy`
- `https://example.com/ira/terms`
- `https://example.com/ira/acceptable-use`
- `https://example.com/ira/permissions`

If you use GitHub Pages with Markdown files, confirm that the rendered pages are reachable without login and that the privacy page is the exact URL you enter in Play Console.

## Before public release

- Replace all placeholders.
- Publish the Privacy Policy at a public HTTPS URL.
- Keep the in-app Privacy and terms entry and first-launch consent flow in sync with the published documents.
- Test first launch, Accept, Decline, each document link, small-screen scrolling, large font sizing, and the version-bump prompt.
- Update `README.md` with the public legal URLs.
- Produce a release build, not a debug build.
- Disable or redact sensitive diagnostic logging in release builds. The current debug build logs command text, contact names, phone numbers, message text, model output, and spoken replies to Android logcat.
- Review `READ_CALL_LOG`. It is declared in the manifest and requested in `MainActivity`, but the current code does not read call history. Remove it or document an approved caller-ID use case before Play review.
- Review `QUERY_ALL_PACKAGES`, SMS, Call Log, Accessibility, Notification Access, overlay, and foreground microphone use against current Google Play policies.
- Complete the Play Console Data Safety form consistently with `PERMISSIONS_AND_DATA.md` and `PLAY_DATA_SAFETY.md`.
- Do not submit "no data collected" while cloud prompts, conversation history, or speech audio can leave the device.
- Confirm that third-party provider links and terms are current.
- Confirm the operator's legal name, jurisdiction, and contact details.
- Have a qualified lawyer review the final package.

## Current code facts used in these documents

- Cloud chat is on by default.
- Cloud providers: Pollinations by default; Google Gemini if the user adds a key.
- Web research: DuckDuckGo HTML search, then a cloud provider for the answer.
- Conversation history: up to 14 messages in memory.
- Notifications: last 20 strings in memory; latest five exposed to the read-notifications tool.
- Wake word and barge-in: processed on device by Vosk; no audio file storage found.
- Command transcription: Android `SpeechRecognizer`, which may use a network speech service such as Google. This is not controlled by the cloud chat toggle. Short call prompts use Vosk on the device.
- Spoken replies: Android `TextToSpeech`, which may use a network or vendor engine. This is not controlled by the cloud chat toggle.
- Screen content: processed on device through Accessibility; not uploaded by IRA.
- Contacts and call state: processed on device; not uploaded by IRA.
- SMS: passed to Android `SmsManager` or WhatsApp; not stored by IRA in its own database.
- Preferences: `vp_prefs`; personal notes: `vp_memory`, up to 50 notes.
- Android backup: disabled.
- No analytics, advertising, or crash-reporting SDKs found in the current dependency list.
- Debug logs can contain sensitive text.

## Maintenance

When the app changes, update these documents before release if the change affects:

- permissions or special access;
- cloud providers or endpoints;
- data sent to a provider;
- local storage or retention;
- account features;
- payments;
- children's features;
- third-party SDKs;
- the operator's legal details.

Keep the effective date and version current.
