# AlienFlow 👽

Personal native Android dictation with a movable alien bubble, live text in the focused field, very light corrections, and a user-supplied OpenAI API key.

## First build

The first implementation is being compiled and reviewed on `build/first-apk`. See [implementation status](docs/IMPLEMENTATION.md) for actual evidence and remaining work. A working installation, live OpenAI session and universal editor compatibility are not established by source code or a passing build alone.

The initial target is a Galaxy Z Fold6: ARM64, Android 13 or newer. Gboard stays selected. AlienFlow uses Android's accessibility input connection and a nonfocusable accessibility overlay to reach the active field.

## Defaults

- A draggable alien bubble with a 32 dp visible image, at least 48 dp touch target, and 25% idle opacity.
- Tap to start/stop; show live text directly in the focused field.
- Very light live corrections that preserve wording, negation, numbers, names, slang and profanity.
- Seven days of lightweight text history; successful recordings deleted; interrupted audio kept privately for recovery.
- Keep recording locally on a connection loss and retry the live connection. Recover uncertain audio after stopping without writing into a newly focused field.
- No AlienFlow word quota. The owner's OpenAI account, connectivity and available device storage determine practical limits.

Appearance and main behavior settings live in the app. This is an original personal implementation inspired by the dictation workflow; it uses its own native UI and alien artwork.

## Phone setup

Use a **signed APK** from the release delivery, then open AlienFlow, enter your own API key, allow the microphone and enable the clearly labelled AlienFlow accessibility service. Leave Gboard selected and start with an empty note. API keys are entered and encrypted on the phone; no key is bundled in the APK or repository.

Follow [the first-phone checklist](docs/DEVICE_TESTS.md) to verify recording, text preservation, corrections, interruptions and updates on the real device.

## Development and handoff

- [Product and engineering specification](docs/PRODUCT_SPEC.md): requirements, architecture, defaults and acceptance criteria.
- [Implementation checkpoint](docs/IMPLEMENTATION.md): implemented behavior, evidence, known gaps and next steps.
- [Protocol evidence](docs/PROTOCOL.md): exact native WebRTC interfaces and provider integration assumptions.
- [Build evidence](docs/BUILD_EVIDENCE.md): pinned versions, verified upstream sources and local checks.
- [Build and release instructions](docs/RELEASE.md): GitHub Actions, signing identity and safe upgrades.
- [Agent contract](AGENTS.md): rules for future AI development agents.

## Build

GitHub Actions runs unit tests, release lint and release assembly. It produces an aligned **unsigned** release bundle with reports and source hashes. The permanent private key is kept separately; the final delivery is signed with that key and verified before being described as installable. See [RELEASE.md](docs/RELEASE.md).

```bash
bash gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

A successful CI run establishes compilation and static tests. Device behavior and provider account access require the phone checklist and real API calls.
