# AlienFlow2 implementation checkpoint — 0.1.17

Repository: IssisX/AlienFlow. Branch: AlienFlow2. Date: 2026-10-12. VersionCode: 18.

## Requested behavior and observed failures

The owner reports long Stop delays after 10–15 seconds of speech, wants background readiness without keeping the activity open, and supplies a Chrome/ChatGPT screenshot showing [AF-E10] Editor sync timed out. The screenshot proves a before-mutation synchronization failure; it does not include node/keyboard metadata or prove a particular representation mismatch. The other screenshot lists two ongoing foreground-service apps while AlienFlow is idle. Android Active apps is a foreground-service list, distinct from Samsung Keep open.

Baseline local revision bacb64660432c787886cc16f22d2ffb7e513ba74 and remote checkpoint 47ca334f89c9e19195efb0c84de3b3ce75e92181 share tree 3fc1dae6897c63b8d88c54338a2f2d1b2da4f522. Checkout was clean. Preserve build/first-apk. Standing execution authorization is in AGENTS.md.

## Named source defects and repairs

Stop previously waited for serial optional cleanup requests with a 45-second HTTP read timeout. Coroutine cancellation did not cancel the blocking HTTP call. It also had an unconditional one-second finish floor and 1.2-second kick. Live cleanup is now genuinely cancellable, bounded to four seconds during speech, and canceled at Stop. Completed recognition and already accepted corrections remain; uncompleted live polishing never queues behind Stop. The explicit after-stop preference gets one combined, losslessly chunked request budget of 2.5 seconds; slow/unavailable polishing preserves recognized words. Audio drain and final transcript acknowledgements, rather than a fixed floor, authorize finishing. The transport’s actual unacknowledged-commit ledger also gates completion before UI counter callbacks arrive; removing a timer cannot close a stream early. Required capture/transcription/editor confirmation still must complete.

The keyboard path previously selected/replaced the entire owned transcript for every delta, requested the whole draft, and rejected every nonzero surrounding-text offset. It now replaces only the changed tail, preserving the confirmed common prefix and UTF-16 pairs. Native keyboard windows use their explicit Android offsets and exact absolute cursor/text alignment against the complete accessibility snapshot. The entire mutation range must be covered; unknown offsets still require full matching contents. No substring searching, blind SET_TEXT of existing drafts, automatic paste, app-specific allowlist, field adoption or submission was added. This repairs proven contract incompatibilities; the screenshot's exact mismatch and installed Chrome behavior remain unverified until phone observation.

The existing CaptureService now owns one foreground lifetime for background readiness, capture and delivery. It starts when the user-enabled accessibility service connects, shows a persistent Ready notification, promotes to microphone only after the user's dictation tap, and returns to specialUse readiness after capture. Capture resources still release at Stop; no idle recording occurs. Notification updates are skipped when status is unchanged. Hide alien stops idle readiness; Reset position restores it. Accessibility loss stops readiness. START_NOT_STICKY prevents recreating a microphone/editor session after process loss. API 33 uses an untyped readiness foreground service; API 34+ declares specialUse with its concrete subtype explanation. Google Play reviews this declaration; it is not proof of store approval or OEM persistence. Setup explains background readiness without extra routing/server switches.

Added safe stop-phase checkpoints distinguish transcript readiness, optional after-stop cleanup and saving without logging speech. Recognition model, minimal delay, 20-ms PCM frames, parallel startup, silver artwork, keyboard/overlay behavior, cancellation protections, Termux final-only/no Enter, vocabulary opt-in, no-reasoning cleanup, and request memo remain.

## Verification status

Initial candidate eeb5c3f243b2cdedbefffa942f3b1cb9b49cda4e failed CI run 38304774788: 185 tests, three failures. Two tests retained correct text/cursor outcomes but required the original initial selection dispatch; that behavior was restored. The third exposed an introduced stale required-range after placeholder disambiguation, leaving a coroutine waiting; initial evidence is now rebound to the resolved owner without relaxing guards or extending test deadlines. A held-commit regression also checks that removing the finish floor cannot skip acknowledgement. Corrected candidate CI, lint, assembly, signing, artwork/payload verification and delivery remain pending. Tests exercise production coordinator plus cancellable loopback HTTP (key and transcript fixtures), production bridge with native EditText/InputConnection and simulated bounded IPC, offset/coverage/UTF-16 guards, and the production service under Robolectric SDK 33/35. They do not prove paid provider performance, installed Chrome/Fold6 behavior, all editors, Samsung battery management, live billing or Play approval. No paid API call or microphone/device test was made.

## Primary sources

- https://developer.android.com/reference/android/view/inputmethod/SurroundingText — explicit offsets, unknown offset -1.
- https://chromium.googlesource.com/chromium/src/+/refs/heads/main/content/public/android/java/src/org/chromium/content/browser/input/TextInputState.java — surrounding-window text, relative selection and offset.
- https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping — Active apps lists foreground services.
- https://developer.android.com/develop/background-work/services/fgs/service-types — microphone and specialUse types; specialUse requires a reviewed subtype explanation.

The previous efficiency evidence is RELEASE_VERIFICATION_0.1.16.json. Sponsor-paid three-day trial credentials/expiry/spend enforcement are not implemented. This candidate is not a public store release.
