# Background spending incident — 17 October 2026

The owner reports three balance drains, approximately $25 total, while AlienFlow appeared idle. Treat this as a release blocker. Do not ask the owner to replenish API credit for testing. The owner confirms the latest incident used Claude’s newer build; the exact installed point release and account usage records have not been collected; a reproduced code defect does not by itself attribute every reported charge.

## Confirmed source defects and current baseline

The automatic-upload and cancellation defects exist in both `39a8e71bcbdff6397e056c211c053bf07b96da5c` (.25 chat) and `eff228559b8fc26e1a0ae7dbd15e903649d341eb` (.28 Claude). The latter is the representative current-line regression baseline. Older .25 capture defaults are distinguished below:

- Finishing an interrupted session could automatically call `recover()` and upload its entire saved WAV for another paid transcription, after `active` and `finishing` were both false.
- Stop and Cancel returned immediately in that state. Recovery used synchronous HTTP and had no cancellation hook tied to the request. Screen lock, leaving the activity and service loss also failed to cancel idle-state recovery.
- In .25, quiet auto-stop defaulted to Off. Audio was transmitted throughout an active session, including quiet frames, and there was no independent maximum session lifetime. Ambient sound is not reliable evidence of intentional continued dictation.
- Local fallback attempted automatic realtime reconnection, while recovery could later reupload the entire recording.

Claude’s .28 added a silence gate, a default 15-second quiet stop and a configurable 15-minute session cap. Those controls do not cancel the automatic upload after dictation ends. Its statement that nothing runs in the background was incorrect.

These are concrete mechanisms for unintended/background API activity. The historical diagnostic excerpt covers editor failures, not account usage. The owner’s latest report must not be dismissed because those earlier logs do not contain a connection error.

## Repair contract

- New and upgraded installs start with `paid_api_enabled=false`. The existing encrypted key is retained. Opening AlienFlow, accessibility readiness and editing settings never enable this preference or initiate paid work.
- Account/setup and the home screen expose Pause all API activity. It persists the pause, closes active capture/connection, cancels HTTP cleanup, recovery and connection checks, and preserves unfinished audio/text. Removing a key also pauses and cancels work.
- Saved-audio recovery requires an explicit user action, identifies itself as paid, is cancelable even while dictation is idle, stops on leaving the activity/recovery page or screen lock, and has a two-minute total deadline. Each HTTP call has a 45-second deadline and automatic connection retry is disabled. Cancellation cannot refund work already processed by the provider.
- A dropped realtime connection can continue **local recording only**. It does not automatically reconnect or trigger a second transcription upload.
- The newer 15-second quiet default remains. Quiet capture stops within the selected 1–30 seconds; legacy Off migrates effectively to 30 seconds. The configurable 1–5-minute cap defaults to five, with coordinator and capture-thread deadlines bounding each active session even without another audio callback or with continuous background noise. A new intentional tap starts a new session. This is an incident-driven duration safeguard, not a word quota or an account dollar cap.
- Switching eligible apps/fields during an active session still follows the established epoch/ownership contract. Existing keyboard, Termux final-only/no-Enter delivery, API 36 and APK-only development remain required.

## Verification requirements

`BillingShutdownRegressionTest` runs unchanged against the shipped source in an isolated worktree. It must reproduce automatic recovery after Stop and ineffective Cancel by assertion, then pass on the candidate. Loopback HTTP exercises actual coordinator cancellation and retained recordings; it is not a paid-provider evaluation.

Additional tests cover idle Stop, screen lock, service loss, activity pause, persisted API pause, zero requests on launch with access disabled or enabled, actual connection-check socket cancellation, quiet-stop transport behavior, no automatic reconnection, and the five-minute coordinator watchdog. Historical editor, Termux, writing and Android 16 integration gates stay required.

At preparation time these changes are a candidate, not a verified APK. Record the final source SHA, workflow result, exact test counts and signer evidence after the checks complete. No real API calls, billing-account inspection or physical Fold6 acceptance have been performed. Public paid release remains blocked.
