# AlienFlow — isolated chat branch

Repository: IssisX/AlienFlow. Working branch: AlienFlow(chat). AlienFlow2 is a separate branch in this repository, not a separate repository. This repair writes only the isolated chat branch.

## Signed development update: 0.1.23-chat.1 (24)

This update integrates the reviewed 0.1.22 browser-editing repair from AlienFlow2 while preserving the chat branch's Termux guard. It addresses the 0.1.21 diagnostic where accessibility reported 12 characters/cursor 11 and the keyboard returned one character/cursor 0. The app was trying to synchronize two different editor representations, then reporting AF-E10.

Accessibility now identifies the focused, non-protected field. Keyboard text and keyboard coordinates authorize keyboard mutations and confirmations. The app no longer requires browser placeholder/presentation text to equal the editable keyboard buffer. Observed mutation coverage, shared text, user edits, selected replacement and cancellation remain checked. No timeout increase, blind whole-field replacement, placeholder blacklist or automatic resend was added.

The audio, OpenAI connection/models/prompts, settings, history, encrypted key storage, approved Gray image and 75% default opacity are unchanged. Native WebRTC remains absent. Termux keeps final-only delivery once after Stop, without Enter or control keys. The chat guard also rejects blank/control-only terminal output.

Install the signed `AlienFlow-0.1.23-chat.1.apk` delivered in the conversation over the existing app. Do not uninstall or clear data. Application ID `io.github.issisx.alienflow` and the original certificate are retained. The unsigned APK inside the Actions bundle is not an installer.

## Actual verification

[Actions run 38468493602](https://github.com/IssisX/AlienFlow/actions/runs/38468493602), compiled source `adda87cd9bc2e0f3cb491a35ab9a8d9877347c4f`, passed 212 unit tests with zero failures/errors/skips, release lint with zero errors and 39 warnings, release assembly and the two actual Android 16 editor integration tests.

The browser test uses real Chromium WebView/Gboard/accessibility IPC and an actual alien tap. It observes live insertion and final revision despite placeholder/keyboard differences, selected replacement, keyboard typing without stopping dictation, preserved draft anchors and a repeated Unicode session. The official installed Termux 0.118.3 test verifies actual terminal echo once after Stop, no premature partial, a repeated session and switching between terminal and ordinary live editing. Recognition and microphone readiness are explicit fixtures; no physical microphone or paid provider request was used.

The historical 0.1.17 Termux regression still reproduces exactly its two expected failures, while current tests pass. This preserved gate is separate from current release acceptance. All 178 downloaded bundle member hashes, the packaged artwork, release source identifier, absence of native libraries/debug browser classes and original APK v3 signature were checked. All 25 original APK payload entries remained unchanged after signing.

[Exact release record](docs/RELEASE_VERIFICATION_0.1.23-chat.1.json), [signer output](docs/APK_SIGNATURE_0.1.23-chat.1.txt), [current checkpoint](docs/IMPLEMENTATION.md).

## Phone acceptance still pending

Repeat the exact field that reported Editor sync timed out: dictate, pause for revisions, stop and check that text remains in the field. Then test Termux final delivery without Enter. The emulator tests do not establish the exact Fold6/Chrome/Gboard/tmux/Codex combination. If it fails, use Settings → Help & about → Diagnostics / share report and provide the new version/source and observation. No permission reset is indicated by the supplied report, which already showed accessibility bound and working microphone/server activity.

## Release scope

API 36 and APK-only development are preserved. No AAB, store submission, billing, new permissions, analytics or data migration was introduced. Google Play readiness remains separate from this editor repair. Prior chat verification/history and the untouched AlienFlow2 branch remain available.
