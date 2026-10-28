# IRA Privacy Policy

**App:** IRA  
**Android package:** `com.voicepoco.app`  
**Operator:** [Legal name]  
**Address:** [Postal address]  
**Privacy contact:** [privacy email]  
**Grievance Officer:** [Name], [email], [address]  
**Effective date:** 28 October 2026  
**Last updated:** 28 October 2026  
**Version:** 1.0-draft

This Privacy Policy explains how IRA handles information when you install and use the app. It applies to the IRA Android app and the features described here. It does not apply to third-party apps, websites, or services that IRA can open or contact.

IRA is a voice-first assistant. It can listen for a wake word, understand commands, speak replies, read the screen when Accessibility is enabled, read notifications when Notification Access is enabled, resolve contacts, place calls, send messages, and use a cloud language model for chat and research.

## 1. Plain-language summary

- IRA has no user accounts and no developer-operated analytics or advertising service.
- Wake-word audio is processed on your device by Vosk. Most command audio is sent to Android's speech recognition service, which may use a network service such as Google. IRA does not store or upload audio itself.
- Cloud chat is on by default in this build. When you use chat, tools, or web research, the text of your request and recent conversation text are sent directly from your device to a cloud provider.
- If you add a Gemini API key, IRA tries Google Gemini first. Otherwise, or after a Gemini failure, it uses Pollinations.
- Web research sends your search query to DuckDuckGo and then sends the search results to a cloud provider to compose an answer.
- Screen content, notification content, contact lists, call history, and SMS databases are processed on your device. IRA does not upload those collections to the developer.
- Your words can still contain personal information. If you say a name, number, address, message, or other personal detail, that text may be sent to a cloud provider when cloud features are used.
- You can turn off cloud features, freeze IRA, revoke permissions, clear app data, or uninstall the app.
- IRA is not for emergencies, medical advice, legal advice, financial advice, or other high-risk decisions.

## 2. Information IRA processes on your device

### 2.1 Microphone and voice

When the wake service is on, IRA uses the microphone to detect its wake word. Wake-word audio is processed on your device by the bundled Vosk speech model.

After the wake word, IRA uses Android's `SpeechRecognizer` to transcribe your command. The speech recognition service is selected by Android and is often provided by Google. Depending on your device and settings, that service may process audio on the device or send it to a network service. IRA does not control that service. The cloud chat toggle does not turn off Android speech recognition. Some short prompts, such as the call answer prompt, use the on-device Vosk command model instead.

IRA does not create or keep audio recordings. IRA does not upload audio to the developer. The Android speech recognition provider may process or retain audio under its own policy.

The microphone can be active while the wake service is running. The persistent notification shows that the service is active. Freezing IRA stops the wake service and the microphone loop.

Barge-in uses a second audio stream while IRA is speaking so you can interrupt it. That audio is processed on your device by IRA and is not stored or uploaded by IRA.

Replies are spoken through Android's text-to-speech engine. The engine is selected by Android and is often provided by Google. The reply text is passed to that engine and may be processed by the engine provider under its own policy. The cloud chat toggle does not turn off text-to-speech.

### 2.2 Commands, transcripts, and conversation history

IRA detects the wake word on the device. For commands, IRA sends audio to Android's speech recognition service and receives text. The text is used to choose an action or reply. A short conversation history is kept in memory while the app process is running. It is not written to a conversation database by IRA. It is cleared when the app process ends or when the conversation is reset.

If cloud features are on, IRA sends the current request and up to 14 recent conversation messages to a cloud provider. The request also includes the current device time and the list of tools IRA can use. The cloud provider returns text or a tool choice.

### 2.3 Screen content and Accessibility

If you enable IRA in Android Accessibility settings, IRA can read text and labels from the screen, tap elements, scroll, type into fields, go back, go home, take a screenshot through Android's accessibility screenshot action, and operate some app flows such as WhatsApp message composition. Screen data is held in memory and is used to perform the action you requested.

IRA does not upload screen content to the developer. If you ask IRA to read the screen, the text is spoken to you. If you mention screen content in a voice command, that spoken text may be sent to a cloud provider when cloud features are used.

Accessibility is a powerful permission. You can turn it off at any time in Android Settings. Freezing IRA makes the accessibility service inert, but Android still lists the service until you disable it in Settings.

### 2.4 Notifications

If you enable Notification Access, IRA can read the title, text, and app label of new notifications. It keeps the 20 most recent notification strings in memory and exposes the latest five when you ask IRA to read notifications. IRA does not write notifications to a database and does not upload the notification list to the developer.

Notification content can be sensitive. If you ask IRA to read notifications aloud, other people nearby may hear it. You can revoke Notification Access at any time in Android Settings.

### 2.5 Contacts, calls, and messages

If you grant Contacts access, IRA reads contact names and phone numbers on your device to resolve who you want to call or message and to show a caller's name. IRA does not upload your contact list to the developer.

If you grant Phone access, IRA reads call state so it can announce, answer, or decline a call. On supported Android versions, the declared call log permission can help identify an incoming caller. IRA does not read, store, or upload your call history.

If you grant SMS access, IRA can send a message through Android's SMS manager. If you use WhatsApp composition, IRA uses Accessibility to operate WhatsApp on your device. IRA does not keep a separate message database. Your messaging app and your mobile carrier may store the message under their own policies.

### 2.6 Settings, notes, and preferences

IRA stores app settings in Android SharedPreferences on your device. These settings can include:

- whether the wake service was requested;
- whether IRA is frozen;
- whether cloud chat is on;
- whether action mode is on;
- whether automatic call answering is on;
- whether the larger on-device model is selected;
- the selected mode;
- guide and onboarding flags;
- the optional Gemini API key, if you add one;
- personal notes you ask IRA to remember, up to 50 notes in the `vp_memory` preferences.

The Gemini API key is stored in app-private SharedPreferences. It is not encrypted separately by IRA. Anyone with root access, a privileged backup, or another way to read app-private storage may be able to read it. Do not use a key that has broader access than you are willing to store on the device.

Android backup is disabled for IRA (`allowBackup=false`), so IRA does not use Android cloud backup for this app's private data.

### 2.7 Installed apps

IRA can list launchable apps and installed applications so it can open an app by name. App labels and package names are processed on your device. IRA does not upload your installed-app list to the developer.

### 2.8 Diagnostic logs

This private build is a debug build. Debug builds can write diagnostic messages to Android's local log. Those messages can include command text, contact names, phone numbers, message text, tool arguments, model output, and spoken reply text. The logs stay on the device and are not sent to the developer by IRA. Android and device manufacturers may retain logs in a system buffer, and someone with access to the device or ADB may be able to read them.

A production build should disable sensitive diagnostic logging before public distribution. See `legal/README.md`.

## 3. Cloud features and third parties

### 3.1 Cloud is on by default

In this build, cloud chat is enabled by default. You can turn it off on the IRA main screen. When cloud chat is off, IRA uses the on-device model and local tools only, subject to the limits of those features.

### 3.2 What is sent to a cloud provider

When cloud features are used, IRA can send:

- the text of your current request;
- up to 14 recent conversation messages;
- the current device time;
- the list of available tools and their parameter descriptions;
- for web research, your search query and the titles and snippets returned by DuckDuckGo;
- for a tool decision, the model's returned tool name and arguments are processed on the device, not sent to the developer.

IRA does not send your raw audio, screen-node collection, notification list, contact list, call history, or SMS database to the developer. Those collections are processed on the device. However, the Android speech recognition service may receive command audio independently of the cloud chat setting. In addition, the text you speak can include personal information, and that text is sent when cloud features are used.

### 3.3 Cloud and platform providers

| Provider | When it is used | Data sent | Provider terms |
| --- | --- | --- | --- |
| Pollinations | Default cloud provider when Gemini is not configured or Gemini fails | Request text, recent conversation, current time, tool list, and web research text | https://pollinations.ai/ |
| Google Gemini API | If you add a Gemini API key and the request succeeds | Same request content, plus the API key in the HTTPS authorization header | https://ai.google.dev/gemini-api/terms and https://policies.google.com/privacy |
| DuckDuckGo HTML search | When IRA performs web research | Your search query | https://duckduckgo.com/privacy |
| Android speech recognition service | When you speak a command after the wake word | Command audio, language, and partial results | Your device's selected speech service, often Google. See https://policies.google.com/privacy |
| Android text-to-speech engine | When IRA speaks a reply | Reply text | Your device's selected text-to-speech engine, often Google. See https://policies.google.com/privacy |
| GitHub | When you download the app, models, or documentation | Standard GitHub download data | https://docs.github.com/en/site-policy/privacy-policies/github-privacy-statement |

Cloud and platform providers may process data in the United States and other countries. They may also receive your IP address, approximate network location, device type, and request metadata as part of a normal network request. Their retention, logging, and use of data are governed by their own terms and privacy policies, not by this Policy. IRA does not control those providers. Do not use cloud features or voice features for information you are not willing to send to a third-party provider.

Call answer prompts use the on-device Vosk command model and are not sent to Android speech recognition.

### 3.4 Web research

For web research, IRA sends the search query to DuckDuckGo's HTML search endpoint, reads the result titles and snippets, and sends those results to Pollinations or Gemini to compose an answer. IRA does not fetch every linked page. The search provider and the AI provider may log the query and results under their own policies.

### 3.5 No developer backend

IRA does not operate a user account system, a content store, or an analytics backend. The developer does not receive your prompts, audio, screen content, notifications, contacts, messages, or call data through an IRA server. The app sends cloud requests directly from your device to the providers listed above. Android speech recognition and text-to-speech are provided by the platform and are not controlled by IRA.

## 4. How IRA uses information

IRA uses information to:

- detect the wake word and understand commands;
- perform the action you requested;
- speak replies and confirmations;
- read the screen or notifications when you ask;
- resolve contacts, place calls, and send messages when you ask;
- remember notes and preferences you choose to save;
- perform web research when you ask;
- maintain app state, security, and basic diagnostics;
- comply with applicable law and enforce the Terms.

IRA does not use your information for advertising, profiling, or sale.

## 5. Legal bases for processing

If you are in the European Economic Area, the United Kingdom, or Switzerland, IRA relies on these legal bases:

- **Consent:** microphone, contacts, phone, SMS, accessibility, notification access, overlay, and other Android permissions you grant. You can withdraw consent by revoking the permission or uninstalling the app.
- **Contract:** processing needed to provide the app and the features you request under the Terms.
- **Legitimate interests:** keeping the app secure, preventing abuse, and maintaining basic reliability, where those interests are not overridden by your rights.
- **Legal obligation:** processing required by applicable law.

If you are in India, IRA processes personal data on the basis of your consent and, where applicable, legitimate uses permitted under the Digital Personal Data Protection Act, 2023. You can withdraw consent by revoking permissions, clearing app data, or uninstalling the app.

## 6. Sharing and disclosure

IRA does not sell your personal data. IRA does not share personal data with advertisers or data brokers.

IRA may disclose information:

- to the cloud and search providers listed in this Policy when you use those features;
- to your device manufacturer, carrier, messaging app, or other apps you choose to interact with, as part of normal Android operation;
- if required by law, legal process, or a valid government request;
- to protect the rights, safety, or security of users, the developer, or the public;
- in connection with a merger, acquisition, financing, or sale of assets, subject to appropriate safeguards.

IRA does not have a developer-operated server that stores your content, so there is no IRA server-side account to delete. You may need to exercise rights against a cloud provider separately for data that provider retains.

## 7. Retention

| Data | Where it is kept | How long |
| --- | --- | --- |
| Audio | Device memory and the Android speech service | IRA does not store audio. The speech provider may retain it under its own policy. |
| Transcripts and conversation | Device memory | Until the app process ends or the conversation is reset |
| Notification strings | Device memory, last 20 | Until the app process ends |
| Screen nodes | Device memory | Until replaced, cleared, or the app process ends |
| Preferences and notes | Device SharedPreferences | Until you clear them, clear app data, or uninstall |
| Gemini API key | Device SharedPreferences | Until you remove it, clear app data, or uninstall |
| Debug logs | Android system log buffer | Until Android clears the buffer or the device is wiped |
| Cloud prompts and results | Third-party providers | Per the provider's retention policy |
| Downloaded models | App-private files | Until you delete them, clear app data, or uninstall |

Uninstalling IRA removes app-private data under normal Android behavior. Data held by third-party providers is not deleted by uninstalling IRA. Use the provider's controls or contact the provider to exercise rights over that data.

## 8. Security

IRA uses HTTPS for cloud and search requests. App-private storage is protected by Android's app sandbox. Android backup is disabled for IRA.

No method of transmission or storage is completely secure. IRA cannot guarantee that data will never be accessed, disclosed, altered, or destroyed. You are responsible for keeping your device locked, keeping your operating system updated, reviewing app permissions, and removing the Gemini key if you no longer need it.

Do not use IRA on a device you do not own or control, or on a device where you do not have permission to access the screen, notifications, contacts, messages, or calls.

## 9. Your choices and controls

You can:

- freeze IRA to stop the wake service, bubble, screen actions, and notification reading;
- turn off cloud chat on the main screen;
- remove the Gemini API key;
- revoke microphone, contacts, phone, SMS, overlay, accessibility, notification access, and modify-system-settings access in Android Settings;
- change the Android speech recognition or text-to-speech engine where your device allows it;
- clear app data in Android Settings;
- uninstall IRA;
- clear personal notes by clearing app data or uninstalling.

Freezing IRA does not uninstall it and does not remove data already stored. Turning off cloud chat does not turn off Android speech recognition or text-to-speech. Revoking a permission stops the related feature but does not delete data that was already processed.

## 10. Your privacy rights

Depending on where you live, you may have the right to:

- access the personal data IRA holds about you;
- correct inaccurate data;
- delete data;
- restrict or object to processing;
- withdraw consent;
- receive a portable copy of data you provided;
- complain to a supervisory authority.

IRA holds most data only on your device. To exercise access or deletion rights for on-device data, use Android's app data controls or uninstall the app. For data held by a cloud provider, contact that provider. For requests to the developer, use the contact details at the top of this Policy.

### 10.1 India

If you are in India, you may contact the Grievance Officer listed at the top of this Policy. You may also have the right to complain to the Data Protection Board of India after exhausting available grievance channels.

### 10.2 European Economic Area and United Kingdom

If you are in the EEA or UK, you may lodge a complaint with your local data protection authority. You can also object to processing based on legitimate interests and request restriction or deletion, subject to applicable law.

### 10.3 California

If you are a California resident, you may have rights under the California Consumer Privacy Act, as amended, including the right to know, delete, correct, and opt out of sale or sharing. IRA does not sell or share personal data for cross-context behavioral advertising. IRA does not knowingly collect personal data from consumers under 16 for sale or sharing.

## 11. Children

IRA is not directed to children under 13, or under 16 in the EEA, and is not intended for use by children. IRA involves microphone, contacts, calls, messages, accessibility, and notification access. Do not let a child use IRA without adult supervision and consent. If you believe a child has provided personal data to the developer, contact us so we can address it.

## 12. International transfers

Cloud and search providers may process data outside your country. Where required, the provider is responsible for its own transfer safeguards. If you do not want data processed outside your country, turn off cloud features and do not use web research.

## 13. Automated decisions

IRA uses AI models to suggest actions and replies. It does not make solely automated decisions that produce legal or similarly significant effects about you. You remain responsible for reviewing actions, especially calls, messages, posts, settings changes, and any destructive action. IRA refuses some actions, such as payments and file deletion, but its refusals are not a guarantee of safety.

## 14. Do Not Track

IRA does not respond to browser Do Not Track signals because it is an Android app, not a website. IRA does not use advertising trackers.

## 15. Third-party links and apps

IRA can open third-party apps, websites, and system settings. This Policy does not cover those third parties. Review their privacy policies before using them. IRA is not responsible for their content, security, or data practices.

## 16. Changes to this Policy

We may update this Policy. The updated version will show a new effective date. If the changes are material, we will provide a reasonable notice in the app or in the repository before they take effect. Continued use after the effective date means you accept the updated Policy.

## 17. Contact

Questions, requests, or complaints:

**Email:** [privacy email]  
**Postal address:** [Postal address]  
**Grievance Officer:** [Name], [email], [address]

If you are in the EEA or UK, you may also contact your local supervisory authority. If you are in India, you may contact the Data Protection Board of India after using the grievance process above.
