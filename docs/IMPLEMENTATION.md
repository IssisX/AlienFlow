# Implementation checkpoint

Date: 2026-10-10.

## Authority and baseline

- User authorized implementation and supplied the new IssisX/AlienFlow repository.
- Remote default branch: main. Initial README commit: 773aa09b99fbc9bf5e906158f05ad1d9706cb861.
- Local working branch: build/first-apk. No pre-existing application code or repository instructions existed.
- Product requirements: PRODUCT_SPEC.md. The historical planning status inside that document describes its creation checkpoint; this file records subsequent execution.
- Application ID selected before first install: io.github.issisx.alienflow.

## Current status

The native application is implemented on `build/first-apk`. Revision `33ba2d8f3f7f5d40747bfe532d1af43ea52a530e` passed Kotlin compilation and the unit-test task and assembled the unsigned release APK. Release lint found one error: an editor flag used a numeric literal instead of Android's named constant. That correction and explicit backup/launcher resources are being prepared for the next exact-source build.

Independent review found no remaining important source blocker for a first test APK at the third compiler checkpoint, subject to rechecking later changes. This is not full product acceptance: final release checks, signing, installation, real API dictation and device compatibility are still pending. The private permanent signing key and backup exist; its public fingerprint is in RELEASE.md.

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
