# First phone verification

This is the acceptance checklist for the first APK. Empty boxes are pending, not passes. Use a spare text note for the initial checks.

If Android blocks the accessibility toggle as a restricted setting after APK installation, open Settings → Apps → AlienFlow → the More menu → Allow restricted settings, then return to AlienFlow's accessibility setup. The permission allows reading and editing the focused field. These are the [official Android instructions](https://support.google.com/android/answer/12623953?hl=en); menu wording can differ by device. Only enable it for the AlienFlow APK whose source and signing identity accompany this handoff.

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

## 0.1.1 tap-to-start feedback check · pending phone observation

- [ ] Install the signed `AlienFlow-0.1.1.apk` directly over 0.1.0; do not uninstall. Verify settings and saved key persist.
- [ ] Focus a new empty note with Gboard selected; tap the alien once. Confirm a non-touchable status notice appears next to it. Record the exact notice (no API key or private dictated text).
- [ ] If the notice reports a missing editor connection, verify whether the keyboard cursor is active and whether the in-app empty test field behaves differently. No blind editor writes are authorized.
- [ ] If the notice reports a microphone start denial or foreground-service restriction, investigate the actual Android background start gate on the Fold6 before changing capture ownership or adding another permission.
- [ ] If no status notice appears at all, diagnose overlay touch dispatch and movement threshold before investigating the network/provider.
- [ ] Confirm that the idle bubble is 75% opaque by default, that 100% slider value looks fully solid, and that user-selected opacity persists after restarting the app.

Source/CI/signature proof is in `RELEASE_VERIFICATION_0.1.1.json`; do not mark these phone observations as passed until actually reproduced.

## 0.1.3 crash capture · pending phone test

- [ ] Install the signed 0.1.3 APK **over** 0.1.2, without uninstalling or clearing data. Confirm the key and settings remain.
- [ ] Focus an empty note and tap the alien once. If Android reports an app crash, reopen AlienFlow.
- [ ] Scroll to **Crash diagnostic (private)**, tap **Copy crash diagnostic**, and paste that text into the development chat. It omits exception messages and dictated text. If it reports only the last checkpoint, this does not establish a Java exception.
- [ ] Use the evidence to identify the owner of the failure; fix that owner and rerun the actual device path before claiming crash resolution.

No Java handler can promise to catch a native crash or an OS-level process kill. No ADB/Termux debugging setup is required for the initial attempt to collect an app-owned report.
