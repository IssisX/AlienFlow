# AlienFlow 👽

Personal native Android dictation with a movable alien bubble, live text in the focused field, very light corrections, and a user-supplied OpenAI API key.

## Signed diagnostic build · 0.1.3

Version **0.1.3** adds a private crash diagnostic, because the owner reports that tapping the alien in 0.1.2 causes an Android process crash. It records fixed startup checkpoints and sanitized Java/Kotlin crash source locations, without capturing key, speech, field text, or exception messages. Reopen the app after reproducing, tap **Copy crash diagnostic**, and send the result to the development agent. For a native crash the last checkpoint may remain even if there is no Java exception record. This update is **diagnostic only**; it does not claim the crash cause is fixed.

[GitHub Actions run 38066975001](https://github.com/IssisX/AlienFlow/actions/runs/38066975001) passed 56 tests, release lint with zero errors, assembly and package verification for compiled source `972fbab4e28c8addd8f02ac55062d470a8e9964c`. The signed [0.1.3 verification record](docs/RELEASE_VERIFICATION_0.1.3.json) ties the actual installer to the existing signing certificate and package ID. Update over 0.1.2; do not clear saved settings or keys.

## Previous test build · 0.1.2

Version **0.1.2** repairs editor readiness and readback: empty-field placeholders are no longer mistaken for typed contents, documented unknown offsets can be corroborated against the complete field, and blocking keyboard reads no longer run on the main thread. [Actions run 38065273911](https://github.com/IssisX/AlienFlow/actions/runs/38065273911) passed **54 tests**, release lint with zero errors (20 warnings), assembly and package checks. This includes six Android-framework widget test executions at APIs 33 and 35 that reproduce the old placeholder mismatch and exercise insertion/revision. Those are Robolectric tests, not physical accessibility-service or microphone tests.

The signed APK uses the original certificate and package ID, with versionCode 3. Install **AlienFlow-0.1.2.apk** over the previous version; do not uninstall or clear app data. The signed installer is delivered in the development conversation. [Version 0.1.2 verification](docs/RELEASE_VERIFICATION_0.1.2.json) records exact source, artifact and signed APK hashes; [signature output](docs/APK_SIGNATURE_0.1.2.txt) records the certificate verification.

The owner has enabled accessibility and passed the key/session check. Tap and long-press reach the bubble. The most recent phone report is an editor-readiness rejection in 0.1.1. The 0.1.2 repair is built and tested, but **live microphone capture and in-field transcription on the Fold6 still need device confirmation**. See [implementation status](docs/IMPLEMENTATION.md) and [device tests](docs/DEVICE_TESTS.md).

Earlier release evidence remains available for [0.1.1](docs/RELEASE_VERIFICATION_0.1.1.json) and [0.1.0](docs/RELEASE_VERIFICATION.json). The historical review is tied to its recorded revision; it is not a review of every later update.

[Pull request #1](https://github.com/IssisX/AlienFlow/pull/1) contains the implementation and open phone-acceptance work. Future agents should start from `build/first-apk`, read `AGENTS.md`, and inspect current state before editing.

The initial target is a Galaxy Z Fold6: ARM64, Android 13 or newer. The architecture keeps Gboard selected, using Android's accessibility input connection and a nonfocusable accessibility overlay.

## Defaults

- A draggable alien bubble with a 32 dp visible image and at least 48 dp touch target. Idle default is **75% opaque (25% transparent)**, adjustable in Settings.
- Tap to start/stop; live words directly in the focused field, with status beside the bubble.
- Very light live corrections preserving wording, negation, numbers, names, slang and profanity.
- Seven days of lightweight text history; completed audio deleted after successful processing; interrupted audio kept privately for recovery.
- Local capture and reconnect attempts on a connection loss. Current recovery happens after Stop into history/copy; seamless inline gap replay remains unfinished.
- No AlienFlow word quota. Provider limits, connectivity and device resources still apply.

Appearance and main behavior settings are in the app. This is an original personal implementation inspired by the dictation workflow, with its own native UI and alien artwork.

## Phone setup

Install the **signed APK**, not the unsigned APK inside the development bundle. Existing users should update in place and retain their saved key/settings.

For a new installation, open AlienFlow, enter your own API key and allow the microphone. If Android's accessibility list shows **Controlled by Restricted Setting**, first open **Settings → Apps → AlienFlow → ⋮ / More → Allow restricted settings**, then return to **Settings → Accessibility → Installed apps → AlienFlow dictation** and enable it. See the [official Android instructions](https://support.google.com/android/answer/12623953?hl=en).

The accessibility overlay is distinct from an ordinary application overlay: it uses the enabled accessibility service rather than SYSTEM_ALERT_WINDOW / Appear on top. Do not request unrelated permissions as a substitute for diagnosing an editor failure. Microphone permission and actual foreground-service startup are separate requirements.

A successful **Check key & transcription model** result establishes OpenAI session access only, not microphone or editor operation. Keep the existing keyboard selected, focus the empty test field or a new note, and tap the alien. Wait for actual Listening before speaking. If an error remains, record its AF-E code/message without including API keys or private text.

Follow [the first-phone checklist](docs/DEVICE_TESTS.md) for text preservation, interruptions and updates. Real phone tests remain required even after CI succeeds.

## Development and handoff

- [Product specification](docs/PRODUCT_SPEC.md): intended behavior and acceptance criteria.
- [Current checkpoint](docs/IMPLEMENTATION.md): implementation, verification and remaining work.
- [Historical checkpoint through 0.1.1](docs/history/IMPLEMENTATION_THROUGH_0.1.1.md): prior observations and release history, preserved verbatim.
- [Original native implementation](docs/APP_IMPLEMENTATION.md), [audio storage](docs/AUDIO_STORAGE.md) and [protocol](docs/PROTOCOL.md): source interfaces and historical decisions.
- [Build evidence](docs/BUILD_EVIDENCE.md) and [release instructions](docs/RELEASE.md): toolchain and permanent signing process. Current versions live in build configuration.
- [0.1.2 verification](docs/RELEASE_VERIFICATION_0.1.2.json) and [signature output](docs/APK_SIGNATURE_0.1.2.txt): exact delivered bytes.
- [Historical independent review](docs/CODE_REVIEW.md): findings at the exact revision stated there, not a blanket endorsement of later changes.
- [Agent contract](AGENTS.md): rules for future development agents.

## Build

GitHub Actions requires passing unit tests, release lint and release assembly, then verifies source/package identity, native alignment and artifact hashes. It produces an aligned **unsigned** bundle with reports; the permanent private key stays outside the repository. Final installation APKs are privately signed and verified.

```bash
bash gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

CI establishes the checks actually run there, not physical-device microphone/editor behavior or complete product acceptance.
