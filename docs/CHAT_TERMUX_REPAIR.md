# Termux repair — diagnosis and reproducible evidence

Owner requested an isolated AlienFlow(chat) branch copied from AlienFlow2 and preservation of .17 working outside Termux. Actual repository IssisX/AlienFlow; copy source 185665b2451e1ce3b9a78e2cb4ba4697bd70e581. This source already contained the proposed seven-line coordinator change; it was retained with its provenance.

## Bug

During .17 recording, targetEvent calls pumpWrite despite final-only Termux. The adapter refuses an early write and its binding is discarded. During finishing, that same event can commit a partial result before the final transcript and consume the one terminal send. The central pumpWrite(finalDelivery=false) gate prevents both paths; only tryFinish passes finalDelivery=true once the existing completion requirements hold. No unrelated production refactor was added.

## Proof run

Compiled source 0d0d6e6a67d221d24f96353ceaf0097ec8576240; [run 38315352858](https://github.com/IssisX/AlienFlow/actions/runs/38315352858).

- `python3 scripts/prove_termux_regression.py`: same three tests against shipped .17 source 7ebe095bfaa83456690b46d8934c0cad2590a94a in a separate worktree. Exactly the two premature-write cases fail; changed-field protection passes. Baseline XML and observed failures are retained, not merely source inspection.
- Candidate full suite: 192 tests pass, none skipped; lint zero errors/38 warnings. Runtime source diff from .17 is confined to the coordinator write gate.
- `bash scripts/test_installed_termux.sh`: official Termux v0.118.3 installed on Android 16/API36 emulator, actual alien taps and Android accessibility/InputConnection, existing terminal prefix, no partial writes, late final recognition after Stop, exactly one complete text delivery and no Enter. Passed. The resulting real terminal screenshot was visually inspected.

The transcript payloads in the installed-app test are explicit fixtures; the microphone/OpenAI boundary is not exercised. This proves the changed editor integration through real Termux, not end-to-end physical Fold6 speech recognition. Version 0.1.18-chat.1 (20) uses the original app ID/certificate and .17 artwork/preferences.

Setup failures were environmental: corrupt SDK archive, then AVD directory mismatch. The latter was diagnosed from the actual emulator log and fixed using explicit shared Android state directories and bounded waits. The successful rerun retained all original release gates.

## Primary sources checked

- https://raw.githubusercontent.com/termux/termux-app/v0.118.3/terminal-view/src/main/java/com/termux/view/TerminalView.java — commitText/finishComposingText consume and clear the IME buffer; TYPE_NULL is intentional. Terminal text cannot be revised as a normal document span.
- https://developer.android.com/reference/android/accessibilityservice/InputMethod.AccessibilityInputConnection — accessibility input contract.
- https://developer.android.com/reference/android/app/Instrumentation — cross-app UiAutomation; retain enabled accessibility services during tests.
- https://github.com/termux/termux-app/releases/tag/v0.118.3 — pinned official installed package and published checksum.
- https://developer.android.com/tools/variables — shared Android SDK/emulator/AVD state paths; official page updated 2026-10-05.

Physical owner acceptance remains the next observation. See IMPLEMENTATION.md and RELEASE_VERIFICATION_0.1.18-chat.1.json for the exact signed bytes and limits. Do not edit AlienFlow2, main or build/first-apk.
