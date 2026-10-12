# AlienFlow2 implementation checkpoint — 0.1.10

Repository: IssisX/AlienFlow. Branch: AlienFlow2. Baseline: f3b928a0d37f1b7290d5606d210bc16ccdce27e5. Date: 2026-10-12. VersionCode: 11.

## Owner observations and diagnosis

The owner approves the 0.1.9 icon and confirms partial Termux functionality. Three phone screenshots show AF-E14 insertion confirmation failure in the app test field, a provider final-audio-buffer warning in Termux, and AF-E10 node/keyboard disagreement in Chrome. The ordinary editor path emits E10/E14; these screenshots do not establish that Chrome was routed through the terminal adapter. begin() already reset the mode on every new dictation.

Concrete defects: editor confirmation sampled only once after an arbitrary 100ms delay and permanently invalidated ownership on that sample; expected manual edits used the same Boolean failure result as integration errors. Input finish/start invalidated the owner but left the terminal-mode flag set. At Stop, the WAV could close before the capture callback returned, while the final commit used another arbitrary 250ms delay. A delayed final callback can therefore fall outside the finalization boundary. These are source-supported mechanisms; the screenshots alone do not prove which timing caused each phone error.

## Implemented repair

- EditorSynchronization is the production confirmation gate. It reads node and IME metadata, waits for exact proposed text and selection to agree, and never resends the insertion. Persistent disagreement rejects further writes. Coherent manual text/cursor changes, composition and target changes pause inline writes quietly. The existing field and user's edits remain authoritative. Capture continues and the complete recognized transcript remains available in AlienFlow. A new tap after Stop starts a fresh session; live insertion does not silently adopt a new field mid-session.
- Input lifecycle callbacks clear terminal mode and old overlay notices, then refresh availability for the current field. Existing terminal final-only insertion behavior is preserved.
- CaptureDrain joins the real capture task before the final commit and before coordinator WAV closure. No guessed final-read timer remains. Empty/rejected commits still retain audio; Diagnostics records commit bytes, capture state and connection generation. The provider's exact reason for rejecting the pictured commit remains unresolved without that metadata or a provider response. Do not call it a proven device fix.
- The icon shows only useful diagnostic failures, not starting/listening/finishing/completion or manual-edit notices. Settings has a persistent Show diagnostic errors beside alien switch; off disables all overlay notices without disabling diagnostic records. No change to the accepted icon, opacity, package, signing key or provider route.
- Diagnostic records contain lengths, cursor offsets, phase and state only, never editor text, credentials or recorded audio. A fresh session clears old failure metadata.

## Verification contract

Regression tests exercise delayed node/IME convergence, manual edits, composition, persistent disagreement, stale target rejection, production input lifecycle mode reset, delayed capture completion, error-only feedback and true rejected-commit byte counts. Existing editor, terminal, protocol, cancellation, storage and render checks remain required. Tests and CI are not hardware/provider proof. Record compile/test/lint/signature outcomes against the exact artifact SHA in RELEASE_VERIFICATION_0.1.10.json.

## Phone acceptance still required

Install over 0.1.9. Dictate in Termux, Stop, then start a fresh dictation in the app test field and Chrome. Edit/cursor-move during dictation: your edit stays intact, recording continues, inline replacement pauses, and no interaction-error notice appears. Turn Show diagnostic errors beside alien off and confirm no overlay messages. If AF-E10, AF-E14 or AF-N02 recurs, copy Diagnostics immediately; it now distinguishes before/after editor state and sent audio byte counts.

Prior checkpoint: history/IMPLEMENTATION_0.1.9.md.
