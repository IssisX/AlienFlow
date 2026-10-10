# Implementation checkpoint

Date: 2026-10-10.

## Authority and baseline

- User authorized implementation and supplied the new IssisX/AlienFlow repository.
- Remote default branch: main. Initial README commit: 773aa09b99fbc9bf5e906158f05ad1d9706cb861.
- Local working branch: build/first-apk. No pre-existing application code or repository instructions existed.
- Product requirements: PRODUCT_SPEC.md. The historical planning status inside that document describes its creation checkpoint; this file records subsequent execution.
- Application ID selected before first install: io.github.issisx.alienflow.

## Current status

Source implementation and independent static review are in progress. The private permanent signing key has been created and its backup saved; the public certificate fingerprint is recorded in RELEASE.md. Remote branch build/first-apk is initialized at the baseline. Local build-file syntax and official wrapper checksum checks passed.

Application source implementation in progress. No APK compilation, signing, installation, real API dictation, or device compatibility has yet been verified.

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
