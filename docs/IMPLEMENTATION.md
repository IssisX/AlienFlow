# AlienFlow(chat) — editor sync repair, 0.1.23-chat.1 (24)

Date: 2026-10-13. Repository: IssisX/AlienFlow. Branch: AlienFlow(chat).
Baseline: 79008d5ea95492dbfe9e910bb4d38e529aad4231 (source-handoff only over 2ada079).
Imported editor repair: AlienFlow2 at 2c3f36d8cebfb5aff124191f1b00ee21892b9fae; compiled upstream source dc87d202161bffe31cd13edb6a1cb3c8ded79bb6.
Current compiled source: adda87cd9bc2e0f3cb491a35ab9a8d9877347c4f, tree e65fa9b26a884de04103298f7b90f25acef07545. Later documentation-only commits do not change the APK.

## Observation and diagnosis

The owner report identifies installed 0.1.21 (22), source 5e248ee3a9c4c91a0c78580f0917d641e7035b15. Microphone frames, server session, transcript readiness and saving complete. No live connection error or new crash appears. The failure is editor authorization before insertion: node length 12/cursor 11, keyboard length 1/cursor 0/offset 0, evidence 09 reported as a generic AF-E10 timeout. Private characters are not known from that report. Its Chrome unavailable-input record explicitly may predate the session; it is not proof of the current target's identity.

Read-only upstream inspection found the same cross-representation failure reproduced on an API 36 Chromium WebView. Actual baseline artifact 11824819007 (SHA-256 2599f3f75fb874ed283ee0d20de9436dc1bdf040e9e05859de7187ca8ecb88f3) shows accessibility placeholder text, keyboard newline, delivered=false and AF-E10. Upstream repaired artifact 11827092984 (SHA-256 3b06924ea2510a450e91cda903b7c56ade931916d6238cf29574c05d4cc35ef8) shows delivered=true, final revision, selected replacement, preserved typed text, repeat Unicode session and installed-Termux echo. All 144 upstream member digests were independently checked. These are upstream results; the new branch verification below was separately executed.

## Repair and preserved contracts

Port the reviewed keyboard-coordinate ownership repair and real browser test into the isolated chat branch. Accessibility owns focused-node identity/protected-field exclusion. The keyboard connection owns text and coordinates for keyboard writes. No equality between those representations is required. Confirm proposed mutations through fresh keyboard evidence; preserve the complete observed mutation range and shared keyboard characters. Unknown offset -1 requires a stable absolute IME selection. Other negative offsets are rejected, not guessed. No placeholder blacklist, trimming, blind whole-field replacement, longer timeout, clipboard fallback or duplicate resend.

Keep the chat branch's TerminalInsertion blank/control-only guard and tests. Termux remains final-only, once after Stop, without Enter. The original DictationCoordinator, RealtimeTransport, RealtimeProtocol, OpenAiClient, TerminalDictation, LocalStore, MainActivity and PendingAudio source files are byte-for-byte unchanged from the chat baseline. Settings, history, key-storage and artwork remain intact. Package/signing identity and permissions are unchanged. API 36 and APK-only delivery; AAB remains deferred. Source branches AlienFlow2, main and build/first-apk are not written.

A new regression uses explicit fixture characters with the reported 12:1 / 11:0 metadata for known offset 0, and also covers offset -1 and invalid offsets. The local Kotlin smoke reproduced old AF-E09 and verified insertion/revision through the new owner. It is not a device or provider test.

## Verified current branch

[Run 38468493602](https://github.com/IssisX/AlienFlow/actions/runs/38468493602) completed successfully for adda87cd9bc2e0f3cb491a35ab9a8d9877347c4f. The complete local staged source tree matched the remote Git tree exactly before publishing. Local XML/TOML/Python/shell syntax checks and the Kotlin smoke passed. Four imported upstream test/debug files retain their original blank EOF lines; these are whitespace warnings, not a claimed clean whitespace gate.

- 212 unit tests executed; zero failures, errors or skips.
- Release lint: zero errors, 39 warnings. Reviewed categories: synchronous diagnostic preferences, pinned tool/SDK/dependency recommendations, monochrome launcher resource, view constructor, localization and absolute coordinates. No new error suppression/baseline or disabled check.
- Historical 0.1.17 Termux replay: exactly the two named expected failures reproduced, and its changed-field protection case passes. Current coordinator tests pass separately.
- Two actual API 36 instrumentation tests passed in one process. Chromium WebView 133.0.6943.137 reproduced differing placeholder/keyboard representations and now delivered actual DOM text with no editor error. Live final revision, selected replacement, typing continuation, preserved anchors and repeated Unicode session passed. The installed official Termux 0.118.3 x86_64 test observed terminal echo once per stopped session, no premature partial, repeated session, ordinary live delivery after Termux and visible Termux input on return.
- Recognition events/capture readiness in those editor tests are explicit fixtures; no microphone or paid OpenAI call was made. Actual screenshots of the resulting browser DOM and terminal echo were visually inspected. Neither is a Fold6 screenshot.
- Release APK assembly, source/package/version/API identity, approved artwork and absence of native libraries/debug browser classes passed.
- Artifact 11850717692: AlienFlow-0.1.23-chat.1-unsigned-release-bundle.zip, 6,711,409 bytes, SHA-256 72507ae5416be5c2114c294c01719ae2f93d462ad6ce5720c5a2f5623146ab2e. All 178 manifest-listed member hashes verified after download.
- Signed APK: AlienFlow-0.1.23-chat.1.apk, 4,569,240 bytes, SHA-256 86a284711f2f69e5dda52a7091029f1246792aebfeeef4294918ba3e09e493d0. APK v3 signature verified with original certificate d79e74b3a4c24ee225d1220c2c374c6f958520d4f3257fcfb399ff09bf5c965f. All 25 unsigned application payload entries remain identical; only three META-INF signature entries were added. The private key was not uploaded or committed.

[Machine-readable release verification](RELEASE_VERIFICATION_0.1.23-chat.1.json) and [actual signer output](APK_SIGNATURE_0.1.23-chat.1.txt) tie the delivered file to these checks.

## Device acceptance

Install the signed version in place, not an unsigned bundle; do not uninstall or clear data. Repeat the failing text field, test live words/corrections, Stop, then test Termux final-only delivery without Enter. Same-phone acceptance remains pending. A remaining error needs the new version/source and fresh keyboard observation, not another permission reset. Previous chat checkpoint is preserved at history/IMPLEMENTATION_CHAT_0.1.20-chat.1.md. A successful emulator editor run does not establish universal editor support or public-store readiness.
