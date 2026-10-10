# AlienFlow 0.1.4 Fold6 acceptance check

Install the signed APK over the existing version. Do not uninstall or clear app data.

## Crash repair path

- [ ] Confirm saved API key and settings remain.
- [ ] Focus a blank text field with Gboard selected.
- [ ] Tap the Gray alien once.
- [ ] Process remains alive for at least 30 seconds.
- [ ] Status reaches `Listening • tap the alien to stop`.
- [ ] Android microphone privacy indicator appears without an app-crash dialog.
- [ ] Speak: `AlienFlow microphone test number forty-two.`
- [ ] Words appear in the same field without duplication.
- [ ] Tap again; microphone privacy indicator disappears.

## Composition regression

- [ ] Open an empty ChatGPT input field.
- [ ] Tap the alien without typing a word first.
- [ ] No AF-E08 refusal occurs for zero-length candidate range.
- [ ] Begin typing an unfinished composing word and then tap the alien.
- [ ] AlienFlow refuses only when real composition evidence exists, preserving the unfinished word.

## Text ownership

- [ ] Dictate in the middle of existing text; prefix and suffix remain.
- [ ] Select a phrase and dictate; only the selection changes.
- [ ] Move the cursor or type manually during dictation; automatic edits stop without erasing user text.
- [ ] Switch fields while a response is outstanding; no stale result appears in the new field.

## Artwork and appearance

- [ ] Bubble is the new graphite/silver Gray alien, not the green cartoon head.
- [ ] Idle default remains 75% opaque.
- [ ] Size and opacity sliders still change the visible bubble and persist after restart.

Record observed results in `IMPLEMENTATION.md`. Do not mark universal editor compatibility complete from one field or one app.
