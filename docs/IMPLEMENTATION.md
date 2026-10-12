# AlienFlow2 implementation checkpoint — 0.1.19

Repository: IssisX/AlienFlow. Branch: AlienFlow2. Date: 2026-10-12. VersionCode: 20.

## Report and diagnosis

The owner reports repeat failures in ChatGPT and Chrome on 0.1.18 (19), source 185665b2451e1ce3b9a78e2cb4ba4697bd70e581, Samsung SM-F956U / Android 16 / API 36. The supplied metadata shows successful microphone and live connection startup, transcript completion and saving, followed by a pre-write editor rejection. The last observation has node length 25, IME length 27, both cursors 25:25, explicit offset 0 and composing=false. The old 0.1.6 SocketException and PACKAGE_UPDATED exits do not establish a crash in these sessions.

EditorEvidence rejected any keyboard window whose end exceeded the accessibility text length, even when the extra characters were beyond the selected mutation. This coverage check necessarily rejects the reported lengths. The diagnostic did not record character equality: whether the two representations share an exact prefix, and what the extra characters are, remain unknown. Do not infer that they are newlines. Chromium TextInputState supplies a separately represented IME window and explicit absolute offset; Android SurroundingText documents that offset and allows bounded windows.

## Repair and acceptance

Corroboration now requires an explicit anchored offset, exact equality of every shared character, matching absolute selection, nonempty independent text evidence for nonempty input, and coverage of the entire mutation within both representations. IME-only characters past the node are outside the allowed edit. The existing keyboard range commit leaves them intact; no trimming, normalization, substring search or whole-field reconstruction is added. Unknown offsets still require exact full contents. Genuine shared-text/cursor conflicts remain rejected. Diagnostics retain a metadata-only evidence rejection code in addition to lengths, avoiding an ambiguous generic timeout report.

Regression tests exercise the production bridge and real framework EditText/InputConnection with a simulated accessibility representation omitting an arbitrary two-character suffix: initial 25/27 lengths and cursor 25, revisions, cancellation, bounded nonzero-offset windows, selected replacement, multiline Unicode, typed user text between speech updates, and a shared-text conflict that dispatches no commit or selection. Remote Android accessibility IPC and representation publication are explicit fixtures. Existing terminal final-only gating, typing/composition protections, focus ownership, background readiness, selected keyboard, cost reductions and approved artwork are retained. No provider request is added by this repair.

Primary references: https://chromium.googlesource.com/chromium/src/+/refs/heads/main/content/public/android/java/src/org/chromium/content/browser/input/TextInputState.java and https://developer.android.com/reference/android/view/inputmethod/SurroundingText .

## Verification

Verification pending the exact corrected source CI build, release lint, assembly, bundle integrity checks, original-certificate signing and saved APK verification. Tests simulate the representation boundary on SDK 35, not the installed Fold6 / Android 16. Successful compilation and simulated editor tests cannot certify ChatGPT, Chrome or Termux phone behavior. This release addresses the proven overlong-window rejection; shared-text disagreement would still correctly reject. Historical evidence: RELEASE_VERIFICATION_0.1.18.json and history/IMPLEMENTATION_0.1.18.md. Standing release authorization is in AGENTS.md.
