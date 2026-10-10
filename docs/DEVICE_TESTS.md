# First phone verification

This is the acceptance checklist for the first APK. Empty boxes are pending, not passes. Use a spare text note for the initial checks.

## Setup and first dictation

- [ ] Install the signed ARM64 APK on the Galaxy Z Fold6; record Android/One UI version.
- [ ] Enter an OpenAI API key in AlienFlow on the phone. Grant microphone permission and enable the clearly labelled AlienFlow accessibility service.
- [ ] Leave Gboard selected. Close AlienFlow's activity, focus a text field, and tap the bubble. Confirm a real microphone indicator and live words before stopping.
- [ ] Speak two sentences separated by a pause. Confirm the pause finalizes a phrase while the dictation session stays active.
- [ ] Tap stop. Confirm final text, microphone release and useful history. Confirm the field is never submitted automatically.

## Bubble and settings

- [ ] Idle bubble is approximately a Gboard mic button in visible size and 25% opaque by default; active state is clearly distinguishable.
- [ ] Dragging does not accidentally start/stop dictation. Position survives restart, fold/unfold and rotation, remaining reachable.
- [ ] Main behavior settings change actual behavior, persist, and apply consistently across apps. Test faithful, very light and stronger correction modes.
- [ ] Ordinary battery settings work. Cold microphone start must pass with the activity closed and without having recently opened or prewarmed recording.

## Text preservation

- [ ] Insert in the middle of an existing sentence and over a deliberate selection. Prefix and suffix remain intact.
- [ ] Say names, a URL, `minus ten`, profanity, a negation and uncertainty. Very light correction preserves meaning and vocabulary.
- [ ] Type or move the caret during dictation. AlienFlow stops unsafe edits and preserves the resulting words for recovery.
- [ ] Switch fields/apps during an outstanding response. No old response appears in the new field.
- [ ] Start while Gboard has an unfinished composing word. Existing text remains intact, or the app clearly declines an unsafe edit.
- [ ] Unsupported or ambiguous editors fail visibly; they never trigger blind whole-field replacement.
- [ ] Check undo/copy/recovery without overwriting unrelated edits.

## Same behavior across apps

- [ ] Ordinary Android text field and multiline note.
- [ ] Browser input and textarea.
- [ ] Messaging field and custom/rich editor used by the owner.
- [ ] Password/numeric/special field handles restrictions visibly and safely.

A custom editor failure remains a product compatibility failure to investigate. Do not mark universal support complete based only on a standard Android EditText.

## Network, lifecycle and updates

- [ ] Drop connectivity mid-phrase. Confirm honest state and the configured capture/recovery behavior; reconnect and verify ordering without lost/duplicated text.
- [ ] Stop during connection setup and during cleanup. Late callbacks never resurrect recording or modify a different field.
- [ ] Turn off microphone permission, lock the screen, kill the process, restart the app, and inspect recoverable pending work.
- [ ] Leave a dictation session active through provider connection renewal. No arbitrary application word/session cap appears.
- [ ] Clear history/pending recordings. Confirm configured retention and successful-recording deletion.
- [ ] Install a later APK over this one and confirm package/signature identity and data preservation.

Record observed results in `IMPLEMENTATION.md` or a dated verification report. Do not include API keys, private dictation or recordings in the public repository.
