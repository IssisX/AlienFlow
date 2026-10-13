# AlienFlow(chat) — verified 0.1.19 integration

Date: 2026-10-13. Repository: IssisX/AlienFlow. Branch: AlienFlow(chat).
Signed development version: **0.1.20-chat.1 (21)**.
Compiled source: **683ab2dcc5902d41ed5b283f08d6a8054def1b2d**.
Compiled tree: dadbe62f548408e2a5d58ba0083f5c88be85ccc8.

## Preserved source and contracts

AlienFlow2 is a branch, not another repository. AlienFlow(chat) already contained the prior Termux repair at f24ace1d85b480005d5587b618e6cf37db48ab55. Merge a912cd3f0d8a6bb08be0893e5812db587ea7efe8 preserves that ancestry and the complete upstream 0.1.19 at 3be1cd2b5bac1206ad3f9025b64d1b55e5c2eac1. No writes were made to AlienFlow2, main or build/first-apk. Historical checkpoints are preserved verbatim under history/IMPLEMENTATION_CHAT_0.1.18-chat.1.md and history/IMPLEMENTATION_ALIENFLOW2_0.1.19.md.

The working 0.1.19 coordinator, microphone/transport, model choices, editor bridge, IME-only suffix preservation, main UI, approved icon, opacity, settings, history, key-storage format and package identity remain. No new permission or data migration was added.

## Termux behavior

The historical .17 coordinator attempted terminal writes while speaking or before the last recognition result completed. The centralized finalDelivery gate fixes both paths. This gate already existed in .18/.19 and the preserved chat branch; it is not a newly discovered .19 defect. No new .19-specific phone failure trace was supplied.

Termux receives final text once after Stop, retaining existing input without Enter or control-key actions. Ordinary fields retain live revisions. The only additional production delta versus .19 is a TerminalInsertion guard: after converting terminal control characters, a blank result cannot insert a blank fragment or consume one-shot delivery. The regression exercises that actual owner.

## Executed verification

[Actions run 38398347454](https://github.com/IssisX/AlienFlow/actions/runs/38398347454) passed all required steps:

- 198 unit tests executed, no failures/errors/skips. The same historical regression reproduced exactly the two expected .17 failures; changed-target protection passed.
- Real installed official Termux v0.118.3 x86_64 on Android 16/API 36. Actual overlay touches and accessibility/InputConnection IPC delivered completed text once, held partial/delayed results until ready, preserved the prefix and did not send Enter. Switching to an ordinary field produced live partial/final revisions before Stop. Returning to Termux allowed a second session without resetting accessibility.
- Release lint: zero errors, 38 reviewed warnings. They concern deliberate diagnostic persistence, pinned versions/SDK, localization/RTL, programmatic view constructors and launcher-theme metadata. No suppression or relaxed gate was added.
- Release assembly, identity, absence of native WebRTC, icon digest and required UI artifacts passed. Termux screenshots plus settings, large-text small-screen controls, history and light/dark indicators were visually inspected.

The first expanded run, 38396676169, passed the unit suite and first two editor legs, but the return tap timed out. Its harness checked only View.isShown/coordinates, not the actual topmost touch target after a keyboard transition. The final harness observes settled geometry and the topmost accessibility-overlay region before one tap; it also retains geometry/screenshots/diagnostics on failure. No application source or behavioral assertion changed in that test-only repair. Do not cite the first timeout as an established production input defect.

Recognition/readiness callbacks and an unstarted transport are explicit fixtures. The integration is NOT a physical microphone, OpenAI, Gboard, tmux/Codex or Fold6 test. Synthetic timing records are not measured dictation latency.

## Signed delivery

Artifact 11814466968, AlienFlow-0.1.20-chat.1-unsigned-release-bundle.zip: SHA-256 d1549d65e8f7cf194c15a906928881f0f715c1b9d6944f78c01e74d0fada8335. Download digest and all 157 manifest-listed member hashes matched.

Installer: AlienFlow-0.1.20-chat.1.apk; 4,552,856 bytes; SHA-256 27193414f7d4e3a2e7e91ed14b7f0a4ec91ba6bfec9244656f070cf7b4d06be8. APK v3 signature verified against original certificate d79e74b3a4c24ee225d1220c2c374c6f958520d4f3257fcfb399ff09bf5c965f. All 25 unsigned application payload entries remained identical; only three META-INF signer entries were added. Approved icon SHA-256 remains 48d0ce0aacba2fd4264100fb1af1b6a73f54290f6c5296f4adc07d3974a50671. Private signing material was not changed or published.

Exact evidence: RELEASE_VERIFICATION_0.1.20-chat.1.json and APK_SIGNATURE_0.1.20-chat.1.txt. Later documentation-only commits do not change the compiled app.

## Remaining acceptance and paid release

Install in place without clearing data. With the usual keyboard, dictate in Termux and Stop, verify no Enter, switch to an ordinary live field, then return to Termux. A remaining failure needs the new version-specific report from Settings → Help & about → Diagnostics / share report. Older WebRTC exits are not current evidence.

PLAY_RELEASE_READINESS.md records pending disclosure/consent, accessibility/foreground-service declarations, privacy policy/Data safety, account/cost decisions, App Bundle/signing and real-device release validation. This is a development APK, not a Play submission or approval.
