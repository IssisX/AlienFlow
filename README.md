# AlienFlow 👽

Personal native Android dictation with a movable alien bubble, live text in the focused field, very light corrections, and a user-supplied OpenAI API key.

## First signed test APK · 0.1.0

The 0.1.1 maintenance update changes the default bubble to **75% opaque** and shows a non-intercepting status notice beside the bubble when tapped, including explicit editor/microphone startup errors. This update is not device-verified yet. Follow the status of the newest GitHub Actions run before installing; the 0.1.0 APK remains the previous signed release until the new build is signed and verified.

The first APK has been built, independently reviewed, privately signed and verified. The implementation is on `build/first-apk`; [Actions run 38058270344](https://github.com/IssisX/AlienFlow/actions/runs/38058270344) passed all 36 tests, release lint with zero errors, APK assembly and package checks. The signed APK is provided in the development-session handoff. Its exact source revision, hashes and signature are recorded in [release verification](docs/RELEASE_VERIFICATION.json).

The owner reports that the app opens and its key/session check succeeds. The current phone blocker is Android's **Controlled by Restricted Setting** gate on accessibility; the per-app unlock is below. Microphone capture, live provider transcription and compatibility with everyday editors still need to be exercised on the device. See [implementation status and remaining work](docs/IMPLEMENTATION.md).

[Pull request #1](https://github.com/IssisX/AlienFlow/pull/1) preserves the reviewed implementation and its open phone-acceptance checklist. A future development session should start from this branch and read `AGENTS.md`.

The initial target is a Galaxy Z Fold6: ARM64, Android 13 or newer. Gboard stays selected. AlienFlow uses Android's accessibility input connection and a nonfocusable accessibility overlay to reach the active field.

## Defaults

- A draggable alien bubble with a 32 dp visible image and a 48 dp touch target. Updated default is 75% opaque (25% transparent), adjustable in Settings.
- Tap to start/stop; show live text directly in the focused field.
- Very light live corrections that preserve wording, negation, numbers, names, slang and profanity.
- Seven days of lightweight text history; successful recordings deleted; interrupted audio kept privately for recovery.
- Keep recording locally on a connection loss and retry the live connection. Recover uncertain audio after stopping without writing into a newly focused field.
- No AlienFlow word quota. The owner's OpenAI account, connectivity and available device storage determine practical limits.

Appearance and main behavior settings live in the app. This is an original personal implementation inspired by the dictation workflow; it uses its own native UI and alien artwork.

## Phone setup

Install **AlienFlow-0.1.0.apk**, the signed APK from the release delivery. The `unsigned-release-bundle` is an intermediate build artifact that requires signing before installation.

Open AlienFlow, enter your own API key and allow the microphone. If Android's accessibility list shows **Controlled by Restricted Setting**, unlock the app first:

1. Open **Settings → Apps → AlienFlow**.
2. Open the **⋮ / More** menu at the top right and choose **Allow restricted settings**. Complete any phone confirmation.
3. Return to **Settings → Accessibility → Installed apps → AlienFlow dictation** and turn it on.

This is Android's per-app setting; see the [official instructions](https://support.google.com/android/answer/12623953?hl=en). A successful **Check key & transcription model** result tests OpenAI session access only; it does not establish accessibility or microphone readiness. Leave Gboard selected and start with an empty note. API keys are entered and encrypted on the phone; no key is bundled in the APK or repository.

Follow [the first-phone checklist](docs/DEVICE_TESTS.md) to verify recording, text preservation, corrections, interruptions and updates on the real device.

## Development and handoff

- [Product and engineering specification](docs/PRODUCT_SPEC.md): requirements, architecture, defaults and acceptance criteria.
- [Implementation checkpoint](docs/IMPLEMENTATION.md): implemented behavior, evidence, known gaps and next steps.
- [Native app implementation](docs/APP_IMPLEMENTATION.md): actual UI, dictation, correction, persistence and recovery behavior, with device-dependent limits.
- [Audio recovery implementation](docs/AUDIO_STORAGE.md): durable recording, bounded upload chunks and regression coverage.
- [Protocol evidence](docs/PROTOCOL.md): exact native WebRTC interfaces and provider integration assumptions.
- [Build evidence](docs/BUILD_EVIDENCE.md): pinned versions, verified upstream sources and local checks.
- [Build and release instructions](docs/RELEASE.md): GitHub Actions, signing identity and safe upgrades.
- [Release verification](docs/RELEASE_VERIFICATION.json) and [signature output](docs/APK_SIGNATURE.txt): exact delivered bytes and signing identity.
- [Independent code review](docs/CODE_REVIEW.md): repaired findings, exact reviewed source hashes, minor follow-ups and remaining phone checks.
- [Agent contract](AGENTS.md): rules for future AI development agents.

## Build

GitHub Actions runs unit tests, release lint and release assembly. It produces an aligned **unsigned** release bundle with reports and source hashes. The permanent private key is kept separately; the final delivery is signed with that key and verified before being described as installable. See [RELEASE.md](docs/RELEASE.md).

```bash
bash gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

A successful CI run establishes compilation and static tests. Device behavior and provider account access require the phone checklist and real API calls.
