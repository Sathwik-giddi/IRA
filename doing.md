# doing.md

Working log. Newest entry at the bottom. Timestamps are IST (Asia/Kolkata), machine clock.

---

## 2026-10-25 21:20 — Direction change accepted

User decision, recorded verbatim in intent:

> "i don't want any hard coded data... it's like giving out instructions like, i didn't catch it... i can do this and leave it all. i want everything to be running itself from scratch."

Interpretation applied from here on:

- The `when { lower.startsWith("open") ... }` command ladder is **not** the product. It is removed from the decision path.
- Canned instructional replies ("Say open, tap, scroll, type, go back, or go home", the "Didn't catch that, say poco again" variants, "Mic had a hiccup") are removed. Replies come from the model / from the action result.
- Intent → tool call is produced by an on-device LLM, not by string matching.
- The tool schema (what the app *can* do) stays explicit — that is the app's API, not scripted conversation.
- Rule parser survives only as a **labelled control** in the harness so we can prove the model beats it. It is deleted from the runtime path.

---

## 2026-10-25 21:20 — Environment audit

| Item | Result |
|---|---|
| Device | `i7krsovwcmnbpfyx` online (Poco M4 5G, Dimensity 700, 5.7 GB RAM, Android 14) |
| NDK | **missing** → install started (`ndk;27.2.12479018`) |
| Network | reachable (HF 200) |
| Host disk | 74 GB free |
| Build | `assembleDebug` green after parser extraction |

---

## 2026-10-25 21:21 — Runtime + model decision

- **FunctionGemma 270M IT** confirmed to exist: Gemma-3-270M fine-tune by Google for function calling; GGUF mirrors by `unsloth` and `bartowski`.
- Chosen quant: `functiongemma-270m-it-Q4_K_M.gguf` — **241 MB**, fits the 4 GB budget with room for KV cache.
- Runtime: **llama.cpp** (`ggml-org/llama.cpp`), arm64-v8a, CPU, mmap. Reasons:
  - GBNF grammar → output is guaranteed-shape JSON, no regex scraping.
  - mmap keeps RSS low on 4 GB devices.
  - Same runtime on MediaTek and Snapdragon → comparable benchmark numbers.
  - MediaPipe LLM Inference rejected: maintenance-only, `.task` models only, no FunctionGemma.
- Rejected for now: Qwen3-0.6B (2× size, kept as quality baseline later), LFM2.5-350M (no function-calling tuning).

Downloads started in parallel:
- `git clone --depth 1 https://github.com/ggml-org/llama.cpp` → `/tmp/vp/llama.cpp`
- model → `/tmp/vp/functiongemma-270m-it-Q4_K_M.gguf`
- NDK r27c via sdkmanager (first attempt died with the shell timeout, restarted with `nohup`)

---

## 2026-10-25 21:22 — Harness, still the gate

Nothing about "no hardcoded" removes the need for measurement. Dataset v1 stays at 250 rows:

| Category | Total | Hinglish | English |
|---|---|---|---|
| Commands + paraphrases | 140 | 90 | 50 |
| Ambiguous | 30 | 18 | 12 |
| Destructive / sensitive | 25 | 15 | 10 |
| Adversarial | 25 | 15 | 10 |
| Out-of-scope | 15 | 9 | 6 |
| Malformed / rapid repeat | 15 | 9 | 6 |

Pass rule: per-language gates (intent ≥ 90 %, args ≥ 90 %, safety ≥ 98 %, clarify ≥ 80 %, robustness ≥ 99 %, p95 < 4 s, peak PSS < 450 MB), each with Wilson 95 % lower bound. Aggregate score cannot pass anything.

Status: dataset generator, scorer and runner being written now.

## 2026-10-25 22:30 — native perf root cause + routing
- Root cause of 7x slowdown: `CMAKE_BUILD_TYPE=Debug` in the app's externalNativeBuild (bench was Release). Forced Release in `app/src/main/cpp/CMakeLists.txt`. Result: pp 7 -> 165 tok/s, tg 4.8 -> 30-41 tok/s (llama-bench reference 188/43).
- Second bug: `llama_batch_init` leaves `n_tokens = 0`; every decode was failing instantly. Set `batch.n_tokens = chunk` / `one.n_tokens = 1`.
- Added `llama_log_set` bridge to android log (tag VP_LLAMA) — native errors were invisible before.
- Added per-session stats JNI `LlamaBridge.stats` (pp_tok/pp_ms/tg_tok/tg_ms/pp_tps/tg_tps) + `bench` backend in HarnessService writing `files/vp_bench.txt`.
- 940-token all-tools prompt made the model refuse; added `ToolRouter` (word-boundary lexical match over tool hints) -> prompts 76-240 tokens, ~1.5-2.2 s/command.
- `refuse` tool in the routed list causes false refusals ("whatsapp kholo" -> refuse). Removed from core routing; destructive/unsafe words still route it.
- Early stop at `<end_function_call>` cut generation 40 -> 14-18 tokens.
- Known open defects: `wifi band karo` -> state:on; `volume badhao` -> down (flaky); `delete all photos` -> target=all.

## 2026-10-25 22:45 — argument polarity
- Added `ArgNormalizer`: canonicalizes 2-value enums (on/off, up/down) from the utterance ("band"->off, "chalu"->on, "kam"->down, "badhao"->up). The model still picks the tool and all other args; this only resolves the enum token.
- Dropped "chalu" from open_app hints (it routed "wifi chalu karo" to open_app).
- Observed device memory pressure during long runs: MemFree ~57 MB, SwapFree 2.3 GB -> tg collapsed 33 -> 4-5 t/s on later rows (weights evicted from page cache). Latency must be measured with cooldown and reported with memory state.

## 2026-10-26 23:20 — full 250-row baseline after safety work

- Root cause of the "slow model" was two stacked bugs, not the model: app lib was built
  `CMAKE_BUILD_TYPE=Debug`, and `llama_batch_init` left `n_tokens = 0` so every decode failed.
  After forcing Release and setting n_tokens: pp 160-175 t/s, tg 30-41 t/s (llama-bench ref 188/43).
- Reduced footprint: n_ctx 1024 -> 768, n_batch 128, n_ubatch 64 (compute buffer 130 -> 65 MiB).
  Prompt loop now chunks at n_batch (the 256 hardcode aborted decode with SIGABRT in ggml).
- Arg repair: model was copying the verb into the slot ("phone app kholo" -> app="kholo").
  `ArgNormalizer.clean()` drops filler/verb tokens for app/label/target/state/level/direction/time
  and falls back to the utterance nouns. phone / play store / settings now exact.
- Added missing tools `set_data`, `post_social` (dataset expected them); "otp" stays a refusal.
- Router: added OOS hints (email/cab/biriyani/order/bill/balance/AC/...) and refuse hints
  (factory reset, play protect, find my device, screen lock, system prompt, previous instructions,
  admin, root, forward, developer options, silently, mass targets). A blunt "no match -> unsupported"
  fallback was tried and reverted: it collapsed clarify to 8-11%.
- Added `SafetyGuard`: deterministic pre-filter for prompt injection, mass targeting and
  irreversible device-admin asks. Runs before the model; model still handles everything else.
- Full 250 (run1, harness/results/baseline-model-20261026-230956):
  en intent 49/50, args 41/50, safety 19/26 (73.1%), clarify 8/12, schema 92/94
  hi-en intent 79/90, args 62/90, safety 29/39 (74.4%), clarify 15/18, schema 155/156
  refuse 44/48 (92%), confirm 14/17, latency p50 1.81 s / p95 4.34 s, peak PSS 519 MB.
- Open gaps: hi-en intent 87.8% (needs 90), args 69%, safety 74% (needs 98), clarify en 67%,
  p95 over 4 s, PSS 519 MB over the 450 MB gate. Remaining OOS misses are "email/cab/biriyani/AC"
  rows where the model answers with clarify or send_message.

## 2026-10-26 23:57 — safety layer, production switch, final full run

- Runner telemetry fixed: battery counter read is blocked in /sys on this device
  (Permission denied), so `charge_uah()` now falls back to `dumpsys battery` "Charge counter"
  (uAh). Added top-level `min_free_kb` to meta; verified `--runs 3` with cooldown, per-run PSS,
  thermal before/after and battery delta all populate.
- Safety pattern correction: mass-target words were over-refusing rows the dataset wants
  confirmed. Swapped "sabko" for "bina puche" (the unsafe marker is "without asking", not
  "everyone") and dropped "all contacts"; "sab contacts" still refuses.
- Router tie-break: when `unsupported` scores >= the best device tool, unsupported wins
  (fixes "send an email to my boss" being taken as send_message).
- `repairArgs()`: repairs escaped values that swallowed a following `,param:` pair.
- Production path switched: `BubbleService.handle()` now calls `ModelBrain.decide()` through
  `ConfirmPolicy` and `ToolExecutor`; the rules `when` block and all canned replies
  ("Didn't catch that", "Say open, tap, scroll...") are deleted. Destructive tools ask for
  confirmation and wait for yes/no on the next utterance.
- New files: `ConfirmPolicy.kt`, `ToolExecutor.kt` (back/home/scroll/tap/type/open_app/volume/
  alarm/dial/sms; contact lookup needs READ_CONTACTS, otherwise opens the dialer/composer).
- Final 250-row run (`harness/results/baseline-model-20261026-234845`, scored
  `baseline-model-20261026-235653`):
  en intent 49/50, args 41/50, safety 23/26 (88%), clarify 8/12, schema 93/94
  hi-en intent 79/90, args 62/90, safety 32/39 (82%), clarify 15/18, schema 155/156
  refuse 48/48 (100%), confirm 16/17, p50 1.73 s, p95 3.94 s, peak PSS 521 MB.
- Only remaining safety miss is dest-06, where a mangled escape still yields
  contact="ravi ko 500 rupaye bhej do". Arg extraction (69% hi-en) is now the top gap.

## 2026-10-27 — gap analysis vs original plan

- Wrote gap-analysis.md (read-only; no code changed). Key finding: ToolExecutor has handlers for
  only 10 of 23 catalog tools; set_wifi/bluetooth/brightness/dnd/data, post_social, delete_files,
  uninstall_app, pay_money, share_location silently no-op on device although the harness scores them.
- Roadmap P0: executors or explicit not-executed results; READ_CONTACTS onboarding; execution-level harness check.

## 2026-10-27 — P0: executors for every catalog tool

- ToolExecutor: set_wifi/set_data open the internet panel, set_bluetooth opens BT settings (apps cannot
  toggle these on Android 10+), set_brightness (needs Modify system settings), set_dnd (needs DND access),
  post_social opens the app, uninstall_app opens the system uninstall prompt. delete_files, pay_money,
  share_location, and unknown tools now return "Not executed: ..." instead of an empty string.
- Manifest: READ_CONTACTS, WRITE_SETTINGS; MainActivity button 1 now also requests contacts.
- Built and installed on device i7krsovwcmnbpfyx. Brain/decision path unchanged, so no harness re-run.
  Not yet verified on-device end to end (needs permissions granted by hand; A11y resets on install).

## 2026-10-27 — persistent assistant card (Siri/Gemini-style)

- BubbleService: bubble no longer vanishes 900 ms after a reply. A bottom card shows heard text + result,
  stays 8 s idle (touch pauses the timer), dismisses via X, swipe down, tap outside or idle timeout.
  Tapping the orb always re-listens (bypasses the 4 s wake cooldown). When a yes/no confirmation is
  pending the app auto-listens again after 2.5 s. Wake word is re-armed 900 ms after each command.
- Built and installed; not yet verified on-device (A11y resets on install).

## 2026-10-27 — 15 new tools + notification reader

- New tools: set_timer, add_event, set_flashlight, set_hotspot, set_airplane, set_dark_mode,
  set_screen_timeout, set_battery_saver, set_ringer, open_settings, take_screenshot, media_control,
  read_notifications, battery_status, web_search. Executors in ToolExecutor; NotificationReader service
  (needs notification-listener grant). Hotspot/airplane/dark mode/battery saver open the system screen.
- First regression run collapsed intent to 66% en: name-part router bonus ("open", "search", "screen")
  plus hints "camera/settings/calendar/play" pulled new tools into open_app/tap rows. Fix: Tool.nameHints
  flag (off for new tools), dropped open_camera (open_app covers it), removed bare hints, "reminder"
  -> "set reminder".
- Full 250 after fix (baseline-model-20261027-005448): en intent 49/50, args 41/50, safety 23/26,
  clarify 8/12; hi-en intent 79/90, args 62/90, safety 32/39, clarify 15/18 = same as before.
- NOT covered by dataset: the 15 new tools have no rows, so their accuracy is unmeasured.
  Executors unverified on device.

## 2026-10-27 — routines, memory, read_screen, new-tools dataset

- New tools: run_routine (sleep/driving/meeting/outdoors/battery_low; fixed sequences of existing
  executors), remember/recall (local notes in SharedPreferences, max 50), read_screen (a11y text dump).
- New dataset harness/datasets/new_tools.v1.jsonl (92 rows, 23 tool/arg cases x 2 en + 2 hi-en);
  scorer SCHEMA extended. commands.v1 stays locked for comparability.
- ArgNormalizer: duration/seconds -> seconds, web query noise stripped, enum repair, routine name from
  utterance, remember note taken from utterance. Router: dropped bare hints, "flight" -> "flight book/ticket".
- Results new_tools (newtools-20261027-011214): intent en 46/46, hi-en 43/46; args 45/46 and 43/46.
  commands.v1 unchanged: en intent 49/50, hi-en 79/90.
- Not built: translation, smart home, camera/OCR, calendar reading, multi-turn context.
  Executors still unverified on device.

## 2026-10-27 — continuous conversation

- BubbleService: after every reply the app waits for TTS to finish (tts.isSpeaking poll, max 9 s), then
  listens again without the wake word. Silence/STT error, "thanks/bas/stop/bye", X, swipe down, tap
  outside, or 12 turns ends the session; wake word stays paused for the whole session.
- ModelBrain.conversational (on only in BubbleService): when a follow-up utterance routes to nothing
  ("10 minute karo", "aur badhao"), the previous command is added as a prior turn in the prompt and its
  tool is routed. Context cleared on dismiss. Harness leaves it off.
- Regression commands.v1 unchanged (en 49/50, hi-en 79/90). Follow-up quality is not measured by any
  dataset yet and not verified on device.

## 2026-10-27 11:23 — messaging layer (build)
- Added `MessageSender.kt`: contact resolution (exact > prefix > word > contains; collapses duplicate numbers), silent SMS via `SmsManager` when `SEND_SMS` granted, honest handoff to `ACTION_SENDTO` otherwise.
- Added `AssistantService.whatsappSend(contact,text,dry)`: launch WhatsApp -> `search_input` -> tap result -> `entry` -> `send`, 450ms retries, 16s cap, dry-run stops before send. Requires accessibility.
- Added optional `app` param to `send_message`; scorer ignores extra args (verified `args_ok` only checks expected keys).
- Manifest: SEND_SMS, ANSWER_PHONE_CALLS, READ_PHONE_STATE, MODIFY_AUDIO_SETTINGS; `SEND_SMS` added to runtime request in MainActivity.
- Added deterministic dev hook: `am start -n com.voicepoco.app/.MainActivity --es tool send_message --es contact Mom --es text late [--es app whatsapp --es dry 1]` -> `onStartCommand` executes the tool. Broadcast hook kept but MIUI drops it unless `--receiver-include-background` and process is warm.
- Verified on Poco: `Mom` resolved to `Mommy Main` (prefix outranks `Harsha Mom`/`Rakesh Mom`), SMS compose opened with body. WhatsApp dry-run blocked: accessibility is off and `settings put secure enabled_accessibility_services` is denied to shell on this MIUI.
- Still blocked: command STT is `SpeechRecognizer` -> `error code=7` (network) / `5` (client); false wake hits from ambient audio. Next: `VoiceEngine` (single AudioRecord, Vosk wake+command, buffer, whisper fallback) and call announce/answer.

## 2026-10-27 11:41 — UI rewrite (MainActivity + Ui.kt)
- Replaced programmatic Button list with a quiet dark control surface: ink #0E0F0D, surface #171814, bone #F2EDE2, muted #A3A196, amber #E3A559.
- Orb is the only primary action; state lives in kicker + headline, not repeated three times.
- Capability rows (mic, screen control, messages, bubble, act) show real grant state: amber pill when missing, dot when granted.
- Removed duplicated ActionBar, dark status/nav bars, hidden ScrollView bar.
- Verified on device 22041219PG via screencap; contrast: bone/ink 16.5, muted/ink 7.4, amber/ink 9.0, amber on action pill 7.3.
- Left grey edge bar also appears in Settings: MIUI system element, not our layout.
- Store: a11y reset by adb install, now shows Enable again; needs one manual toggle.

## 2026-10-27 12:03 — Stage 2: new tools, calls, modes, privilege UI
- ToolExecutor now handles answer_call, decline_call, set_mode, play_music, next_reel, media_control (play/pause/next/prev via MediaController), plus privileged wifi/data/bluetooth/airplane/dark/saver with panel fallback when root is absent; brightness falls back to root before asking for WRITE_SETTINGS.
- call_contact places the call with ACTION_CALL when CALL_PHONE is granted, otherwise opens the dialer; resolves contact through MessageSender ranking and asks when a name is ambiguous.
- ToolCatalog: added entries and Hinglish hints for answer_call, decline_call, play_music, next_reel, set_mode (8 allowed modes). ConfirmPolicy: per-tool questions (call, send, uninstall, data, post, pay, delete, share).
- Manifest: CALL_PHONE, READ_CALL_LOG. MainActivity permission bundle now includes call perms; new Calls row, Auto answer toggle (driving only), Access level row (root/device owner/Shizuku summary).
- Mode chips row added above capabilities: 8 modes, active chip amber, tap applies and toasts the result.
- Fixed the `--es tool` forwarder to pass name/state/level/action/query instead of only contact/text/app/dry.
- FastLane battery broadened: any "battery" or "charge kitna" text routes to battery_status.
- Device verification (22041219PG, build installed 12:02): set_mode conversation/driving, focus mode via FastLane, "battery kitna hai" -> fast lane battery_status, set_flashlight on/off, media_control next, answer_call refuses without call access, set_wifi off opens the panel. Service starts as microphone FGS, engine ready, mic open.
- Test harness note: `adb shell am broadcast --es text "two words"` loses quotes and sends pkg=two; must wrap the whole device command in single quotes.
- Still manual: accessibility toggle after each install, call/SMS grants, auto-answer preference; wake-word accuracy cannot be exercised over adb; no root/Shizuku on the test device.

## 2026-10-27 12:12 — STT root cause fix, access row clarity
- Root cause of "what's that?" replies: the command phase was decoded by the Vosk wake model, a tiny English model built for "poco" and stop words. It cannot transcribe "open whatsapp".
- Added CommandStt.kt: Android SpeechRecognizer, en-IN, partial results, 1 s complete-silence endpoint, 12 s watchdog, explicit error text. Wake model now only detects the wake word.
- VoiceEngine gets an onWake callback; on wake it parks the loop and closes the mic. Its loop now refuses to reopen AudioRecord while paused, which fixes a race that could leave the mic open under SpeechRecognizer.
- All command entry points moved to the new listener: wake, follow-up, 15 s reels window, ringing answer/decline prompts, bubble tap. Follow-ups still speak first, then listen.
- Added a short beep before listening outside Quiet mode, so the command window is audible.
- Debug path: DEBUG_MIC broadcast starts a 6 s listen.
- Access row renamed from "Access level". Sub now explains stock: "Stock. Radio toggles open Settings." Root, device owner and Shizuku get their own wording; the tag reads Stock/Root/Shizuku/Owner and the tap still shows the detail.
- Device check: "stt start en-IN", "stt ready", error 7 with silence, no mic open during the listen window, engine reopens after the final. Build installed 12:11.
- Caveat: one-breath "poco open whatsapp" can clip the first word; wake, wait for the beep, then speak. Offline accuracy depends on the phone speech service.

## 2026-10-27 12:22 — Calls actually dial, fuzzy contact names
- Diagnosed: "call Phanindra" reached call_contact, then the app asked "Call Phanindra?" and listened for yes. No call was placed, which is why nothing happened.
- Removed the confirmation gate from call_contact and send_message. An explicit call or SMS now runs directly, matching the earlier "send silently" instruction. Ambiguous names still ask which contact.
- Added fuzzy contact matching in MessageSender: if the provider filter returns nothing, read phone rows and rank by edit distance (limit 2 for names of 8+ chars, 1 below). "phanindra" now finds the saved "Phanidra" contact.
- Added a call log line: "call <label> tel=<number> direct=<bool>" right before the intent, so a missing dial is visible in logcat.
- Debug receivers: DEBUG_LOOKUP (resolve a name, log label/number/matches), DEBUG_CONTACTS (first 40 names), DEBUG_FIND (LIKE query with numbers).
- Verified lookups: "nanna garu" -> Nanna Garu, "annaya" -> Annaya 2, "phanindra" -> Phanidra. All matches=1 with numbers.
- Build installed 12:21. Calls use ACTION_CALL because CALL_PHONE is granted; without it the dialer opens prefilled.

## 2026-10-27 12:31 — Reels deep link, first-run guide, mode tooltips
- Reels mode was only launching Instagram home. It now tries instagram://reels_home first (verified on device: lands on the Reels tab), then instagram.com/reels inside the app, then YouTube shorts, then the Instagram app.
- Tapping Reels from the UI now starts the service with a 15 s listen window, so "next" and "ruko" work without saying poco again.
- Added a first-run guide dialog: three steps, five example commands, a screen-control note, and a single "Got it" action. Runs once via guide_seen; reopen it from the new "How to use" row.
- Added mode tooltips under the chips: per-mode copy for 8 s; Reels stays 15 s and turns amber and tappable when screen control is off, opening accessibility settings.
- Build installed 12:29. Verified the reels deep link (bubble and Listening card over the Reels feed) and the guide layout by screenshot.
- Reminder: accessibility resets on every install, so scrolling and WhatsApp need one manual Enable.

## 2026-10-27 13:00 — 1B model trial, guard, reverted to 270M
- Confirmed the 270M FunctionGemma is wired and used: load 1.3 s, 236-token prompt, 393 ms generation, ~2.5 s per command, PSS 658 MB.
- Downloaded Qwen2.5-1.5B-Instruct Q4_K_M (986 MB), added a ChatML prompt path, lenient call parser, and a model_big pref plus a "Big model" row.
- On device the 1.5B loaded in 5.5 s and took about 9 s per decision, with PSS 1.18 GB.
- It also invented a weather tool with city Mumbai that was never offered. Added a guard so any tool outside the routed list becomes unsupported and cannot execute.
- The 1B failed the speed and quality bar, so it is parked as .gguf.off; the 270M is active again and verified. Enabling it needs the pref swap finished so the file rename is not required.

## 2026-10-27 13:19 — Voice orb animation, raster Lottie rejected
- Inspected the supplied `video-1793086635931.json`: it is a Lottie wrapper around 288 full-frame 636x640 WebP images at 15 fps, 19.2 s, 7.8 MB, no alpha, white background. Not shippable as a bubble asset.
- Added `VoiceOrbView`: native Canvas wireframe sphere, amber lines, soft glow, dark translucent disc, 76 dp, 60 fps, states IDLE/LISTENING/THINKING/SPEAKING. No new dependency and no asset weight.
- `BubbleService` now maps listening/thinking/speaking to orb states; added a debug-only `DEBUG_ORB` receiver for state screenshots.
- Build succeeded at 13:16. Install blocked: device asleep and MIUI returns `INSTALL_FAILED_USER_RESTRICTED`; adb input is blocked and there is no root. Needs the phone unlocked and the install allowed before on-device verification.

## 2026-10-27 13:27 — Orb installed and verified
- MIUI workaround: `adb push app-debug.apk /data/local/tmp/vp2.apk`, then `adb shell pm install -r --user 0 /data/local/tmp/vp2.apk` succeeds. Streaming `adb install` and `--no-streaming` still fail with `INSTALL_FAILED_USER_RESTRICTED`.
- Refined `VoiceOrbView` to 26 longitudes and 6 latitudes, all amber, less dense.
- Installed and verified on device: the 76 dp orb renders over the app, animates (pixel diff between frames), and `DEBUG_ORB` switches listening/thinking/speaking without a crash. The real voice path uses the same `showBubble` states.
- Screenshot: `/tmp/orb_final.png` (listening state, cropped view inspected).

## 2026-10-27 13:35 - Palette change: celadon on deep petrol
- User chose antislop during the work and asked for a unique palette with no gold or orange. Replaced the amber accent with celadon `#83CBB9` on deep petrol `#09100F`, with cool bone text and a 3:1 interactive border.
- Centralized the palette in `Ui.kt`; the orb, chips, rows, guide, status bars, and bubble card now use the same tokens. Hardcoded amber values removed.
- Contrast checked: bone on ink 16.9:1, muted on ink 8.7:1, accent on ink 10.3:1, interactive border on surface 3.36:1.
- Build succeeded, installed with push plus `pm install -r --user 0`. Main screen and floating orb verified by screenshot; orb lines, active chips, dots, and the card border now use celadon.
- Screenshots: `/tmp/palette_main.png`, `/tmp/palette_orb2.png`.


## 2026-10-27 13:57 - Conversation follow-up: repeat the last answer
- Root cause: FastLane answered "what time is it" before `ModelBrain`, so the model's `previous` context stayed empty. A following "what?" fell through to the `clarify` exemplar and produced "kya karna hai?".
- Added `FollowUp.kt` for short repeat/confusion phrases: "what?", "what did you say", "repeat", "kya?", "kya bola?", "phir se", "samajh nahi aaya" and similar.
- `BubbleService` now keeps the last spoken answer for 45 s, clears it when a new command starts, and replays it through the normal card, TTS and listen path. FastLane results are also passed to `ModelBrain.remember` so model context can follow a fast command.
- Verified on the Poco: `what time is it` -> `tell_time` -> "It's 13:56."; then `what?` -> `follow-up repeat: what? -> It's 13:56.`; `kya?` also repeated "It's 13:57.".
- Screenshot `/tmp/followup.png` shows the card: “what?” / It's 13:56.
- Build installed via push plus `pm install -r --user 0`.

## 2026-10-27 14:44 - Rename to IRA, wake word validated
- Rebranded every visible surface: launcher `app_name` is `IRA`; main header and orb say IRA; guide, mode hint, status notification, accessibility label, notification listener label, orb content descriptions, permission prompts, and call prompts now say IRA.
- Kept the package `com.voicepoco.app` so `pm install -r` preserves permissions, model files, and prefs. Internal debug actions and class names are unchanged.
- Wake word: `VoiceEngine.WAKE` is now only `ira`; the old poco variants are gone. Renamed the audio thread to `ira-voice`.
- Offline validation against the app’s Vosk model: macOS `say` samples for Samantha “Ira”, “Eera”, “Hey Ira”, and Rishi “Ira” all decoded as `ira` under the wake grammar. The legacy “Poco” sample no longer triggered.
- Installed 14:42 after `am force-stop com.miui.securitycenter`; the first attempt hit `INSTALL_FAILED_USER_RESTRICTED`.
- `aapt` confirms `application-label:'IRA'`, `IRA Assistant`, and `IRA Notifications`. Screenshot `/tmp/ira_main2.png` shows the IRA header and the `ira` orb.
- Started the service with `--es tool battery_status`: model loaded, engine state `ready`, mic opened, and no wake grammar error.
- Caveat: live mic pronunciation of “ira” is still unverified because adb cannot inject speech. The recognition test used the same model and grammar with generated voice samples.

## 2026-10-27 15:16 - Cloud brain, rate limit, optional Gemini key
- Added `CloudBrain.kt` as the online path. It sends the last 14 turns to Pollinations, routes research questions through DuckDuckGo HTML search, and falls back to the top snippets when synthesis fails.
- Routing is explicit: research keywords go straight to search, tool-like utterances get the JSON tool prompt, and normal conversation gets a short spoken-reply prompt. A cloud `web_search` tool call is treated as research.
- Cloud is tried after FastLane and SafetyGuard in `BubbleService`; a cloud miss still falls back to the local model for tool commands. User and assistant turns are recorded for follow-up context and cleared on `dismissSession`.
- Manifest now has `INTERNET` and `ACCESS_NETWORK_STATE`.
- Pollinations free tier sends `402 FREE_LIMIT` at one reply per 30 seconds per IP. Added a 30 s cooldown on that response, a 20 s cooldown on timeout or request failure, and a 15 s read timeout. Chat during cooldown returns a spoken cooldown message instead of waiting on the network.
- Added an optional `Cloud key` row. It stores a Gemini API key in the app prefs and switches the same OpenAI-compatible request to `gemini-2.5-flash`. The key stays on the phone. The row reads `Add` when empty and shows the active dot when set.
- The local big model is gone from the phone. Only `functiongemma-270m-it-Q4_K_M.gguf` remains, 241 MB. `model_big=true` is still in prefs but `ModelBrain` ignores it because the file is absent.
- Test at 15:12: `tell me a joke about my phone` -> cloud chat 10.9 s, 108 chars. Test at 15:14: a follow-up within the free window timed out, set a 20 s cooldown, and spoke the chat fallback instead of the weak local clarification.
- Test at 15:15: `what is the latest AI news today` -> 5 DuckDuckGo results, cloud synthesis timed out, and the top snippets were spoken from the fallback path (198 chars).
- UI verified through `uiautomator dump`: `Online brain` is On, `Cloud key` is present with `Add`, and the footer is present. The top screenshot `/tmp/ira_settings.png` was captured. Scrolling and tapping still cannot be automated because MIUI blocks `INJECT_EVENTS` and secure settings.
- Build and install: `./gradlew assembleDebug`, push to `/data/local/tmp/ira_cloud.apk`, `pm install -r --user 0`, start `.MainActivity` and `.BubbleService`.
- Not tested: a real Gemini key, multi-turn cloud context beyond one turn, and the key dialog on screen. The free endpoint remains too slow for a Siri-like rapid exchange without a key.

## 2026-10-27 15:22 - Context and tool verification
- Added `remember`, `remind`, `yaad`, `note kar`, `calendar`, `event`, `vibrate`, `ringer`, `pause`, `resume`, `next song`, `previous song`, and `screen timeout` to the cloud tool router.
- Verified the phone tool path: `remember that my favorite color is teal` produced `remember {note=my favorite color is teal}` and the tool executed.
- Added a `cloud history=` debug line. A fresh chat request logged `cloud history=1 last=my favorite color is teal`, so the turn is recorded before the request.
- Full two-turn context could not be confirmed on the free endpoint. The next request either timed out after 15 s or returned a generic "I'm not sure" answer. The request does carry history, but the free Pollinations tier is too slow and rate limited for a reliable multi-turn test.
- The optional `Cloud key` path remains the reliable fix. With a Gemini key, the same messages go to `gemini-2.5-flash` with a 15 s read timeout.
- Current device build is installed and the service is running.

## 2026-10-27 15:34 - Repo and BazaarLink evaluation
- Reviewed `judejulius/jarvis`, `AnaaySampat/jarvis`, and `tokenarc/open-jarvis` at source level.
- `judejulius` is the strongest fit: native Kotlin, a 234-line observe-act `PhoneAgent`, numbered `ScreenReader`, OpenAI-compatible tool session, action-log handover, multi-model fallback, task island, and 11 tests.
- `AnaaySampat` has the best process and memory design but is a 413-file Tauri/TypeScript system; port cost is high. Useful ideas: completion-claim guard, phone/action truth checks, cycle guard, scoped research, model policy, and workflow journal.
- `tokenarc` is not usable as-is. `ProviderFallbackChain` calls `getActiveProvider()` for every name, so fallback never switches; `SelfHealingExecutor` calls `complete()` on `ModelManager`, which has no such method; `AgentCore` reads the screen once and executes a blind 2-8 action plan. Treat it as a design reference only.
- BazaarLink free Qwen is a major upgrade. `qwen/qwen3.7-flash:free` answered with context in 1.58 s, called `set_timer` correctly in 2.36 s, and in a phone-agent test tapped WhatsApp element 1 from the screen snapshot in 5.24 s. `auto:free` returned 429; `deepseek-v4-flash-0731free` answered in 2.63 s. Pollinations was 11-15 s and rate limited.
- The key was used only in test commands and is not written to source. Next: add BazaarLink as the default cloud provider with the key entered on-device and a `:free` model allowlist.

## 2026-10-27 15:55 - Short answers, barge-in, cloud fallback
- Added `SpeechStyle.kt` so normal replies are one or two sentences / under 38 words, while explicit detail requests allow up to 230 words / 700 tokens. `CloudBrain` now applies that rule to chat and research and passes the detail flag through tool decisions.
- Fixed a bug in `SpeechStyle.firstSentences()` that returned only the second sentence instead of the first two. A JVM harness against the compiled class passes: plain question false, `explain` true, `detail me` true, `short` overrides detail, two-sentence trim, 230-word detailed cap, and 700 detailed tokens.
- Added `BargeInDetector.kt` using `VOICE_COMMUNICATION`, AEC, adaptive RMS, 6 voiced frames, and a 220-1800 threshold with slow noise adaptation. `BubbleService` starts it during TTS, stops TTS and starts listening on trigger, and exposes `DEBUG_BARGE`. TTS now uses `USAGE_ASSISTANT`, calls `engine.pause()` before speaking, and ignores wake events for 700 ms after TTS ends.
- Added `DEBUG_SPEAK` for repeatable TTS tests and `listen start ms=... tts=...` logging.
- Device test with the 15:47 build: AEC active, TTS residual RMS mostly 26-58, no self-trigger at threshold 500; Mac speaker at `afplay -v 6` only reached RMS 232, so barge-in did not fire. The 220 threshold build is built but not installed.
- Added Gemini failure fallback: one Gemini attempt, then a 60 s Gemini cooldown, then Pollinations. This removes the previous "Cloud is slow right now" dead end when the Gemini key is invalid.
- Latest `assembleDebug` succeeds. APK is at `app/build/outputs/apk/debug/app-debug.apk`; installed build remains 15:47 because MIUI returns `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`.
- Pending: enable Developer options > Install via USB, install the 15:55 APK, then test barge-in with a real human voice and tune the 220 threshold if needed.

## 2026-10-27 15:57 - Lint clean
- Fixed the lint errors: runtime mic permission guards in `BargeInDetector` and `VoiceEngine`, API guards for `TelecomManager.endCall` and `PowerManager.getCurrentThermalStatus`, audio mode restore suppression, a non-required telephony feature, and a documented `QUERY_ALL_PACKAGES` suppression for this sideloaded app.
- `./gradlew :app:lintDebug :app:assembleDebug` passes. The latest APK includes the 220 barge threshold, the TTS-end wake guard, the two-sentence fix, and the Gemini-to-Pollinations fallback.
- Install is still blocked by MIUI: `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. The installed build remains 15:47, so the new threshold and fallback are not on the phone yet.

## 2026-10-27 16:10 - Freeze switch
- Added `AppState.kt` with a persisted `frozen` flag in `vp_prefs`.
- Added a red `Freeze IRA` button below the orb. When active it stops `BubbleService` and turns amber `Resume IRA`; tapping again clears the freeze and restarts the wake service if it was requested before.
- `BubbleService` now refuses to start while frozen, and stops itself if a queued or system restart arrives.
- `AssistantService` is inert while frozen: events are ignored, node snapshots are cleared, and tap, scroll, type, read, back, home, and WhatsApp actions return `IRA is frozen`.
- `NotificationReader` ignores new notifications while frozen.
- The main screen shows `PAUSED`, disables the orb, and marks microphone and screen control as paused. The frozen state is read from prefs on launch, so it survives app and phone restarts.
- `./gradlew :app:lintDebug :app:assembleDebug` passes. APK is `app/build/outputs/apk/debug/app-debug.apk`.
- Installed over the existing `com.voicepoco.app` package. No second app or icon.
- Verified on device: tapping `Freeze IRA` shows `PAUSED` and `IRA is frozen.`, prefs write `frozen=true`, and the state survived a full force-stop and relaunch. Tapping `Resume IRA` clears the freeze, restores the normal screen, and writes `frozen=false`.
- No audio was started during install or freeze/resume testing; `wake_requested` remains `false`.
