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

- [ ] Idle bubble is approximately a Gboard mic button in visible size and 75% opaque by default; active state is clearly distinguishable.
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

## Historical 0.1.1 tap-to-start checklist

- [ ] Install the signed `AlienFlow-0.1.1.apk` directly over 0.1.0; do not uninstall. Verify settings and saved key persist.
- [ ] Focus a new empty note with Gboard selected; tap the alien once. Confirm a non-touchable status notice appears next to it. Record the exact notice (no API key or private dictated text).
- [ ] If the notice reports a missing editor connection, verify whether the keyboard cursor is active and whether the in-app empty test field behaves differently. No blind editor writes are authorized.
- [ ] If the notice reports a microphone start denial or foreground-service restriction, investigate the actual Android background start gate on the Fold6 before changing capture ownership or adding another permission.
- [ ] If no status notice appears at all, diagnose overlay touch dispatch and movement threshold before investigating the network/provider.
- [ ] Confirm that the idle bubble is 75% opaque by default, that 100% slider value looks fully solid, and that user-selected opacity persists after restarting the app.

Source/CI/signature proof is in `RELEASE_VERIFICATION_0.1.1.json`; do not mark these phone observations as passed until actually reproduced.

## Historical 0.1.3 crash checklist (navigation superseded by 0.1.5)

- [ ] Install the signed 0.1.3 APK **over** 0.1.2, without uninstalling or clearing data. Confirm the key and settings remain.
- [ ] Focus an empty note and tap the alien once. If Android reports an app crash, reopen AlienFlow.
- [ ] Scroll to **Crash diagnostic (private)**, tap **Copy crash diagnostic**, and paste that text into the development chat. It omits exception messages and dictated text. If it reports only the last checkpoint, this does not establish a Java exception.
- [ ] Use the evidence to identify the owner of the failure; fix that owner and rerun the actual device path before claiming crash resolution.

No Java handler can promise to catch a native crash or an OS-level process kill. No ADB/Termux debugging setup is required for the initial attempt to collect an app-owned report.

## 0.1.5 focus repair and diagnostic delivery — physical checks pending

- [ ] Install the signed 0.1.5 APK over the existing app without uninstalling or clearing data. Verify version 0.1.5 (6), retained settings/key/history and the approved Gray face.
- [ ] Confirm the **Diagnostics / share report** button immediately below the landing-screen version, and the same entry in the long-press alien menu.
- [ ] Focus an empty note, tap the alien, and observe actual Listening, words in the same field, and microphone release after Stop. Separately check manual edits and focus changes preserve existing text.
- [ ] If dictation stops/exits, return to Diagnostics and copy/share/save the actual report. Distinguish process exits from app-controlled stops by timestamps and session events. Do not clear diagnostic data first.
- [ ] The optional 3-second no-upload microphone probe reports sample counts and releases capture when complete or when leaving the screen. A passed probe does not establish WebRTC/provider correctness.

The framework UI tests and visually reviewed Robolectric renders passed in CI; these checkboxes remain phone-only acceptance, not implied passes.

## 0.1.7 field-lost recovery (not yet tested on Fold6)

- [ ] Install 0.1.7 over 0.1.6 with retained key and settings.
- [ ] Focus an empty text box, tap alien once, and wait for Listening. Speak, pause, observe text, tap stop.
- [ ] During a session, intentionally switch input fields. Dictation must continue recording; it must NOT edit the newly focused field. After stop, the transcript/history remains copyable.
- [ ] Reproduce previous immediate termination if possible. If it remains, confirm displayed status includes the initiating failure (provider/network/service) and distinguish actual saved audio from an empty capture.
- [ ] Send diagnostic with session events from the current run; do not treat older native crash history as new.

## 0.1.8 live transcription route (physical testing pending)

- [ ] Update over 0.1.7; confirm stored key, settings, history and approved Gray image survive.
- [ ] Check key & transcription model: require “Live connection accepted” only after WebSocket session acknowledgment; microphone must remain off.
- [ ] Focus an empty input, tap alien, speak a phrase, wait for live words and a completed transcription turn, speak again, stop and confirm microphone release.
- [ ] Position alien near top/keyboard; full failure notice must remain visible, and Diagnostics must show newest AF-N01 metadata.
- [ ] Share fresh diagnostic with timestamp if still failing; exclude API key and dictated text.
