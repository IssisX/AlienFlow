# AlienFlow — isolated chat repair

Repository: **IssisX/AlienFlow**. This checkout is **AlienFlow(chat)**, copied from **AlienFlow2** at `185665b2451e1ce3b9a78e2cb4ba4697bd70e581`. No changes from this repair were written to AlienFlow2, main or build/first-apk.

## Signed update: 0.1.18-chat.1 (20)

The owner-confirmed baseline is 0.1.17: dictation works outside Termux. This update retains that implementation and fixes premature Termux delivery. Termux receives the completed transcript once after Stop; existing input is preserved and Enter/control actions are not sent. Ordinary supported document fields retain live dictation.

The copied branch already contained the proposed seven-line coordinator repair. It was preserved, reviewed and verified rather than replaced by another speculative implementation. Accessibility events previously requested writes during speech or before final recognition completed; this detached the terminal binding or consumed its one allowed commit too early. The central write gate now waits for final delivery.

## Actual verification

[CI run 38315352858](https://github.com/IssisX/AlienFlow/actions/runs/38315352858), compiled source `0d0d6e6a67d221d24f96353ceaf0097ec8576240`:

- The same three regression tests were run against shipped 0.1.17. Exactly the two premature-write cases failed; changed-target protection passed.
- The candidate passed **192 tests**, zero failures/errors/skips; release lint **zero errors, 38 warnings**; debug/release/test APK assembly passed.
- **Official installed Termux v0.118.3 on an Android 16/API36 x86_64 emulator passed the cross-app test.** Real screen taps operated the floating alien, and the real accessibility service/InputConnection delivered the completed phrase once into the running terminal. Existing input stayed; provisional text did not arrive; no Enter was sent. Recognition events were injected fixtures: this test does not claim physical microphone, OpenAI, Fold6 or the owner's Gboard/configuration coverage.
- Downloaded bundle SHA-256 and all 139 recorded member hashes matched. The signed installer verified against the original permanent certificate. All 25 unsigned application payload entries remained byte-identical after signing. The approved artwork and absence of native WebRTC were rechecked.

[Exact release evidence](docs/RELEASE_VERIFICATION_0.1.18-chat.1.json), [signature output](docs/APK_SIGNATURE_0.1.18-chat.1.txt), [current implementation checkpoint](docs/IMPLEMENTATION.md), [diagnosis and reproducible checks](docs/CHAT_TERMUX_REPAIR.md).

## Install

Use the signed `AlienFlow-0.1.18-chat.1.apk` delivered in the development conversation, not the unsigned Actions bundle. Same package `io.github.issisx.alienflow`, original signing certificate, versionCode 20: update in place without uninstalling or clearing app data. Saved key, preferences, history, bubble position and icon formats were not reset or migrated.

On the Fold6, open Termux with the normal keyboard, focus the terminal, tap the alien, speak, and tap Stop. The completed words should arrive once on the current line without submitting it. The owner's physical-device acceptance is still pending. An unexpected failure should be captured using the existing Settings → Help & about → Diagnostics / share report path, not older setup instructions.

Read AGENTS.md before edits. Do not overwrite the other agent's branches or claim paid-release/universal compatibility from these bounded checks.
