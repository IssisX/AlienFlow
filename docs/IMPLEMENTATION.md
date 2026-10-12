# AlienFlow2 implementation checkpoint — 0.1.15

Repository: IssisX/AlienFlow. Branch: AlienFlow2. Date: 2026-10-12. VersionCode: 16.

## Observed and supported cause

The owner reports any keyboard typing stops inline insertion while listening stays on. Source decisively distinguishes this from a microphone/provider outage: the target monitor treats text/cursor inequality as focus loss and clears the editor, while the USER_EDIT write completion explicitly clears both editor and captureTarget for the remaining session. There is no reattachment path. Physical Fold6 reproduction is unavailable here.

## Implemented contract

Typing/cursor changes in the same bound field do not pause or detach dictation. Initial microphone binding also permits an active keyboard composition; no AF-E08 typing prerequisite remains. The coordinator keeps capture and transcription running and the editor writer bound. Structural focus/lifecycle invalidation remains distinct from content ownership. A new field is never adopted by an ongoing dictation.

The bridge retires the previously dispatched speech span after user interaction, captures fresh prefix/suffix/cursor evidence from the same field, and writes only the new speech tail. SpeechContinuation maps the protected word boundary across source revisions using matching prefix/suffix tokens; protected case/punctuation/partial-word revisions cannot restore deleted or edited words. Its frontier advances only on dispatched/consumed results, not previews. This deliberately prevents late cleanup from revising speech already edited by the user. It is a conservative positional boundary through wholly revised unanchored recognition, not semantic recognition proof.

Existing drafts retain verified keyboard range edits. The node route is still available only to a session initially bound to an unambiguous empty field; after a user edit in that same session it preserves captured user prefix/suffix and revalidates the full field immediately before dispatch. It does not enable arbitrary pre-existing drafts without a range connection. Cursor restoration requires an advertised selection action, a confirmed payload, and the unchanged target. Neither route resends a mutation or submits a field.

The ordinary keyboard InputConnection and the accessibility connection have different commit behavior. AOSP RemoteInputConnectionImpl batches beginBatchEdit, finishComposingText, commitText, endBatchEdit for the accessibility route (introduced before API 33). AlienFlow must not wait for a composing word or for the user to press space. It snapshots the literal composing word as protected user content, verifies matching node/keyboard contents and cursor, then uses that accessibility commit. The node route's native ACTION_SET_TEXT preserves the captured literal word and finishes composition through the widget's text update. Post-dispatch confirmation remains strict and must observe the proposed text and cursor. The OS IPC simulator now includes the actual accessibility finish-before-commit behavior around a real native InputConnection; a raw keyboard commit alone is not an accurate stand-in.

A 30 ms coalesced retry remains only for changed text/cursor evidence or temporarily unavailable metadata. There is no typing delay, silence wait, dictation pause state, error notice, or restart tap. Cancel after user interaction leaves the field intact rather than undoing keyboard edits. History stores the recognized transcript, not a reconstruction of the user's combined keyboard draft.

## Verification status

Added pure source-frontier regressions and production coordinator/bridge tests with native Android EditText and InputConnection. OS focus and remote accessibility IPC are simulated by Robolectric SDK 35. Required cases: typing before speech; repeated typing during one recognition turn; node-only and keyboard routes; late finals; existing anchors; cursor/selection movement; composing transaction; no adoption of another field; cancellation preservation. Release tests, lint, assembly, signed update identity and certificate are pending. Local Gradle invocation could not start because downloading the pinned Gradle 9.6.0 distribution failed with Network is unreachable. No tests or compilation ran. git diff --check passed. Automatic approval review rejected the attempted push to AlienFlow2 as an external write without explicit publishing authorization; the remote still points at 6f9fb65148ac086d8436840a536d4255889daccd. No alternate publication route was attempted. The repair remains local until approval, after which the existing branch-specific GitHub Actions pipeline can verify and package it. Exact installed Fold6, Gboard, ChatGPT and provider behavior remain unverified.

## Preserved

Approved silver interface/icon, selected keyboard, nonfocusable overlay, settings/history structure, fast parallel microphone startup, startup audio buffer, minimal transcription delay, light readiness haptics, Termux final-only insertion and no Enter, app identity and signing certificate.

## Phone acceptance

Install over 0.1.14. In a native field and ChatGPT, dictate, type a word, and continue speaking in the same session. Repeat edits, delete an earlier spoken word, move the cursor, then Stop. Confirm keyboard edits survive, later speech arrives automatically, and no paused status or duplicate earlier transcript appears. Termux keeps its terminal-specific single final insertion.

Primary references: [AOSP accessibility finish-before-commit](https://android.googlesource.com/platform/frameworks/base/+/d92f3fca7402f54ae59b8fc4a450878332009531^!/), Android AccessibilityInputConnection commitText/setSelection and AccessibilityNodeInfo ACTION_SET_TEXT/ACTION_SET_SELECTION. The broad API reference describes keyboard composition generically; the framework accessibility implementation is the decisive route-specific source. A build or framework test does not establish phone compatibility.
