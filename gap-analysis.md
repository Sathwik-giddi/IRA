# VoicePoco: gap analysis vs the original smartphone-agent plan

Date: 2026-10-27. Sources: `voicepoco-handoff.md`, `ToolCatalog.kt`, `ToolExecutor.kt`,
`ConfirmPolicy.kt`, `AndroidManifest.xml`, `doing.md`. Read-only analysis, no code changed.

## 1. Bottom line

The handoff describes a **safe, narrow command router**, not the plan's "proactive agent".
It decides well on ~20 tools, but only **10 of the 23 catalog entries have an executor** that does
anything, and the plan's multi-step outcomes, current-information, vision and smart-home sections
are not started. Several of those sections conflict with the product constraints (fully offline,
270M model, ~5.7 GB RAM) and need an explicit decision, not just more tools.

## 2. Critical finding: the model can pick tools the app silently ignores

`ToolExecutor.execute` handles only: `back, home, scroll, tap, type, open_app, set_volume,
set_alarm, call_contact, send_message`. Everything else falls to `else -> null`, which
`BubbleService` turns into an empty string. So for these tools the model scores as correct in the
harness, but on the phone **nothing happens** (and the plan's rule 12, "never pretend an action
succeeded", is at risk):

`set_wifi, set_bluetooth, set_brightness, set_dnd, set_data, post_social, delete_files,
uninstall_app, pay_money, share_location`

The harness measures decisions only, so the 98% intent number overstates what the user gets.
Also `call_contact` and `send_message` only open the dialer/composer (they never send), and
without `READ_CONTACTS` (not in the manifest) the contact is not resolved at all.

## 3. Plan section by section

| Plan section | Status | Notes |
|---|---|---|
| 1. Primary role / intent inference | Partial | Single-tool routing works. No inference beyond the literal command. |
| 2. Device control and settings | **Mostly missing** | Catalog has wifi, bluetooth, brightness, volume, dnd, data; only volume executes. No hotspot, NFC, location, airplane, screen timeout, refresh rate, dark mode, font size, battery saver, ringtone/vibration, notification management, diagnosis or recommendations. |
| 3. Communication and organization | Partial | Alarm, call (dialer), SMS (composer) work. Missing: timers, reminders, calendar, recurring items, reading/summarizing notifications or messages, translation, dictation beyond `type`. |
| 4. Multi-step outcomes | **Not started** | The model emits one call per utterance. "I'm going to sleep" needs a plan of several calls. |
| 5. Apps and content | Partial | `open_app`, tap/type/scroll via accessibility. No file/photo/contact search, media control, camera, screenshot, share. |
| 6. Internet / current info | **Conflicts with offline** | Weather, news, web are routed to `unsupported`. Plan wants search. Needs a decision (see 5). |
| 7. Camera / vision | **Not feasible as built** | A text-only 270M function-calling model cannot read images. Needs OCR (ML Kit) or a separate VLM. |
| 8. Smart home | Not started | Needs network or local protocol integration; also conflicts with offline-only. |
| 9. Personalization / memory | Not started | Only yes/no confirmation state exists. No preferences, no follow-up context. |
| 10. Troubleshooting | Not started | Needs read access to battery/settings state and a multi-step flow. |
| 11. Privacy and permissions | **Good** | SafetyGuard, refuse 48/48, destructive confirmation. Gaps: confirmations are yes/no keyword matching; no permission-disclosure flow. |
| 12. Execution rules | Partly violated | See section 2 (silent no-ops). Plan says to report what actually happened. |
| 13. Communication style | **Deliberately dropped** | Product rule is "no canned replies", so there is no spoken or text result at all. The plan wants concise confirmations of outcomes. These need reconciling (a model-generated or template-free result message). |

## 4. What Android lets you do (affects the roadmap)

| Capability | Direct API on Android 14? | Practical route |
|---|---|---|
| Wi-Fi / mobile data toggle | No (blocked for apps since API 29) | Open `Settings.Panel` or drive Settings/Quick Settings with the existing accessibility service |
| Bluetooth toggle | Needs `BLUETOOTH_CONNECT`, deprecated path | Same panel / accessibility route |
| Brightness | Yes, with `WRITE_SETTINGS` grant | Settings.System + one-time user grant |
| DND | Yes, with notification-policy access grant | `NotificationManager.setInterruptionFilter` |
| Hotspot, airplane, NFC, location | No | Settings screens via intents / accessibility |
| Timers, calendar events | Yes | `AlarmClock.ACTION_SET_TIMER`, `CalendarContract` insert intent |
| Flashlight | Yes, no permission | `CameraManager.setTorchMode` |
| Read notifications | Yes, with listener grant | `NotificationListenerService` |
| Send SMS silently | Yes, with `SEND_SMS` | Keep intent-with-confirmation as the safe default |
| Delete files / uninstall | Scoped storage / package installer prompts | Open the system UI, never delete silently |
| Pay money | Do not automate | Open UPI app only; plan says confirm payments |

MIUI note from the handoff: adb cannot grant these, so each needs an in-app permission onboarding screen.

## 5. Decisions only you can make

1. **Offline vs current info.** Keep fully offline (drop plan section 6/8 or scope them as "online
   skills" behind an explicit toggle), or allow optional network tools.
2. **Model capacity.** FunctionGemma 270M at ~20 tools already needs a router and few-shot shot
   selection. Going to the plan's ~60+ tools and multi-step output likely needs a larger model
   (the memory gate of 450 MB PSS is already missed at 521 MB) or a two-stage design (route, then fill).
3. **Result messages.** How the app reports outcomes without hardcoded strings.
4. **Gate policy** (handoff section 2): the Wilson gates are unpassable at current bucket sizes.

## 6. Recommended roadmap

**P0 (correctness, small):**
1. Give every catalog tool an executor, or remove the tool/mark `unsupported`. For each tool
   without a real action, return an explicit "not executed" result instead of `""`.
2. Add `READ_CONTACTS` onboarding; keep call/SMS as dialer/composer with confirmation.
3. Harness: add an execution-level check (did the tool have a handler) so intent accuracy stops
   overstating real behavior.

**P1 (high-value device control, no new model needed):**
4. Executors for brightness (`WRITE_SETTINGS`), DND, torch, timer, calendar insert, wifi/bluetooth/data
   via settings panel + accessibility.
5. New tools to match plan section 2: hotspot, airplane, dark mode, screen timeout, battery saver,
   screenshot, media play/pause. Extend `ToolCatalog`, dataset rows (hi-en first) and re-baseline.

**P2 (agent behavior):**
6. Multi-call output ("sleep", "driving", "meeting" routines) as a fixed set of model-selected
   macros built from existing tools; measure with a new multi-step dataset.
7. Notification reading/summary via `NotificationListenerService`, then follow-up context.

**P3 (needs decisions above):** web search, smart home, camera/OCR, memory/personalization.

## 7. Not verified

I did not run the app or harness. Statements about runtime behavior come from reading the code;
the A11y service state on the device is unknown (the handoff says it resets on every install).
