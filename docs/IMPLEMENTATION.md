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
