# AlienFlow(chat) — verified Termux repair checkpoint

Repository: IssisX/AlienFlow. Branch: AlienFlow(chat). Date: 2026-10-12.
Version: **0.1.18-chat.1**, versionCode **20**.
Compiled source: **0d0d6e6a67d221d24f96353ceaf0097ec8576240**.
Compiled tree: **945fcf81f99af30527a9f14e63fd49560fc707b4**.
Later documentation/temporary-workflow cleanup commits do not change this compiled application.

## Ownership and baseline

The requested AlienFlow2 source is a branch of IssisX/AlienFlow, not a separate repository. AlienFlow(chat) was created as an exact branch copy of 185665b2451e1ce3b9a78e2cb4ba4697bd70e581. No write was made to AlienFlow2, main or build/first-apk. The owner's latest working state is 0.1.17 working everywhere except Termux; its compiled source is 7ebe095bfaa83456690b46d8934c0cad2590a94a. Earlier microphone failures in this conversation are not the current diagnosis.

The copied source already contained a proposed 0.1.18 coordinator repair and tests. The prior checkpoint is preserved verbatim at history/IMPLEMENTATION_ALIENFLOW2_0.1.18.md. Existing .17 history and release records remain unchanged.

## Causal defect and retained repair

The .17 coordinator's targetEvent could call pumpWrite regardless of the terminal/end-only setting. During speech, EditorBridge rejects a premature terminal write and the coordinator drops its binding. During finishing, an event could send an incomplete result and consume the one allowed terminal commit before the last recognition arrived. These are reproducible code failures, not guesses from a popup.

The retained central gate blocks live/event-triggered writes for terminal or explicit end-only targets. tryFinish alone authorizes finalDelivery after capture drain, acknowledgements, transcript completion and optional bounded polish. The entire production diff versus .17 is five added/two removed lines in DictationCoordinator.kt. Microphone, transport, model selection, Chrome/ordinary-editor algorithm, editor eligibility, storage, settings, artwork and permission implementation are unchanged.

Termux keeps its existing final-only contract: append the corrected completed text once after Stop, preserve existing input, no cursor moves, no deletion and no Enter or control actions. Changing fields still revokes delivery to the original terminal. Live ordinary fields preserve their existing route.

## Verification completed

[GitHub Actions run 38315352858](https://github.com/IssisX/AlienFlow/actions/runs/38315352858) passed for the exact compiled source above.

1. Baseline discrimination: scripts/prove_termux_regression.py builds shipped .17 in a separate worktree and runs the same candidate TerminalCoordinatorTest. Exactly accessibilityEventsDuringSpeechKeepTheTerminalBoundAndFinalTextCommitsOnce and finishingEventsCannotSendPartialTerminalTextBeforeTheLastTranscript fail. The changed-field protection case passes. A compile failure, extra failing case or unexpected pass blocks the gate. The current checkout is not patched during the test.
2. Candidate: 192 tests executed with zero failures/errors/skips. Release lint zero errors and 38 retained warnings; release/debug/instrumentation APK assembly passed. Test-only AndroidX dependencies are not in the release runtime.
3. Real installed terminal: official upstream Termux v0.118.3 x86_64, checksum/signature checked, installed on disposable Android 16/API36 emulator. Real injected screen touches operated the actual overlay; real Android accessibility broker and cross-process InputConnection delivered the completed text. The terminal showed `AF_SEED_CHAT_FIRST CHAT_LAST END` once, with existing prefix intact, no provisional CHAT_PARTIAL/CHAT_UNFINISHED and no Enter/duplicate cat echo. Three subsequent events did not duplicate delivery. Actual screenshot and result.json were inspected after downloading the bundle.
4. Boundary: transcript events and an unstarted transport at the speech boundary are explicit instrumentation fixtures. This is an installed-Termux editor integration test, not an end-to-end speech/provider or physical Fold6/Gboard test. No paid provider request or user API key was used.
5. Delivery: artifact 11776329022, bundle 4,947,396 bytes, SHA-256 656b2545e14aa404fd768600fd6db208c4ff98da388b2cab6487b14ef2384282. All 139 build-info file hashes matched after download. APK identity, no native WebRTC and approved artwork SHA-256 48d0ce0aacba2fd4264100fb1af1b6a73f54290f6c5296f4adc07d3974a50671 were verified.
6. Signed installer: AlienFlow-0.1.18-chat.1.apk, 4,552,856 bytes, SHA-256 3cf31595f88ab2e3d1ebd2e807b7d906e3e494a320ef7164555484fb55143660. APK v3 verifies with original certificate d79e74b3a4c24ee225d1220c2c374c6f958520d4f3257fcfb399ff09bf5c965f. All 25 original application payload entries are byte-identical after signing; only signer metadata was added. Private signing material remained outside source and CI.

## Test-environment failures resolved, not hidden

The first attempt's Android system-image download was corrupt. A retry installed it but the emulator reported Unknown AVD name because its discovery directory differed from avdmanager's output. An unbounded adb wait stalled that superseded owned run; it was canceled. Bounded setup retries, bounded adb/instrumentation waits, explicit ANDROID_USER_HOME/ANDROID_EMULATOR_HOME/ANDROID_AVD_HOME and a named device/path repaired the test environment. The actual installed-Termux gate then passed. Neither emulator gate nor application assertions were removed. The temporary exact-run cancellation workflow was removed after use.

## Remaining owner acceptance

Install the signed chat APK over .17 without uninstalling/clearing data. Keep the existing keyboard. Dictate in Termux and Stop: final text should arrive once without Enter. Repeat an ordinary input to verify the owner-confirmed working path remains intact on the phone. Physical Fold6, tmux/Codex and the owner's installed Termux/keyboard settings were not directly exercised here. Preserve this distinction; do not claim paid-release readiness.

Machine-readable record: RELEASE_VERIFICATION_0.1.18-chat.1.json. Actual signer output: APK_SIGNATURE_0.1.18-chat.1.txt. Durable tests: TerminalCoordinatorTest and InstalledTermuxTest. Do not take over other branches.
