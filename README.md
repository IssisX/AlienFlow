# AlienFlow — `AlienFlow(chat)`

Android dictation using the selected keyboard, a floating accessibility bubble, and inline text delivery. Application ID: `io.github.issisx.alienflow`. This branch incorporates Claude's .28 work at `b96e59cdbc8ba38d28e5da50666c4efad8ea1305`; `claude/alienflow` and the other development branches are unchanged.

## Background spending repair: 0.1.29-chat.1 (30)

The owner reported three unexpected balance drains. Source inspection and regression tests identified automatic saved-audio uploads after interrupted dictation and ineffective Stop/Cancel during those uploads. The previous claim that no work ran in the background was incorrect. Exact account charges have not been inspected.

- Paid API access starts **paused**, including after upgrading. Account & setup has an explicit switch; opening the app does not start API work.
- Interrupted audio stays on the phone. Recovery requires an explicit paid action; Stop, Cancel, screen lock and leaving the recovery screen cancel the actual request.
- Home and Account expose **Pause all API activity**. Removing the key also pauses and cancels requests.
- Quiet stop defaults to 15 seconds, with a maximum of 30; an old Off preference means 30. Sessions are limited to 1–5 minutes, default five, with independent capture and coordinator deadlines.
- Connection loss permits local recording only, without automatic reconnect or re-upload. HTTP calls have a 45-second deadline and no automatic retry. Recovery has a two-minute total deadline.
- Active work and its Cancel action remain visible in the foreground notification.

These controls bound activity; they are not an account spending cap or proof that every reported charge came from one defect. Cancellation cannot reverse processing already billed by the provider. See the [incident record](docs/BILLING_INCIDENT_2026-10-17.md) and [implementation evidence](docs/IMPLEMENTATION.md).

## Preserved behavior

Live text follows eligible fields during an active session; old-field speech is not inserted into a new field. Light polish preserves user edits and guarded meaning-bearing words. Termux receives a final insertion after Stop and never Enter. The existing silence gate, keyboard overlap repair and interface work are retained.

Android 11 and newer are supported. Android 11–12 use the guarded empty-field route; existing drafts and Termux require Android 13 or newer. Target API remains 36. Development produces APKs, with no AAB or store submission.

## Installing and verification

CI produces an **unsigned** APK inside the Actions bundle. Use the signed delivery APK, or follow the private signing process in [docs/RELEASE.md](docs/RELEASE.md). Updates retain the existing package and original signing certificate; uninstalling or clearing data is unnecessary.

The latest verification status, exact source, workflow and limitations are recorded in [docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md). Tests use local HTTP servers, scripted recognition and emulators. No paid API test is required. Microphone accuracy, account billing attribution and physical Fold6 acceptance remain unverified; this is not a paid public release approval.

Routine device checks are limited to representative Android 12, 15 and 16 phones. Samsung Note10+ is explicitly in scope; its Android 12 emulator coverage does not replace physical Samsung/One UI acceptance. Note10+ and Fold6 are the physical-device priorities.
