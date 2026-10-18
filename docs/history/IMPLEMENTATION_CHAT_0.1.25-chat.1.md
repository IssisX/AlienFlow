# AlienFlow chat implementation — 0.1.25-chat.1 (26)

The 15 October 2026 writing-quality repair is built, verified and signed. This is a development APK, **not signed off for a paid public launch**. [Paid release readiness](PAID_RELEASE_READINESS.md) records the remaining acceptance evidence.

## Behavior

Recording stays active when the focused eligible field changes. New speech goes inline into the new field; late prior-field results stay with their original segment/history. Existing keyboard, API 36, APK-only development, and Termux final-only insertion without Enter remain unchanged.

Cleanup now considers a completed speech prefix together, preserving later provisional speech and the complete raw original. It receives up to 512 characters before and 256 after the insertion from the authoritative keyboard snapshot, only as read-only data. The privacy screen describes this use. Context is absent from logs and history, and old-field context cannot authorize new-field cleanup. Manual edits remain protected. Context-sensitive memoization prevents reusing a sentence-start capitalization decision in a different insertion context; long chunks preserve their separators.

The prompt requests sentence case and coherent continuation across pauses, without inventing missing facts. Light polish permits a small audited set of model-proposed common spelling substitutions (including the reported lowercase thet/that), while protecting capitalized names, paths, identifiers, quantities and meaning. This is deliberately not a claim of general spelling accuracy. Initial insertion now separates neighboring words with owned whitespace, which cancel can remove without changing the original draft.

The recognition delay is medium instead of minimal, a documented balance of latency/audio context; the actual microphone accuracy/latency tradeoff is unmeasured. Stop cancels unfinished live cleanup, then permits one final complete-segment pass within the existing 2.5-second budget if needed. Failure or timeout retains recognized words. This also covers speech finalized only after Stop.

## Exact verification

Compiled source: `39a8e71bcbdff6397e056c211c053bf07b96da5c`. Workflow: https://github.com/IssisX/AlienFlow/actions/runs/38731098492 — success.

232 unit tests, zero failures/errors/skips; release lint 39 warnings, zero errors. Historical .17 Termux and .23 app-switch/stale-cursor behavioral regressions remain required gates. Two new tests fail by assertion against shipped .24, proving the typo-guard and insertion-boundary defects before repair. New tests cover real widget/InputConnection context and insertion, context-dependent request caching, connected finalized prefixes, out-of-order finals, late cleanup, long-chunk whitespace and final-stop HTTP cancellation/budget behavior.

The Android 16 emulator exercised real Chromium/WebView and official installed Termux, including active app switching and return to terminal. The input IPC, DOM and terminal echo are real; recognition and microphone readiness are fixtures. **No paid provider request or physical microphone test was performed.** Real Fold6 update, recording quality, capitalization, semantic coherence and real-device switching require acceptance before public release.

All 199 downloaded bundle member hashes and the workflow artifact digest matched. The APK contains the compiled source SHA. APK v3 verification passed with the original certificate `d79e74b3a4c24ee225d1220c2c374c6f958520d4f3257fcfb399ff09bf5c965f`; all unsigned payload entries remain identical after signing. Signed APK SHA-256: `fb2bc74c68bd6ec6423e84d3159fb167dff80e7c746d62b1c6e7b2f61f0e72c3`. The ZIP download contains byte-identical APK bytes. No private signing material was uploaded or committed.

[Release verification](RELEASE_VERIFICATION_0.1.25-chat.1.json) and [signer output](APK_SIGNATURE_0.1.25-chat.1.txt) record exact evidence. Preliminary runs were superseded during review; no failed check is represented as a passing release. [Previous 0.1.24 evidence](history/IMPLEMENTATION_CHAT_0.1.24-chat.1.md).
