# AlienFlow(chat) — editor sync repair, 0.1.23-chat.1 (24)

Date: 2026-10-13. Repository: IssisX/AlienFlow. Branch: AlienFlow(chat).
Baseline: 79008d5ea95492dbfe9e910bb4d38e529aad4231 (source-handoff only over 2ada079).
Imported editor repair: AlienFlow2 at 2c3f36d8cebfb5aff124191f1b00ee21892b9fae; compiled upstream source dc87d202161bffe31cd13edb6a1cb3c8ded79bb6.

## Observation and diagnosis

The owner report identifies installed 0.1.21 (22), source 5e248ee3a9c4c91a0c78580f0917d641e7035b15. Microphone frames, server session, transcript readiness and saving complete. No live connection error or new crash appears. The current failure is editor authorization before insertion: node length 12/cursor 11, keyboard length 1/cursor 0/offset 0, evidence 09 reported as a generic AF-E10 timeout. Private characters are not known from that report. Its Chrome unavailable-input record explicitly may predate the session; it is not proof of the current target's identity.

Read-only upstream inspection found the same cross-representation failure already reproduced on an API 36 Chromium WebView. Actual baseline artifact 11824819007 (SHA-256 2599f3f75fb874ed283ee0d20de9436dc1bdf040e9e05859de7187ca8ecb88f3) shows accessibility placeholder text, keyboard newline, delivered=false and AF-E10. Upstream repaired artifact 11827092984 (SHA-256 3b06924ea2510a450e91cda903b7c56ade931916d6238cf29574c05d4cc35ef8) shows delivered=true, final revision, selected replacement, preserved typed text, repeat Unicode session and installed-Termux echo. All 144 member digests were independently checked. Those are upstream test results, not this branch's new verification.

## Repair and preserved contracts

Port the reviewed keyboard-coordinate ownership repair and real browser test into the isolated chat branch. Accessibility owns focused-node identity/protected-field exclusion. The keyboard connection owns text and coordinates for keyboard writes. No equality between those representations is required. Confirm proposed mutations through fresh keyboard evidence; preserve the complete observed mutation range and shared keyboard characters. Unknown offset -1 requires a stable absolute IME selection. Other negative offsets are rejected, not guessed. No placeholder blacklist, trimming, blind whole-field replacement, longer timeout, clipboard fallback or duplicate resend.

Keep the chat branch's TerminalInsertion blank/control-only guard and its tests. Termux remains final-only, once after Stop, without Enter. Keep all original transcript/capture/transport/model/prompt/settings/history/key-storage/artwork source unchanged. No package/signing identity change and no permission additions. API 36 and APK-only delivery; AAB work remains deferred. Source branches AlienFlow2, main and build/first-apk are untouched.

A new regression uses explicit fixture characters with the reported 12:1 / 11:0 metadata for known offset 0, and also covers offset -1 and invalid offsets. The local Kotlin smoke run reproduced old AF-E09 and verified insertion/revision through the new owner. It does not emulate the device or provider. The production bridge and imported real browser/Termux integration still require CI execution.

## Verification status

At this source checkpoint: local pure-Kotlin smoke, XML/Python/shell syntax and whitespace checks passed. Full pinned Android build, unit suite, real API 36 browser/Termux tests and original-certificate signing are pending. The workflow preserves the historical .17 Termux regression gate and runs both current editor classes in one instrumentation process. A later evidence record must tie outcomes to this exact compiled revision. Do not describe the upstream pass as a new chat-branch pass.

## Device acceptance

Install the signed new version in place, not an unsigned bundle; do not uninstall or clear data. Repeat the failing text field, test live words and corrections, Stop, then test Termux final-only delivery without Enter. Same-phone acceptance remains pending. A remaining error needs the new version/source and fresh keyboard observation, not another permission reset. Previous verified chat checkpoint is preserved at history/IMPLEMENTATION_CHAT_0.1.20-chat.1.md.
