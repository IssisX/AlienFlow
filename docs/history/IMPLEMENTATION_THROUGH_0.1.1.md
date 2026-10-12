# Implementation checkpoint

Date: 2026-10-10.

## Authority and baseline

- User authorized implementation and supplied the new IssisX/AlienFlow repository.
- Remote default branch: main. Initial README commit: 773aa09b99fbc9bf5e906158f05ad1d9706cb861.
- Local working branch: build/first-apk. No pre-existing application code or repository instructions existed.
- Product requirements: PRODUCT_SPEC.md. The historical planning status inside that document describes its creation checkpoint; this file records subsequent execution.
- Application ID selected before first install: io.github.issisx.alienflow.

## Current status

**AlienFlow 0.1.0 is built, signed, verified, and delivered as the first phone-test APK.** The implementation is on `build/first-apk`. The exact compiled source is `c76b3ccffd7b3072da1a179ebf5f19b39cc7238e`. Later documentation-only commits record delivery evidence without changing the compiled application.

[Actions run 38058270344](https://github.com/IssisX/AlienFlow/actions/runs/38058270344) completed successfully. All 36 tests passed; release lint had zero errors and 18 warnings; release assembly, identity checks, native alignment checks and delivery upload passed. The downloaded bundle and every manifest-listed entry matched their hashes. The privately signed APK verified with the permanent certificate. See [machine-readable release evidence](RELEASE_VERIFICATION.json) and [actual signature output](APK_SIGNATURE.txt).

Independent review found no remaining blocking or important source defect for a **first test APK**, including the final manifest/resources. The owner has since reported opening the installed app and passing its key/session check; the phone observation below records the narrower evidence. Cold bubble microphone startup, live provider behavior, cross-editor compatibility and signed update preservation still require phone verification. This checkpoint does not declare the entire product specification complete.

## Phone setup observation · 2026-10-10

- **Owner report:** the installed app opens and **Check key & transcription model** finds the model and reports session availability. The installed APK hash was not independently read from the phone.
- **Observed screenshots:** Android's accessibility list shows **AlienFlow dictation** and **Termux:X11 KeyInterceptor** disabled with **Controlled by Restricted Setting**. The screenshots establish an OS activation restriction, not a running-service failure. They do not establish when or why Termux:X11's restriction changed.
- **Source boundary:** `DictationCoordinator.testKey()` requests a client secret via `OpenAiClient.clientSecret()` and reports session availability. It does not connect WebRTC, capture microphone audio, connect the accessibility service or insert text. Successful provider access can therefore coexist with the observed permission blocker.
- **Supported remedy:** **Settings → Apps → AlienFlow → ⋮ / More → Allow restricted settings**, complete the phone confirmation, then **Settings → Accessibility → Installed apps → AlienFlow dictation → On**. This is the [official Android per-app unlock](https://support.google.com/android/answer/12623953?hl=en). Termux:X11 has its own per-app setting; the screenshots do not support AlienFlow having changed it.
- **Change scope:** promoted the existing device-checklist instructions into README phone setup. No application code, package identity, signing identity or APK changed; a rebuild does not remove this OS-owned permission prerequisite. The current onboarding UI opens accessibility settings without showing the restricted-setting help; adding that guidance remains a usability follow-up.
- **Pending acceptance:** the owner must perform the unlock on the phone, enable the service and confirm bubble appearance, real microphone capture and live words in a focused note. Those outcomes remain unverified. No dictation runtime check is promoted by the key/session test.

## What the APK contains

| Area | Implemented behavior |
| --- | --- |
| Bubble and keyboard | Original alien overlay, 32 dp image, at least 48 dp touch area, 25% idle opacity, dragging, remembered folded/unfolded positions, and appearance settings. Existing Gboard selection is retained by the architecture. |
| Dictation | Native WebRTC microphone transport to OpenAI, provisional/final transcript processing, guarded live insertion, tap or hold controls, optional silence stop and end-only display. No app word allowance. |
| Corrections | A faithful; B very light by default; C optional rewrite. Live or end timing, optional filler removal, language and vocabulary settings. Conservative B guards preserve protected content and reject ambiguous edits. |
| Private data | Android Keystore encryption for the runtime API key; private atomic text history with stable IDs and configurable retention; cloud/device transfer exclusions. No runtime key in source or CI. |
| Recovery | Same-microphone bounded WAV capture, durable headers, source-preserving recovery chunks, disconnect capture and reconnect attempts, single-flight recovery after stopping, copy/history results. |
| Release | Pinned build inputs, GitHub Actions test/lint/package checks, permanent private signing identity and a documented update process. |

The [app implementation notes](APP_IMPLEMENTATION.md), [audio storage notes](AUDIO_STORAGE.md) and [protocol evidence](PROTOCOL.md) explain the actual interfaces and assumptions.

## Remaining product work and first phone gate

The next action is the [first-phone checklist](DEVICE_TESTS.md), starting with the app's own empty test field, then an empty note with the activity closed and Gboard still selected. Enter the API key on the phone. Confirm actual live words before Stop, final text after Stop, and microphone release. Test deliberate typing/caret/focus changes before using important drafts. Record Android/One UI version and the observed result, without publishing personal dictation or credentials.

These are material limits of 0.1.0:

- Reconnection retains capture and attempts a new live connection, but it does not resume inline editing across the gap. The whole recording is recovered after Stop into history/copy. Seamless live replay and durable per-part recovery checkpoints remain work; retrying a failed recovery can repeat earlier provider uploads.
- Nonempty fields with unknown composing state and incomplete/inconsistent readback are conservatively refused. A keyboard composing word must be finished first. Uniform behavior across arbitrary custom editors remains an acceptance goal, not verified coverage.
- Recovery recognition does not run the live correction pass. Recognition around file-chunk boundaries and trailing RTP audio need real speech tests.
- Local image import/alien variants, edge snap, completed-audio history, configurable pending retention beyond the current 24 hours, usage/cost reporting and per-entry restore/compare remain unimplemented. Existing settings control actual behavior; absent features have no dummy switches.
- One standard RIFF WAV has an approximately 4 GiB format ceiling. Audio queues/uploads are bounded; there is no speech-word counter or subscription gate. Network, provider, storage and file-format limits still apply.

Future agents should resolve observed microphone/protocol/editor failures before dependent polish. Preserve the package ID and use the existing private signing backup for upgrades; increment versionCode. The complete intended behavior and acceptance criteria remain in PRODUCT_SPEC.md.

The [independent review](CODE_REVIEW.md) also records two minor follow-ups: snapshot the language once for a multi-part recovery, and report that no audio was captured when startup stops before the first frame. They remain documented in this test build; they do not represent lost captured audio or a waived release error.

## Implementation decisions

- Work in a fresh isolated clone, preserving main's baseline. There is no existing worktree or app state to replace.
- Use native Android Views for the first APK instead of Compose. This keeps the setup/settings UI functional while reducing plugin and dependency integration work; it does not change the requested behavior. Local persistence is implemented directly using Android facilities rather than introducing Room/DataStore code generation into this first build.
- Build through GitHub Actions because the local environment has a Java runtime but no Android SDK/compiler, and the attempted Gradle distribution download was blocked by network policy.
- Keep signing private material outside the public repository. If GitHub signing secrets are not provisioned, Actions may build an unsigned release candidate for private local signing with one persistent key; an unsigned artifact is never presented as installable.
- Implement the first complete native dictation path before claiming later device-dependent milestones complete.

## First compiler checkpoint

- Source: `659088d25082f70ddd65dca1be2fbb1cd45a6cb4` (branch `build/first-apk`).
- Remote tree exactly matched the staged local Git tree before the branch update.
- [GitHub Actions run 38057134203](https://github.com/IssisX/AlienFlow/actions/runs/38057134203) was observed running for that SHA. Failed during SDK installation: `platforms;android-37` was unavailable. The app compiler, tests, lint and APK packaging did not run. Java provisioning and wrapper validation passed. The separate Gradle cache-cleanup success does not validate the app.
- Subsequent review fixes and the dedicated pending-audio test file are separate working changes. They must be compiled and tested on a subsequent exact revision before final delivery.

SDK correction: use published stable API 36 for compile/target and explicit SDK channel 0. Exact Google repository metadata supplies platform revision 2; no API 37 platform package was published in the queried feed. See BUILD_EVIDENCE.md. This changes the compiler target, not the accessibility architecture (minimum API 33).

## Second compiler checkpoint

- Source: `cf391cd212df6596d709ba06d148653d842771ff`; staged local tree and published Git tree both `8948abf17adfe406511b9c34b1355eb2ab944487`.
- [GitHub Actions run 38057441585](https://github.com/IssisX/AlienFlow/actions/runs/38057441585) installed the corrected SDK and compiled the app.
- Unit tests: 32 executed, one failure in `PendingAudioTest.validAuthoritativeHeaderExistsBeforeCloseAndActiveRepairIsRefused`. WAV finalization encountered `ClosedByInterruptException`. The failure is assigned to the audio owner for a behavior fix and regression coverage; no test gate is being bypassed.
- Separate review repairs address screen-lock microphone shutdown, cancellation during asynchronous history finalization, cancellation between paid cleanup chunks, and preserving local audio when the provider cannot confirm the committed buffer. These working changes require a subsequent exact-source CI run and review.

## Third compiler checkpoint

- Source: `33ba2d8f3f7f5d40747bfe532d1af43ea52a530e`; staged local tree and published Git tree both `f7861d0fdddc427d60d651e8a068efd87c3072f7`.
- [GitHub Actions run 38057983963](https://github.com/IssisX/AlienFlow/actions/runs/38057983963) passed debug/release Kotlin compilation, `testDebugUnitTest` and `assembleRelease`. This revision contains 36 test cases: 14 dictation-logic and 22 audio-storage cases.
- The original audio failure and its new deterministic interrupted-writer regression passed the unit-test task. An independent local Java reproducer also failed with `ClosedByInterruptException` before the repair and finalized successfully after the repair, while restoring the caller's interrupt status.
- `lintRelease` failed with one `WrongConstant` error at `EditorBridge.getSurroundingText`: replace `1` with `InputConnection.GET_TEXT_WITH_STYLES`. No lint baseline or suppression is being added. Packaging and delivery upload were correctly skipped.
- The 19 warnings were inspected. Explicit Android data-extraction exclusions and an actual launcher icon are being added. Remaining warnings concern deliberately pinned tool/dependency versions and SDK, the ARM64-only device target, English-only UI, small draw-time allocation, and intentionally absolute overlay coordinates. They remain visible in the lint report.

## Verified delivery checkpoint

- Compiled source: `c76b3ccffd7b3072da1a179ebf5f19b39cc7238e`; local and remote tree: `c5eed8056f59e16399da70f06bd49b18714a9aa8`.
- [Successful Actions run](https://github.com/IssisX/AlienFlow/actions/runs/38058270344): 36 executed tests, zero failures/errors/skips; zero release lint errors and 18 warnings; release assembly and package/upload checks passed.
- Artifact `11672260565`, `AlienFlow-0.1.0-unsigned-release-bundle.zip`: SHA-256 `bc881f7d35c63017d903439eb94234f4a8a147b44b6b53fddb9c378c910c453f`. Retrieved bytes and all bundled file hashes were verified before signing.
- Signed delivery: `AlienFlow-0.1.0.apk`, 17,029,305 bytes; SHA-256 `79e34f8abf7ddd8dce399b9742d55c6dc028a1d2984abb8c1dfa2bf7b516ed21`.
- APK Signature Scheme v3 verified successfully, one signer, certificate SHA-256 `d79e74b3a4c24ee225d1220c2c374c6f958520d4f3257fcfb399ff09bf5c965f`. Private key and password are excluded from the repository and Actions.
- ARM64, Android 13/API 33 minimum, compile/target API 36; release is not debuggable. Native ELF and APK alignment checks passed for 16 KiB pages in CI.
- Remaining lint warnings concern version-update suggestions, the ARM64 target, English UI, absolute overlay coordinates, small draw allocations and launcher resource/themed-icon polish. They were inspected and retained; no error baseline was added.
- At this artifact-delivery checkpoint, no device install, microphone, provider request, live editor, accuracy/latency or upgrade test was represented as passed. The later phone setup observation above records the owner's installation/key-check report and the screenshot-confirmed restriction separately.

## 0.1.1 status-feedback update · build verified (2026-10-10)

- **User observation:** the alien bubble appears after enabling accessibility but tapping it gives no visible response. This is phone evidence of an unresponsive *interaction*, not enough to distinguish touch delivery, editor refusal, or microphone foreground-service rejection.
- **Source cause of silent failure:** the bubble does forward a tap to `DictationCoordinator.toggle()`, but early rejections in `start()` only update `status` inside MainActivity. The app is normally closed while dictating, so the reason is invisible. Several early editor checks can reject input before `active=true`. There is no evidence yet that the microphone FGS or input connection passes on this phone.
- **Owner correction:** the requested appearance default is **75% opaque / 25% transparent**, not the prior 25% opaque. The actual Settings opacity slider remains adjustable (100% = fully opaque); configured settings are preserved on upgrade. UI labeling and product specification now agree.
- **Repair scope:** a nonfocusable, non-touchable transient status panel beside the bubble displays status transitions including tap/start refusals; no editor text, key or audio appears there. `EditorBridge` gives actionable diagnostics for unavailable editor connection/readback. Native editing and OS permissions are unchanged; no fake success or lowered checks. A small pure-JVM test asserts the opacity mapping.
- **Build verification:** [Actions run 38063401303](https://github.com/IssisX/AlienFlow/actions/runs/38063401303) passed 37 unit tests (15 logic + 22 audio), release lint (zero errors, 19 warnings), APK assembly, package checks, and artifact upload for source `a3d0aee38adb73b545b31db20e806d6f493d76e8`. Downloaded bundle SHA-256 `8c6e0e2e592be9ed6d2307cd9e930d563f543aa277223c447c17183ab98306bf` matched GitHub's digest; all 23 manifest-listed bundle hashes matched. Privately signed APK SHA-256 `438c9bb3b1fd6a876ac173a66bf58c376b20c679aab90d7523ff48f5a7dc9a0f`, size 17,029,305 bytes; APK v3 signature verified with permanent certificate `d79e74b3a4c24ee225d1220c2c374c6f958520d4f3257fcfb399ff09bf5c965f`. There was no app/code alteration after this compiled SHA.
- **Still unverified:** phone tap feedback, cold microphone foreground-service start, and actual in-field transcription. The Android 14+ microphone-service restrictions could block starting from the background, but there is no observed error from this phone yet. Test the status overlay before changing the capture architecture; do not mistake visible diagnostics for a completed dictation fix.
