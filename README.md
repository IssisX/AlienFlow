# AlienFlow — `AlienFlow(chat)`

Android dictation using the selected keyboard, a floating accessibility bubble, and inline text delivery. Application ID: `io.github.issisx.alienflow`. This branch incorporates Claude's .28 work at `b96e59cdbc8ba38d28e5da50666c4efad8ea1305`; `claude/alienflow` and the other development branches are unchanged.

## Balanced efficiency: 0.1.30-chat.1 (31)

Live words remain the default. Light polish now runs once after Stop by default, avoiding repeated correction of the growing transcript during speech. The existing 2.5-second final-polish budget, lossless chunking, and meaning-preserving merge remain. Failed or canceled polish leaves recognized words intact. An explicitly saved live-polish preference is retained; Settings → Dictation & writing → Polish while I speak controls the choice.

See [the cost decision](docs/COST_EFFICIENCY_2026-10-18.md) for the source-level cause, trial-cost assumptions and verification limits. This update does not provision customer trials or change audio models.

## Preserved background spending repair: 0.1.29-chat.1 (30)

**FIXED — the owner confirmed on 18 October 2026 that 0.1.29-chat.1 no longer drains the account in the background.** The exact APK and a release note are preserved. This user confirmation is separate from the earlier automated evidence and does not supply an account-level breakdown of historical charges.

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

The latest verification status, exact source, workflow and limitations are recorded in [docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md). Tests use controlled HTTP requests, local HTTP servers, scripted recognition and emulators. No paid API test is required. Microphone accuracy, historical account billing attribution and broader physical-device acceptance remain unverified; this is not a paid public release approval.

Routine device checks are limited to representative Android 12, 15 and 16 phones. Samsung Note10+ is explicitly in scope; its Android 12 emulator coverage does not replace physical Samsung/One UI acceptance. Note10+ and Fold6 are the physical-device priorities.
