# AlienFlow — Product Design and Implementation Plan

**Version:** 0.1.19 contract · **Updated:** 12 October 2026 · **Stage:** Implemented Android client; exact verification and remaining device limits are in IMPLEMENTATION.md.

**Working product name:** AlienFlow. This is an Android dictation application inspired by the interaction of Wispr Flow, with a personal-key development client and a planned paid Google Play release. The name is provisional and has not been checked for publication or trademark use.

**Goal:** Tap a small, translucent alien bubble while using an existing Android keyboard; speak naturally; see words and very light corrections appear in the focused input field; stop when finished. Use a personal OpenAI API key with no application word allowance or subscription gate.

**Architecture:** A native Android application with a user-enabled accessibility service, a nonfocusable accessibility overlay, a microphone foreground service, a single dictation coordinator, direct OpenAI integration, and lightweight local storage. The existing keyboard remains selected.

**Current implementation:** Kotlin; Android Views for the overlay and application screens; coroutines; Android accessibility input APIs; AtomicFile history; SharedPreferences; Android Keystore; Gradle; GitHub Actions. Minimum Android 13 / API 33 is proposed because the intended accessibility input connection starts there. [S2]

**Spec and plan:** This document contains the product specification, engineering decisions, acceptance criteria, and implementation plan. Requirements described as confirmed come from the conversation. Engineering defaults are proposed implementation choices, now selected under the user's instruction to use sensible defaults.

> For agentic workers: Execute the implementation milestones using subagent-driven-development or executing-plans as appropriate. Keep one owner of shared edits, preserve the requirements and stop conditions in this document, and record actual build and device evidence. Reading this plan does not authorize paid API tests, publication, or creation of a repository in an assumed account.

## 1. Decisions already made

The user has supplied enough information to finish planning. Routine choices should be made by the implementer and recorded; another extended questionnaire is unnecessary.

| Area | Agreed product behavior |
| --- | --- |
| Interaction | A small floating alien bubble, freely draggable, visually about the size of Gboard's voice button. |
| Appearance | 75% idle opacity (25% transparent) initially; adjustable size and opacity; remembered position. |
| Keyboard | Continue using the currently selected keyboard, including Gboard. |
| Text | Provisional words appear directly in the active input field during speech. |
| Corrections | Very light correction, including live updates, is the default. |
| Consistency | One global behavior across apps; no per-app styles or special correction defaults. |
| Controls | Tap to start and tap to stop; ordinary thinking pauses do not stop the session. |
| Language | English initially; language selection and personal vocabulary available. |
| Remembering | Lightweight recent text history, seven days initially. |
| Connectivity | Preserve pending audio through temporary disconnections and resume processing when possible. |
| Limits | No app-imposed word quota. OpenAI charges and limits, connectivity, and device resources still apply. |
| Delivery | Signed development APKs through GitHub Actions; planned paid Google Play release after device and store validation. |
| Options | Main behavior choices are editable; internal algorithms and integrity checks are not exposed as dozens of switches. |

Wispr's current Android help describes its floating control and a workflow that inserts a result after finishing. AlienFlow should preserve the familiar interaction while including the user's explicit addition: live words and live corrections inside the input field. This is a product requirement, irrespective of the inspiration app's current implementation. [S1]

### 1.1 What “almost identical” means here

The everyday experience should feel immediate and unobtrusive: focus a field, tap the alien, speak, tap again, continue typing. Setup happens once. The normal flow does not open the application, move text through a separate preview, or require changing the selected keyboard for each dictation.

The specification covers observable behavior. It does not claim knowledge of Wispr's proprietary code, current model choices, prompts, or internal processing. Original artwork will be used for the alien.

### 1.2 Boundaries of the current work

The initial 10 October planning stage produced documentation only. The Android project, existing repository, signed APKs and GitHub Actions pipeline now exist; IMPLEMENTATION.md and release verification records own the current evidence. Public trial provisioning and store publication remain separate from development APK delivery.

The first implementation target is the Galaxy Z Fold6. Its installed Android version, One UI version, keyboard version, and relevant permission state must be recorded during actual device validation. None has been inferred as an observed fact.

## 2. User experience

### 2.1 First launch

The application presents a short setup sequence: enter the user's API key, grant microphone access, enable the accessibility service, and complete a clearly labeled test dictation. Explain each permission in terms of its purpose. The accessibility permission supplies the floating control and allows editing the focused field; microphone access supplies speech input.

Key validation should distinguish invalid credentials, a model unavailable to the account, exhausted account allowance, and network failure. A paid sample recording belongs to the later, user-initiated setup test. API keys are entered on the phone, never embedded in source or requested in chat.

The landing screen then shows readiness, a dictation test field, a short latest-result preview, and separate History and Settings destinations. A persistent Ready notification keeps the floating dictation control available without keeping the activity open. The same foreground-service lifetime covers capture and delivery; the microphone is active only after a dictation tap. Hide alien stops idle readiness, and Reset position restores it. Notifications provide the relevant Hide, Stop or Cancel action. Permission loss returns the user to the relevant setup action without repeated nagging.

### 2.2 Normal dictation

1. Focus an editable field using the existing keyboard.
2. The alien appears at its remembered position. Its initial visible diameter is 32 dp, adjustable to fit the user's actual screen and Gboard button size. The touch target is at least 48 dp and remains localized around the bubble.
3. Tap the alien. Immediately show a starting state; show a listening state only after microphone capture has actually begun.
4. As speech is recognized, update the owned text span at the insertion point. Show actual words in the field, without a separate preview requirement.
5. As a short phrase becomes suitable for correction, apply only allowed changes to text the current dictation still owns. New recognition results and corrections may visibly revise those words.
6. Tap again to stop capture. Finish processing already captured speech, mark the result complete, and save the configured history record.
7. Resume ordinary typing. The application never presses Send, Enter, Search, or another submission action on the user's behalf.

An explicit selection at the start of dictation may be replaced as an ordinary text insertion would replace it. Snapshot that selection first and make undo contingent on continued ownership. An unselected field's pre-existing text must remain intact.

### 2.3 Bubble behavior and states

Use the alien artwork inside a small circular control. Idle opacity starts at 75% (25% transparent); a separate active indicator and clear status make recording visible. Active opacity defaults to 100% and is adjustable in the same appearance panel. At low opacity, the microphone indicator and notification must still communicate the true recording state.

| State | Visible behavior | Tap behavior |
| --- | --- | --- |
| Ready | Alien at idle opacity. | Start dictation. |
| Starting | Small progress indicator and “Starting”. | Cancel startup. |
| Listening | Distinct recording indicator; optional subtle audio activity. | Stop capture and finalize. |
| Connection interrupted | Explicit “Reconnecting; audio saved” state while capture continues. | Stop capture; preserve pending recovery. |
| Finishing | Processing indicator; microphone is released. | Show available cancel action. |
| Recovery waiting | Pending recording visible in the app; bubble returns to its ordinary availability. | Begin a fresh session if ready. |
| Error | Brief, actionable explanation; retained work remains accessible. | Retry only when appropriate. |

Dragging moves the control without starting a recording. A drag threshold separates tap and move; edge snapping is optional and off by default. Long-press opens a compact menu with Settings, hide, and context-appropriate cancel/undo actions. Press-and-hold recording, when enabled, uses an accessible alternate route to that menu.

Remember separate normalized positions for folded and unfolded layouts. Recalculate against usable bounds when rotation, insets, keyboard position, or display layout changes. The bubble must remain reachable. An explicit Reset position action restores it near the keyboard's upper edge.

When configured to appear for active fields, the bubble stays visible through listening and finalization. “Always visible” remains an option. No app allowlist determines ordinary behavior.

### 2.4 Interruptions and editing

Manual typing and cursor movement must not pause capture, transcription, or detach dictation. Preserve keyboard edits in the same field and insert newly recognized speech at the current cursor without a restart tap. Previously dispatched speech becomes protected from late recognition/correction rewrites after a keyboard interaction. Do not wait for the user to finish a keyboard composing word or type a space. Use the Android accessibility commit behavior that finishes composition before insertion, preserving the literal typed word; require matching fresh text/cursor evidence. Retry only conflicting changed evidence or temporarily unavailable metadata, without a capture/transcription pause. Focus changes, microphone interruptions, locking the screen, permission revocation, or service disconnection invalidate the target binding. A late response must not appear in a newly focused application.

Default behavior on losing the active field is to stop capture and preserve unfinished work. Re-entering that field does not silently resume microphone capture or retrospective edits. A fresh tap starts a fresh dictation session.

Cancel stops recording and processing. If the app can still prove ownership of its inserted span, it can remove that span and restore the initial selection contents. If ownership is uncertain, leave the field unchanged and report that cancellation stopped further work. Never undo the user's subsequent edits. An interrupted recording explicitly retained for recovery is different from a recording the user has deliberately discarded.

## 3. Settings: compact, meaningful, changeable

The main screen shows the current choices in six groups. Related controls open one short panel. Changes apply immediately when safe, or at the next dictation with that behavior stated. Internal buffer sizes, event ordering, model request fields, and stale-edit checks remain engineering details.

| Group | Setting and initial value | User choices |
| --- | --- | --- |
| Bubble | Appearance: alien; 32 dp visible size; 75% idle / 100% active opacity. | Adjust size and both opacity states; choose bundled alien variants or a local image; reset appearance. |
| Bubble | Availability: focused input fields; freely positioned. | Always visible; show for input; temporarily hide. Optional edge snap and Reset position. |
| Dictation | Recording: tap start / tap stop. | Tap toggle or hold to talk; optional silence stop, initially off. |
| Dictation | Text display: live in the field. | Live or insert when finished. The latter is an explicit preference, never a hidden fallback counted as live support. |
| Dictation | Language: English. | Other supported languages or automatic detection; editable personal vocabulary. |
| Corrections | Strength: B, Very light. | A, Faithful; B, Very light; C, Rewrite. Show examples before changing. |
| Corrections | Timing: live, phrase by phrase. | Live or after stopping. With end-only text display, revisions stay internal until insertion. |
| Corrections | Filler/repetition removal: off. | A single optional cleanup control for obvious filler and accidental repetition. |
| History and recovery | Remember: text only for seven days. | Off; text; text plus audio. Retention presets: 1, 7, 30 days or until deleted. Delete entry / Clear history actions. |
| History and recovery | On connection loss: keep recording locally and reconnect. | Continue with recovery, or stop recording immediately and preserve what was captured. Pending audio retention initially 24 hours; selectable 1 hour, 24 hours, 7 days, or until deleted. |
| Connection and usage | Personal OpenAI key; automatic supported model selection for the chosen mode. | Replace/remove/test key; inspect model and connection status; choose supported live or finished-recording options where appropriate. |
| Connection and usage | Resource and cost overview. | View estimated usage, local text/audio storage, pending recovery, and deletion actions. No required app quota. |

This table defines product preferences, not a demand for one switch per implementation constant. Correction strength bundles related behaviors; latency tuning and transport details remain automatic. The user can change the defaults that affect the experience without becoming responsible for model parameters.

Remembering includes recent text, explicit vocabulary entries, preferences, and bubble position. It does not imply continual listening, automatic personality learning, background model training, or sending the entire history to improve every new dictation. If history is off, operational recovery still follows its separate visible preference; explain that distinction in the panel.

## 4. Correction contract

### 4.1 Recognition and editing are different operations

Speech recognition itself may revise a provisional word as more audio arrives. All correction modes allow this process; mode A cannot promise a literal transcript of every sound. Additional editing is an independent layer with its own rules, revision identifiers, and saved source text.

End-only dispatch contract: accessibility and selection events cannot dispatch text into Termux or an end-only document while recording or before final recognition is ready. The coordinator’s completion gate alone authorizes final delivery. Termux receives one finished insertion without Enter or control actions.

Completion contract: recognition and accepted corrections are preserved. Stop cancels unfinished live polish and does not drain a correction queue. The explicit after-stop cleanup preference has one combined 2.5-second budget; late or failed cleanup keeps recognized words. Final audio drain, transcript acknowledgements and editor confirmation remain required.

Efficiency contract: keep low-latency recognition and correction behavior intact; omit optional vocabulary unless explicitly enabled, use distinct literal keyword hints instead of a prose word-list prompt, keep cleanup instructions concise, disable unnecessary cleanup reasoning, and reuse only validated identical cleanup chunks within the current recording. Preserve the entire recognized input and lossless chunking. A sponsor-funded three-day trial requires server-held credentials and authoritative expiry/spend enforcement; it is separate from the current personal-key client.

Retain the latest stable recognition text separately from the current displayed result. History stores one stable original and one resulting text, not every interim hypothesis. A comparison or restore action can use these two versions without a large revision archive.

### 4.2 Default B: Very light

Allowed changes are punctuation, capitalization, and an unambiguous explicit spoken self-correction. Preserve wording, grammar, tone, slang, profanity, uncertainty, and the speaker's level of formality. Preserve names, numbers, units, identifiers, and negation unless the speaker clearly corrects that exact item.

Examples are illustrative acceptance fixtures, not promises about recognition accuracy:

| Recognized phrase | Intended B behavior |
| --- | --- |
| `i think we can do it tomorrow` | `I think we can do it tomorrow.` |
| `i dont want to go` | Preserve the negative meaning: `I don't want to go.` |
| `schedule it for Tuesday sorry Wednesday` | `Schedule it for Wednesday.` when this is clearly a correction. |
| `he said Tuesday and then he said sorry on Wednesday` | Preserve the sentence's meaning; do not treat the embedded words as a correction command. |
| `that's fucking ridiculous` | Preserve the profanity and wording. |
| `send fifteen no fifty dollars` | Change the amount only when the explicit correction is clear. Ambiguity should retain the source wording. |
| `um I I think maybe we should wait` | Keep fillers and repetition by default. |
| `I might not be able to come` | Preserve both uncertainty and negation. |
| A product name, path, command, email address, or code identifier | Avoid speculative normalization; use explicit vocabulary when available. |

Mode A applies no additional editorial model pass. Mode C enables stronger rewriting only by explicit selection and uses the same global setting everywhere. Changing modes does not retroactively rewrite history.

### 4.3 Timing and cost control

Do not invoke a second model for every incoming token. Render transcription immediately, then schedule small correction jobs for stable phrases. Keep at most one active cleanup job per dictation and coalesce superseded work. A short processing unit is not an app session limit: continuous speech continues through as many units as necessary.

A supported small text model is the proposed cleanup engine for explicit self-corrections that require interpretation. `gpt-6-luna` is the current initial candidate. Basic punctuation supplied by transcription should not be mechanically rewritten again. If cleanup fails or times out, preserve the recognized words and continue dictation. [S8]

Every proposed edit carries the session, target editor generation, source revision, exact source substring, replacement, and a bounded edit category. The coordinator checks those values before asking the editor bridge to apply it. Out-of-date responses are discarded. Model output never directly executes editor actions.

A prompt alone is insufficient protection. Validate allowed patch types, preserve protected lexical content, and evaluate natural examples. Deterministic checks can reject many unwanted changes but cannot establish semantic equivalence for every sentence. Ambiguous changes should be skipped. A failed meaningful test in mode B must be fixed or reported; do not rename rewriting as “light correction”.

## 5. Android architecture and compatibility gate

### 5.1 Chosen integration

Use a user-enabled `AccessibilityService` with `FLAG_INPUT_METHOD_EDITOR` and a `TYPE_ACCESSIBILITY_OVERLAY` window. Keep the window nonfocusable, with only the small bubble/menu region accepting touch. The documented accessibility overlay route does not itself require an additional draw-over-other-apps permission. The prototype must verify actual behavior on the device before adding permissions as a workaround. [S3, S4]

The accessibility input connection exposes editing operations such as committing text, selecting text, deleting surrounding text, and reading surrounding text. It is not the complete ordinary keyboard connection and does not expose public composing-text or batch-edit methods. Consequently, provisional words in this architecture are actual inserted text tracked by AlienFlow; a visual “composing” span cannot be assumed. Readback may fail or be unavailable. [S2]

A foreground service owns active microphone capture, with the applicable microphone service type and permissions. Starting that service from another application's screen must satisfy both foreground-service start restrictions and microphone access restrictions. An overlay alone is not proof that capture is permitted. [S5]

### 5.2 What must be proved first

The highest-risk assumption is that this combination can start recording from the bubble while Gboard remains selected and can safely replace live text in the actual editors the user relies on.

The first device demonstration must begin with AlienFlow's activity closed, no existing microphone service keeping access warm, no recent-activity grace being relied on, and ordinary device battery settings. Focus another app's field, tap the bubble, and show real microphone capture, live words, retained focus, and the original keyboard. Repeat after accessibility service reconnection and a fresh process start.

Opening an activity on every tap, silently changing the selected keyboard, showing a separate preview, or pasting only at the end does not satisfy that demonstration. A temporary engineering experiment may use those techniques to investigate a cause, but must not be reported as the requested feature passing.

### 5.3 Universal input is a goal with an explicit proof boundary

One behavior across all input boxes remains the product objective. Android exposes different levels of cooperation from ordinary fields, browser editors, rich editors, terminals, and protected fields. It is not honest to promise identical live replacement in every possible editor merely because the accessibility permission exists.

The implementation must use one general adapter, discover actual capabilities, and test representative editors. A per-app allowlist, different correction policy, or whole-field replacement shortcut is not the solution to an incompatible editor. If an editor does not supply safe editing/readback, retain the transcript and report the limitation. That is a safe failure state, not a passing result for the universal inline requirement.

If the central goal cannot be met with this route, document the precise failing API/device/editor behavior and compare any architecture change against the user's existing-keyboard and uniform-workflow requirements. Do not silently narrow the product to “works in most apps.” Password or otherwise protected fields must not be read into history or model prompts while experimenting with compatibility.

### 5.4 Components and ownership

Start with a single application module and one process. Split by responsibility into packages; separate Gradle modules only if a demonstrated build or ownership need emerges.

| Component | Responsibility | Must not do |
| --- | --- | --- |
| Accessibility service | Editor lifecycle, accessibility connection, bubble window. | Run independent competing text writers. |
| `DictationCoordinator` | Serialize session events, capture state, recognition revisions, cleanup, recovery, and target ownership. | Infer success from a network request alone. |
| `EditorBridge` | The sole owner of reads, selection changes, insertion, replacement, and guarded undo. | Replace an entire field to update one dictated phrase. |
| `AudioCapture` | Microphone lifecycle and bounded audio buffers; feed transport and recovery storage. | Listen while the user believes dictation is idle. |
| `TranscriptionClient` | Authentication, session configuration, events, connection lifecycle. | Write to any editor or determine application focus. |
| `CorrectionEngine` | Produce narrowly scoped proposed edits for a known source revision. | Apply arbitrary model-generated commands. |
| Room repository | Session checkpoints, stable text history, and audio segment metadata. | Save every recognition delta as a permanent history entry. |
| Preferences and key store | Small settings in DataStore; encrypted credential storage backed by Keystore. | Store a usable API key in a plain preference or build artifact. |
| App screens | Setup, settings, history, recovery, usage, diagnostics. | Own a second dictation state machine. |

### 5.5 Single-writer editing protocol

Use one serialized queue for recognition and correction proposals. Each target has an editor generation that changes on focus/session reset, a tracked insertion range, nearby text anchors, a selection snapshot, and a local revision. Android offsets use UTF-16 units; handle surrogate pairs and grapheme boundaries deliberately.

For each replacement, re-check the active editor identity, expected text, anchors, and selection. Replace only the changed tail of owned dictation. An explicit surrounding-text offset may corroborate a bounded keyboard window when its text and absolute selection match the complete accessibility snapshot and the window covers the entire mutation; an unknown offset still requires matching full contents. If they still match, select only the owned range and commit the replacement. Require fresh editor evidence before advancing the confirmed display revision or owned range: accessibility selection and commit operations return no success value, so dispatch alone is not confirmation. If the outcome cannot be confirmed, mark it uncertain, stop subsequent automatic replacements, and preserve the result outside the field. Do not blindly retry or undo an uncertain write. [S2]

These operations are not an atomic compare-and-swap transaction across another app. A manual edit or focus transition can race between the check and write. Explicitly investigate that window, including ordinary keyboard autocorrection, in device tests. If the race cannot be bounded sufficiently to prevent data damage, this is a failed architecture gate, not a problem to hide with a prompt.

Suggested proposed data contracts:

```text
EditorTarget = editorGeneration, editorIdentity, initialSelection, anchors
OwnedSpan = target, startUtf16, expectedText, sourceRevision, displayRevision
RecognitionTurn = localSequence, providerItemId, provisionalText, finalText?, state
EditProposal = sessionId, editorGeneration, baseRevision, expectedSubstring,
               replacement, allowedCategory
AudioSegment = sessionId, sequence, file, timeRange, uploadState, transcriptCheckpoint
```

Identifiers above describe application contracts; they are not claims about the shape of Android or OpenAI request bodies.

## 6. Transcription and live processing

### 6.1 Current model direction

Use a supported OpenAI live transcription model for the default experience. At this documentation checkpoint, `gpt-live-transcribe` is the live model and `gpt-transcribe` is the finished-recording candidate for recovery or the optional end-only mode. OpenAI lists older Whisper and GPT-4o transcription models for retirement on 26 February 2027; a new app should not be tied to those legacy identifiers. Account access still needs to be checked during implementation. [S6, S7, S9]

Keep provider-specific request and event mapping behind one adapter. Model choices exposed to the user should be supported capability choices with a clear price/behavior description. Do not offer a text-chat model in a transcription selector or silently switch from live input to an incompatible model.

### 6.2 Native transport and live handshake (revised 12 October 2026)

Native WebRTC was removed in 0.1.6 after a documented Fold6 native SIGABRT; the Android AudioRecord plus WebSocket implementation remains. The Realtime transcription connection uses `wss://api.openai.com/v1/realtime?intent=transcription` with an authenticated bearer key, **not** `?model=gpt-live-transcribe`. Use `session.update` to configure `type=transcription`, 24-kHz PCM16 and `audio.input.transcription.model=gpt-live-transcribe`; preserve vocabulary, language settings and client-side audio turn commits. Current primary references in `RESEARCH_2026-10-12_LIVE_HANDSHAKE.md`.

The key/model check MUST use that same WebSocket URL and session update and require an acknowledged transcription session; a client-secret request by itself is not evidence of live connectivity. That check must close without activating the microphone. Microphone capture and session negotiation start in parallel; startup audio is bounded and buffered until acknowledgement, then drained once in order. Test real transcript deltas and completion events on the phone. Preserve the personal API key and native Android mic permissions; no model fallback, untrusted endpoint override or recording without user intent.

### 6.3 Audio, turns, and ordered results

The live guide uses transcription sessions, provisional deltas, and completed transcript events; results across separate turns may complete out of order. Current live transcription does not support server or semantic VAD. Use local turn detection and explicit `input_audio_buffer.commit` control events, while sending native AudioRecord PCM frames through the acknowledged WebSocket. Validate the accepted configuration and commit behavior rather than assuming another model's examples apply. [S6]

Separate three concepts: a user dictation session, short transcription turns, and an API connection. The user can keep talking through many turns and connection renewals without a word cap. A pause may finalize a turn without stopping the recording.

The coordinator owns an ordered turn ledger. Correlate deltas and completion events to provider item identifiers and local audio sequence. Final text replaces that item's provisional text; it is not appended a second time. Do not sort opaque provider IDs or assume arrival order equals speaking order. Replayed events and retries must be deduplicated against local checkpoints.

At the transport session's maximum duration, rotate the connection at a suitable boundary while keeping microphone capture and local buffering continuous. The documented general Realtime session limit is a provider connection limit, not an AlienFlow dictation limit. Establish the current live-model limit during the protocol check and test renewal before release. [S12]

### 6.4 Proposed performance targets

These are initial engineering targets, not measurements or promises about a mobile network. Record both warm and cold starts and do not combine them into an artificially favorable number.

| Measurement | Initial target | Measurement boundary |
| --- | --- | --- |
| Visible response to tap | Within 100 ms. | Tap event to first starting-state frame. |
| First readable live words | Median within 1 second; 95th percentile within 2 seconds on a recorded good-network test. | End of the first recognizable phrase to its first in-field appearance. Report speech onset timing separately. |
| Local application overhead | 95th percentile below 100 ms. | Receipt of usable recognition event to successful editor update. |
| Light correction | 95th percentile within 2 seconds after a suitable stable phrase. | Phrase available for correction to verified in-field replacement. |
| Stop to final text | 95th percentile within 2 seconds on the same network, excluding disclosed recovery. | Stop action to settled result. |
| Idle behavior | No microphone capture and no active transcription session. | Observe services, capture state, and network while idle. |

Use at least 30 varied utterances for an initial latency distribution and report the small sample size. Include corrections, names, slang, negation, code-like text, quiet and noisy speech. Report accuracy and changed meaning alongside speed; an aggressive latency setting is not a win if it corrupts wording.

## 7. Recovery, history, and resource use

### 7.1 Recovery contract

Maintain a small in-memory audio ring and app-private persistent segment files for uncheckpointed audio. Promised recovery audio belongs in private persistent/no-backup storage, not an evictable cache directory. Segments must be playable or convertible into a supported transcription upload after a process restart.

On a temporary disconnect, show the interruption immediately. In the default mode, continue bounded local capture, retry with backoff, and preserve order between old pending speech and new speech. Reconnection must not duplicate previously inserted text. If authentication, account limits, or persistent errors prevent progress, stop wasteful retrying, preserve work, and provide the specific next action.

Once capture stops, a recovery worker may process already captured audio when connectivity and Android scheduling allow. It must never start the microphone in the background. After process death or target loss, recovered text appears as a recoverable history entry and is inserted only by a fresh user action into a currently selected field. Recovery does not grant permission to type into whichever application is open later.

Checkpoint stable transcription durably before deleting associated audio. Partial uploads that may have been accepted create an at-least-once retry problem: the app can prevent duplicate displayed text, but cannot promise a provider will never bill repeated processing. Preserve enough segment and result identity to diagnose that boundary without recording content in logs.

Use actual storage pressure and a bounded working set to pause safely before running out of space. Show remaining pending recording and the reason; never silently discard unprocessed speech or enforce a disguised word quota. Recovery expiry is visible in the UI and follows the retention preference. Expired files are deleted on the next permitted maintenance opportunity; exact deletion while the OS prevents the app from running cannot be promised.

### 7.2 Lightweight remembering

Default history contains timestamp, original stable recognized text, displayed result, language, completion/recovery state, and small usage metadata. It stores no permanent stream of revisions and deletes successful audio once its required transcription checkpoint is durable. With text-plus-audio selected, retain completed audio according to the chosen history policy.

Prune old entries in small batches during activity or scheduled maintenance; reading history excludes expired entries immediately. Keep list loading paged. Search operates over retained text only. No idle polling or always-running background process is needed merely to remember text.

Age-based retention and paged reads keep ordinary text history manageable. Show retained size and the oldest retained date. “Until deleted” keeps completed entries until the user removes them or changes the policy; actual storage pressure must be visible. Pending recovery is managed by its own displayed retention policy and is not silently evicted by text-history pruning.

### 7.3 Estimated footprint

The following are calculations, not APK or device benchmarks. UTF-8 bytes per word vary by language, punctuation, and metadata. Audio examples assume recovery has been normalized to 24 kHz mono 16-bit PCM; WebRTC may negotiate a different capture/wire format, whose actual buffer cost must be measured.

| Example | Calculation | Approximate payload |
| --- | --- | --- |
| Seven days of very heavy text use | 10,000 words/day × 7 × 6 bytes/word. | 0.42 MB for one version; 0.84 MB for original plus result, before database overhead. |
| Two-second PCM ring | 24,000 samples/second × 2 bytes × 2 seconds. | 96 kB before runtime overhead. |
| Pending raw PCM per minute | 24,000 × 2 × 60. | 2.88 MB/minute. |
| One hour of pending raw PCM | 2.88 MB × 60. | 172.8 MB. |

Text history is therefore likely to be minor relative to microphone, networking, and retained audio. Actual database, installed app, resident memory, CPU, thermal, and battery figures must be measured later on the Fold6. Streaming avoids loading an entire recording into RAM. Do not claim zero battery cost or an exact APK size before building and profiling.

## 8. Credentials, privacy, and failure behavior

The user's OpenAI key is provided at runtime and encrypted at rest using a Keystore-protected cryptographic key. Exclude key material, audio, and text history from automatic backup by default; do not log dictation content, surrounding application text, credentials, or authorization headers. Diagnostic exports contain scrubbed state and timing with a preview before sharing. [S13]

This is a deliberate personal BYOK deployment. OpenAI's general guidance recommends keeping a service's key behind a backend rather than deploying it in a mobile client. A personal key entered by its owner avoids shipping a shared key, but a running or compromised phone can still access it; encryption at rest does not make it server-held. A distributed multi-user service would require a fresh credential architecture decision. [S14]

Send audio necessary for transcription and the smallest relevant dictated phrase for optional cleanup. Surrounding editor text is used locally for ownership checks and is not sent by default. Personal vocabulary is sent only as explicit transcription context where supported. Neither complete recent history nor arbitrary screen contents belong in prompts.

Local deletion does not assert deletion of already transmitted provider data. The app should link to current provider data-control information and avoid claims such as “nothing ever leaves the device” or “the API stores nothing.” [S15]

| Failure | Required behavior |
| --- | --- |
| Invalid/expired key | Stop authentication retries; preserve pending work; open key replacement action. |
| Model unavailable | Explain the exact unavailable capability; offer compatible supported choices without silently removing live behavior. |
| Rate/account limit | Preserve work; show retry/account action; obey retry guidance. |
| Network outage | Visible local-recovery state according to preference. |
| Microphone unavailable/revoked | Stop capture and release resources; retain captured work; provide relevant permission/interruption action. |
| Editor ownership uncertain | Stop automatic writes; retain transcript; preserve existing field contents. |
| Cleanup unavailable | Keep recognized text and continue; no forced failure of otherwise valid dictation. |
| Storage pressure | Pause capture visibly before loss; retain durable segments and offer recovery/deletion actions. |
| Service/process interruption | Restore settings and recover durable work; never silently restart microphone capture. |

## 9. API cost model

Current official model pages list live transcription at **US$0.017 per submitted audio minute**, approximately **US$1.02 per audio hour**. Finished-recording transcription is listed at **US$0.0045 per minute**, approximately **US$0.27 per hour**. Live transcription remains the default because the user requested words while speaking. [S7, S16]

| Live audio submitted each day | Minutes in a 30-day month | Estimated transcription cost |
| --- | --- | --- |
| 15 minutes | 450 | $7.65 |
| 30 minutes | 900 | $15.30 |
| 60 minutes | 1,800 | $30.60 |

These estimates exclude optional cleanup, reprocessed recovery audio, and any other requests. The candidate cleanup model is currently listed at $0.10 per million input tokens and $0.50 per million output tokens; an illustrative 1,000 input plus 1,000 output tokens is $0.0006. Actual request prompts, repeated context, and output length determine total cost. Prices must be rechecked when implementation starts. [S8]

Usage displays should measure submitted audio and returned usage where available, with visible estimates for incomplete requests. Do not equate microphone wall-clock duration with exact billable usage or present the app's estimate as the provider's final bill. The application has no word allowance and no mandatory monthly app cap; users retain their account-side controls.

## 10. Implementation plan

### Global constraints

- Preserve the confirmed live inline and very-light correction defaults.
- Keep the selected keyboard; retain one global behavior across editors.
- Never auto-submit text, replace unrelated content, or apply stale edits.
- Keep app behavior preferences changeable through the compact settings design.
- Use actual current APIs and pinned dependencies; distinguish documentation evidence from execution evidence.
- Do not build unused provider stacks, account systems, a cloud backend, analytics, or a full replacement keyboard in the first version.
- Build only the requested revision through an intentional workflow; avoid polling and repeated unchanged builds.

### 10.1 Proposed repository layout

The repository owner, URL, and branch have not been bound. `AlienFlow` and `main` are naming proposals, not an existing repository claim. The package name must be chosen and locked before the first permanent signed install.

| Proposed path | Owner/responsibility |
| --- | --- |
| `app/build.gradle.kts` | Android app ID, SDK levels, source-controlled versionCode/versionName, build variants. |
| `gradle/libs.versions.toml` | Exact dependency/plugin versions selected together. |
| `app/src/main/AndroidManifest.xml` | Minimum permissions and service declarations. |
| `app/src/main/res/xml/alien_accessibility_service.xml` | Accessibility capabilities and service description. |
| `app/src/main/java/<package>/accessibility/AlienAccessibilityService.kt` | Editor lifecycle and accessibility overlay host. |
| `app/src/main/java/<package>/overlay/AlienBubbleController.kt` | Bubble states, touch/drag behavior, bounds and position. |
| `app/src/main/java/<package>/dictation/DictationCoordinator.kt` | Single serialized owner of the dictation state machine. |
| `app/src/main/java/<package>/editor/EditorBridge.kt` | Guarded reads, insertions, replacements, and undo. |
| `app/src/main/java/<package>/audio/MicrophoneService.kt` | Foreground microphone lifecycle and interruption handling. |
| `app/src/main/java/<package>/audio/AudioCapture.kt` | PCM/audio pipeline and bounded buffers. |
| `app/src/main/java/<package>/openai/TranscriptionClient.kt` | Chosen current native transport and event mapping. |
| `app/src/main/java/<package>/correction/CorrectionEngine.kt` | Bounded correction requests and validation. |
| `app/src/main/java/<package>/data/AlienDatabase.kt` | Room records, migrations, checkpoints, retention. |
| `app/src/main/java/<package>/data/SettingsRepository.kt` | DataStore preferences and default mapping. |
| `app/src/main/java/<package>/security/ApiKeyStore.kt` | Keystore-backed encryption and credential lifecycle. |
| `app/src/main/java/<package>/recovery/RecoveryWorker.kt` | Existing-audio recovery without microphone restart. |
| `app/src/main/java/<package>/ui/` | Setup, settings, recent history, pending recovery and usage screens. |
| `app/src/test/` and `app/src/androidTest/` | Focused state/ordering/retention tests and editor harnesses. |
| `.github/workflows/build-apk.yml` | Intentional signed APK build and direct artifact upload. |
| `docs/` | This spec, architecture decisions, device evidence, and a concise change log. |

`<package>` is deliberate notation for the package bound at implementation kickoff. It is not a placeholder to commit unchanged into source.

### Milestone 1 — Prove the complete bubble-to-field path

**Purpose:** Resolve the actual platform, provider, and build risks before polishing settings and history.

1. Bind the authorized repository, permanent application ID, and signing ownership. Capture a clean baseline and record the exact revision for every artifact.
2. Select and pin a compatible stable toolchain. Current Android documentation lists AGP 9.4.0 with Gradle 9.6.0 and JDK 17, supporting API 37. Resolve its actual built-in Kotlin compiler and compatible Compose plugin/dependencies rather than combining unrelated “latest” versions. Record wrapper SHA-256 and exact action revisions. [S17, S18]
3. Create the smallest runnable native application that includes runtime key entry, permission setup, the accessibility bubble, microphone lifecycle, a real transcription connection, and guarded live insertion. Temporary simple screens are acceptable; a static skeleton is not completion of this milestone.
4. Validate the single native WebRTC transport as specified in section 6.2, including its shared capture/recovery hook. Record a real session acknowledgement and actual delta/final event shapes, with no credentials or dictated text in logs.
5. Provide an intentionally built, signed test APK through the planned Actions workflow once execution is authorized. Record its commit, package, version, certificate fingerprint, and hash.
6. Run the clean-start Fold6 demonstration in section 5.2. Test a native text field and a browser textarea, plus the first real everyday input the user relies on. Test keyboard autocorrection, cursor movement, and focus changes during an in-flight result. Tap the bubble while Gboard has an unfinished composing word: preserve the manually typed prefix and establish the correct dictated-text boundary without loss or duplication. AOSP explicitly finishes ordinary keyboard composition during an accessibility commit; the resulting Fold6 behavior requires device evidence. [S25]

**Meaningful checks:** Event replay must not duplicate words; stale target results must not write; text outside the inserted span must remain byte-for-byte/UTF-16-equivalent; a manual edit must invalidate unsafe replacement. Simulate an edit dispatch with missing confirmation and verify that no subsequent replacement, retry, or undo assumes it succeeded. A test double helps isolate ordering but does not satisfy real microphone or editor acceptance.

**Exit:** Real speech appears live in another app while the original keyboard remains selected, and the ownership strategy survives deliberate interference. If microphone eligibility, supported transport, or safe live editing fails, record the specific failure and resolve architecture before continuing dependent polish.

### Milestone 2 — Very light live correction without text damage

**Paths:** `CorrectionEngine`, `DictationCoordinator`, `EditorBridge`, focused tests.

Implement the A/B/C policy and live/end timing, with B and live as defaults. Keep original recognition and displayed text distinct. Use phrase-based jobs with revision-checked patches. Ensure the speech pipeline stays responsive while correction requests are in flight.

**Meaningful checks:** Run every fixture in section 4; include speech that literally contains a correction marker without intending a correction. Change focus, move the cursor, type manually, and receive out-of-order correction results. Test Unicode, multiline text, selection replacement, overlapping ASR revisions, and a correction arriving after Stop or Cancel.

**Exit:** The default improves only permitted features, preserves meaning in the evaluation set, and stale work cannot corrupt the field. Report any observed unwanted rewrite with its input, proposed patch, and actual display outcome. Confirm live correction using a real provider, not only mocked output.

### Milestone 3 — Settings, lightweight history, and durable recovery

**Paths:** DataStore and Room repositories, key store, `RecoveryWorker`, capture spool, UI screens, migration/retention tests.

Implement the settings table using grouped controls; persist bubble positions. Persist stable text/checkpoints and pending segment metadata with crash-consistent ordering. Implement text-only seven-day history, optional audio retention, pending retention, deletion, and original/result comparison.

**Meaningful checks:** Force a network interruption, complete some speech before and after it, reconnect, and confirm ordered output without duplication. Kill and restart the process between capture, upload, checkpoint, and deletion. Verify no microphone restart on recovery and no insertion into a new field. Simulate low storage and corrupt/unavailable audio files. Changing history preferences must have the displayed retention effect without deleting active pending work unexpectedly.

**Exit:** Existing speech is recoverable, memory use is bounded, preferences survive restart, and resource reports reflect actual retained data. Auth and account-limit failures must not create an infinite request loop.

### Milestone 4 — Cross-editor, Fold6, and performance validation

**Paths:** Overlay interaction, bounds/position, microphone route handling, editor adapter, integration evidence.

Run the acceptance matrix in section 12 on folded and unfolded layouts, both orientations, ordinary keyboard use, and normal battery settings. Include browser/rich-editor behavior and terminal-like inputs where used. Test supported Bluetooth and device microphone routing, calls or competing microphone use, long silence, continuous speech, and an API connection renewal.

Collect the latency distributions and memory/storage/battery observations from sections 6 and 7. Document the exact device, OS, keyboard, app/editor versions, account model access, and network conditions. Resolve confirmed behavior failures; do not add speculative extra tests after the concrete risk is covered.

**Exit:** Required cases must pass for product acceptance. A completed compatibility investigation may report failures, but every unresolved required editor failure remains a failed requirement and prevents a completion claim for the requested product. A diagnostic APK can still be delivered with that status explicit. Representative passing tests provide bounded evidence and cannot establish compatibility with every possible input box. The user-visible limitation, if any, must match the demonstrated technical limitation.

### Milestone 5 — Reliable updates and handoff

**Paths:** Actions workflow, signing configuration, source version metadata, release notes and verification record.

Build one intended release candidate from the verified revision. Verify APK identity and signing, inspect manifest settings, record SHA-256, and publish the private build artifact through the agreed workflow. Install version N and then N+1 through the phone's installer to verify retained API key, settings, positions, history, and recovery metadata.

**Exit:** The user can download and install the intended APK, recognize its version, and install an update without losing data. Record any install warnings actually encountered and the precise resolution. A store listing, public release, automatic updater, and multi-user backend are separate work.

## 11. GitHub Actions build and APK delivery

### 11.1 Trigger and scope

Use a manually dispatched workflow on the bound `main` branch initially. One workflow run at a time, with `cancel-in-progress: false`, prevents a newer request from terminating an APK already building. Standard concurrency retains one running job and at most one pending job; do not advertise it as a durable unlimited build queue. No scheduled builds or background polling are needed. [S19]

Permissions start at `contents: read`. Keep checkout credentials out of subsequent steps, pin third-party actions to verified full commit SHAs, validate the wrapper, and use one Gradle caching approach. Run untrusted pull-request checks without signing secrets; never combine privileged secrets with untrusted source execution. [S20]

### 11.2 Signing and versioning

Use one release signing identity for the application and keep a secure backup under the user's control. Store only the signing material required for CI in repository/environment secrets. Decode it into a restricted ephemeral directory, keep it out of artifacts and caches, and remove it after use. The OpenAI API key is never a build secret because the application receives it at runtime.

Keep `versionCode` as an increasing source-controlled integer for deliberate installable revisions. Rerunning the same source revision should not turn an older codebase into an apparently newer update. A stable package and signing identity are required for ordinary installed-app updates. [S21, S22]

The exact package ID and action commit hashes are to be bound from the actual authorized repository and selected dependencies during milestone 1. Invented hashes, secrets, URLs, or certificate fingerprints must never be included as if verified.

### 11.3 Build and inspection commands

The following commands are planned checks, not checks executed during documentation. File names and paths will be set by the actual build implementation.

```bash
sdkmanager --list --channel=0
./gradlew --version
./gradlew :app:buildEnvironment
./gradlew --no-daemon --no-configuration-cache :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
apksigner verify --verbose --print-certs AlienFlow.apk
apkanalyzer apk summary AlienFlow.apk
apkanalyzer manifest min-sdk AlienFlow.apk
apkanalyzer manifest target-sdk AlienFlow.apk
apkanalyzer manifest debuggable AlienFlow.apk
sha256sum AlienFlow.apk
```

Resolve executable locations from the installed Android SDK and the produced APK path. Fail the workflow if the release is debuggable, unsigned, has an unexpected package/certificate, or fails required checks. Do not put secret-bearing shell output into a build summary.

### 11.4 Phone-friendly artifact

The current `actions/upload-artifact` documentation supports a single unarchived file with `archive: false` in v7. Use that capability for the signed APK so the phone does not need to extract a ZIP. Pin the verified action revision at implementation time. Set an explicit initial artifact retention of 30 days. [S23]

The run summary includes the application version, source commit, run identifier, signing fingerprint, SHA-256, install instructions, and a link to that run's artifact. Keep names distinct across run attempts. Explain the required GitHub sign-in when applicable. A generated example URL is not a delivered artifact; only link actual completed runs.

## 12. Acceptance criteria and traceability

“Required” identifies the intended product acceptance standard. Some criteria depend on a physical device or paid provider access and remain untested at this planning stage.

| ID | Requirement and passing evidence | Milestones |
| --- | --- | --- |
| AF-01 | Bubble has original alien art, starts near the requested visual size at 75% idle opacity (25% transparent), and the main appearance settings actually change it. | 1, 3, 4 |
| AF-02 | Dragging does not record; tap toggles recording; remembered positions remain reachable after folding/rotation and process restart. | 1, 3, 4 |
| AF-03 | Clean-start microphone capture works from another app with the existing keyboard selected and no hidden activity workaround. | 1 |
| AF-04 | Real provisional words appear in the target input during speech, followed by correlated final revisions without duplicate insertion. | 1, 4 |
| AF-05 | Default B corrects only permitted features, including an explicit self-correction, with live in-field updates and no unwanted tone or grammar rewrite in the evaluation set. | 2, 4 |
| AF-06 | Names, numbers, negation, slang, profanity, and uncertainty survive optional editing except for a clearly spoken correction. | 2 |
| AF-07 | Pre-existing content, manual edits, and newly focused fields are protected against late results and cancellation. No automatic submission occurs. | 1, 2, 4 |
| AF-08 | The same workflow and policy work across representative native, browser, rich, and custom editors. Every failure is identified; unsupported fields do not silently count as passing. | 1, 4 |
| AF-09 | Main defaults are adjustable and survive updates. No per-app correction settings are required. | 3, 5 |
| AF-10 | Text history defaults to seven days, remains lightweight, respects deletion, and does not retain successful audio by default. | 3, 4 |
| AF-11 | Disconnect/reconnect and process death preserve durable captured speech without duplicate display, infinite retry, unintended microphone restart, or late insertion into another app. | 3, 4 |
| AF-12 | Long dictation crosses turn/connection boundaries without an application word quota or silent audio loss. Real resource/provider failures are visible. | 1, 3, 4 |
| AF-13 | Keys are runtime-only, encrypted at rest, absent from source/logs/artifacts, and removable from the phone. | 1, 3, 5 |
| AF-14 | One intentional Actions run produces a verifiable signed APK; updating N to N+1 preserves existing data. | 1, 5 |
| AF-15 | Timing and resource measurements are reported with real device/network conditions and compared with stated targets. | 4 |

### Minimum editor/device matrix

Use a small diagnostic harness for repeatable controls, then verify actual applications. Record exact names/versions during execution rather than assuming every editor in a category behaves alike.

| Surface | Behaviors to exercise |
| --- | --- |
| Native single-line field | Insertion in middle of existing text, selected replacement, cursor move, keyboard autocorrect, and an unfinished Gboard composing word at the initial tap. |
| Native multiline field | Paragraphs, newline-containing content, long surrounding text, mixed manual and dictated input. |
| Browser textarea | Live replacement, focus transitions, page navigation, keyboard retained. |
| Browser contenteditable / rich editor | Selection representation, formatting preservation, immediate editor normalization. |
| Everyday messaging compose field | Draft preservation and no Send action; switching conversations during an in-flight result. |
| Search/URL field | No implicit submission or unwanted navigation; editable suggestions changing the field. |
| Terminal-like or custom input | Whether insertion/readback/replacement represent editable text or input events; never inject a newline as an unintended command. |
| Protected input | Explicit capability/failure handling without exposing protected contents to history or model requests. |
| Fold6 folded/unfolded and rotation | Reachability, positions, focus lifecycle, ongoing capture, no duplicate overlay. |
| Device interruption states | Bluetooth changes, competing microphone use, lock, permission revoke, service reconnect, connectivity loss. |

A compatibility result must describe live insertion, live replacement, and data preservation separately. App launch success is not input compatibility. Any unresolved universal-input failure remains visible in the requirements report.

## 13. Planning evidence and remaining execution gates

### Completed during planning

- Consolidated the user's requested workflow, light correction policy, remembering preference, and instruction to use editable defaults.
- Researched current official Android input/overlay/foreground-service behavior, OpenAI transcription and pricing, and GitHub APK artifact delivery.
- Chose a native, single-process, personal BYOK direction with one editor writer and durable recovery.
- Defined the settings, source/revision ownership contract, meaningful failure handling, acceptance criteria, and phased build/validation plan.
- Confirmed that the current workspace is not an existing Git repository; no existing app baseline was available to inspect.

### To resolve during implementation without another design questionnaire

1. Bind the actual repository and permanent package/signing identity before the first signed install.
2. Verify the exact current native transcription handshake, account capability, chosen dependency, and compatible Android build tuple.
3. Demonstrate microphone eligibility and safe live replacement on the real Fold6 while retaining the selected keyboard.
4. Measure how conservative live corrections behave with the user's speech and representative editors.
5. Verify crash recovery, long-session renewal, signed APK delivery, and update data retention.

The next concrete engineering result should be a real bubble-to-field prototype and its device evidence. Settings polish and additional features follow that result. Documentation is complete for this stage; runtime feasibility remains a fact to demonstrate.

## Sources

Sources were checked on 10 October 2026. Product requirements and proposed architecture are original design decisions; source links support the platform/API facts, not claims that AlienFlow has already been built. Prices, model availability, and tooling must be rechecked at implementation time.

- **S1.** Wispr, [Starting your first dictation on Android](https://docs.wisprflow.ai/articles/5906198129-starting-your-first-dictation-on-android).
- **S2.** Android, [AccessibilityInputConnection reference](https://developer.android.com/reference/android/accessibilityservice/InputMethod.AccessibilityInputConnection).
- **S3.** Android, [AccessibilityServiceInfo reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo).
- **S4.** Android, [Developing an accessibility service codelab](https://codelabs.developers.google.com/codelabs/developing-android-a11y-service).
- **S5.** Android, [Restrictions on starting foreground services from the background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).
- **S6.** OpenAI, [Realtime transcription guide](https://developers.openai.com/api/docs/guides/realtime-transcription).
- **S7.** OpenAI, [gpt-live-transcribe model](https://developers.openai.com/api/docs/models/gpt-live-transcribe).
- **S8.** OpenAI, [gpt-6-luna model](https://developers.openai.com/api/docs/models/gpt-6-luna).
- **S9.** OpenAI, [Deprecations](https://developers.openai.com/api/docs/deprecations).
- **S10.** OpenAI, [Realtime WebSocket guide](https://developers.openai.com/api/docs/guides/voice-websockets?voice-api=realtime).
- **S11.** OpenAI, [Realtime WebRTC guide](https://developers.openai.com/api/docs/guides/voice-webrtc?voice-api=realtime).
- **S12.** OpenAI, [Realtime conversations and session lifecycle](https://developers.openai.com/api/docs/guides/realtime-conversations).
- **S13.** Android, [Android Keystore system](https://developer.android.com/privacy-and-security/keystore).
- **S14.** OpenAI, [Best practices for API key safety](https://help.openai.com/en/articles/5112595-best-practices-for-api-key-safety).
- **S15.** OpenAI, [Data controls](https://developers.openai.com/api/docs/guides/your-data).
- **S16.** OpenAI, [gpt-transcribe model](https://developers.openai.com/api/docs/models/gpt-transcribe).
- **S17.** Android, [AGP 9.4 release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes).
- **S18.** Android, [Built-in Kotlin support](https://developer.android.com/build/migrate-to-built-in-kotlin).
- **S19.** GitHub, [Control workflow concurrency](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency).
- **S20.** GitHub, [Secure use of Actions](https://docs.github.com/en/actions/reference/security/secure-use).
- **S21.** Android, [Sign your app](https://developer.android.com/studio/publish/app-signing).
- **S22.** Android, [Version your app](https://developer.android.com/studio/publish/versioning).
- **S23.** GitHub Actions, [upload-artifact](https://github.com/actions/upload-artifact).
- **S24.** OpenAI, [Create a Realtime client secret](https://developers.openai.com/api/reference/resources/realtime/subresources/client_secrets/methods/create).
- **S25.** AOSP, [Finish ordinary composing text when accessibility input commits](https://android.googlesource.com/platform/frameworks/base/+/d92f3fca7402f54ae59b8fc4a450878332009531%5E%21/).


## Owner amendment — 2026-10-12 / AlienFlow2 0.1.9

0.1.8 live dictation is owner-confirmed. Termux support is mandatory. The Termux terminal adapter keeps the existing keyboard and inserts finalized corrected text once after Stop, because terminal input cannot safely use a document-span replacement protocol. Terminal control characters become spaces; AlienFlow never submits a command. Ordinary document fields retain live text and correction behavior. Quiet successful completion replaces the technical notice beside the bubble. The brand mark is redesigned with a silver alien and dual light/dark border for visibility on either background. These changes do not certify Google Play release readiness.


## Owner amendment — 2026-10-12 / AlienFlow2 0.1.10

The 0.1.9 icon is owner-approved. Routine status and expected manual-edit messages must not appear beside/on the icon. Useful diagnostic errors may appear while debugging; a persisted setting disables all overlay notices. Manual edits preserve the user's field and pause automatic replacement while capture continues; a new dictation starts fresh from the currently focused field. Genuine insertion failures must not be hidden or treated as verified writes. Terminal-to-document switching clears old terminal binding and stale notices. Final capture serialization must complete before final audio commit and WAV closure.


## Owner amendment — 2026-10-12 / AlienFlow2 0.1.11

Tap-to-listening must be as quick as the physical microphone allows. Start microphone capture concurrently with network setup after Android foreground-service permission gates; preserve startup audio and turn order, including a quick Stop before setup completes. Do not keep an idle microphone running. Expose a very small, elegant indicator on the accepted icon: first actual captured frames mean safe to speak, and validated server acknowledgement has a distinct connected treatment. Routine overlay status text remains disabled. Automatic low-latency tuning must preserve guarded editor writes, private recoverable audio, keyboard choice and terminal behavior. Measure real phone startup boundaries in metadata-only Diagnostics before claiming a latency number or choosing a provider from marketing figures.


## Owner amendment — 2026-10-12 / AlienFlow2 0.1.12

After Termux dictation, the bubble must return in a newly focused ordinary eligible field without toggling accessibility or restarting AlienFlow. Resolve current input focus after asynchronous editor/window publication; do not retain the previous terminal as the new editor. Preserve explicit Hide, protected-field exclusion, keyboard choice, guarded writes and the 0.1.11 latency/readiness behavior. Availability repair must not adopt a new write target during an ongoing dictation.

## Owner refinement — public-product polish, 2026-10-12

Treat AlienFlow as a product intended for paying public users. Settings must use familiar language and short, hierarchical pages; explain what each choice changes. Separate account/setup, ordinary preferences, history/recovery and technical troubleshooting. Do not expand full transcripts or an ever-growing history below Settings or setup. Use a compact, searchable history list and individual transcript pages. Preserve the accepted icon and existing dictation/keyboard/Termux behavior. This refinement does not itself assert Play Store readiness, phone validation or implementation of future storage/retention options.

The owner additionally requires an always-visible live appearance preview beside size/visibility controls, a cohesive silver/gray theme matching the accepted icon, and light tactile feedback for real listening/finishing transitions. Ordinary-field editing must not require first using Termux. Prefer general Android editor capabilities over per-app rules. Diagnose the current sync/ChatGPT failures without asserting installed-app support from framework fixtures alone. Preserve snappy capture and buffered initial speech.

## Owner correction — prompt-like corruption and selected ChatGPT composer, 2026-10-12

The 0.1.13 owner observation overrides any implication that ordinary-field support passed on the phone. Do not splice placeholder or merged label text into dictation. The existing whole-field replacement prohibition applies to unrelated user text; a node action may update a field only when its entirety is a positively established dictation-owned span from an initially empty field. Existing drafts require range edits. Resolve virtual input focus through the actual input-focused application window, not the touched/active window or an app-name rule. Preserve the accepted public interface and snappy capture. A screenshot of a selected composer with AF-E01 is a focus-discovery failure observation; precise installed framework behavior and transcription provenance remain facts to establish.


## Owner regression — 2026-10-12 / AlienFlow2 0.1.19

The supplied 0.1.18 Fold6 diagnostic overrides any implication that ChatGPT and Chrome acceptance passed. An explicitly anchored keyboard window may extend beyond the accessibility text representation. Authorize a ranged edit only when every shared character, absolute selection and complete mutation range agree. Preserve all IME-only suffix characters by leaving them outside the edit; never guess or trim them. A representation fixture is regression evidence, not installed-device certification.
