# AlienFlow2 implementation checkpoint — 0.1.9

Repository: IssisX/AlienFlow. Branch: AlienFlow2. Base: 1f5fe9693e030e45155e1ec54de0c67baa8ee1f4. Date: 2026-10-12.

## Owner-observed baseline

The owner confirmed that 0.1.8 works on the Fold6. Preserve its AudioRecord capture, transcription-intent WebSocket, live ordinary-editor insertion, light corrections, selected keyboard, encrypted key and existing recovery/history formats. The previous implementation checkpoint is retained at history/IMPLEMENTATION_0.1.8.md. The concurrent build/first-apk branch is untouched.

## Requested changes

- Quiet successful completion: remove the routine completion notice beside the alien. An unavailable editor uses a brief plain-language message; genuine connection failures retain their details in Diagnostics. A final transcript already confirmed in the editor remains delivered even if edit authority is later revoked.
- Termux: the bubble also recognizes Termux's focused non-editable terminal View with its TYPE_NULL keyboard connection. The terminal adapter inserts corrected text once, after Stop and completion, through Android's accessibility InputConnection. It neither moves the terminal cursor nor deletes existing terminal input. No Enter, newline, Escape, Tab or other control characters are emitted. Control characters in recognized formatting become spaces. This is a terminal-specific adaptation, not a change to live document editing. No silent retries follow uncertain commits, and target changes prevent insertion.
- Brand icon: a newly generated silver alien in a navy circular badge, with dark outer and light inner borders. The asset is used for both bubble and launcher, retains 75% default idle opacity, and is visually inspected at small sizes on white and dark backgrounds. Packaged asset SHA-256: 48d0ce0aacba2fd4264100fb1af1b6a73f54290f6c5296f4adc07d3974a50671.
- Release identity: 0.1.9, versionCode 10, same application ID and existing permanent signing certificate. CI runs independently per branch, so the other model's workflow is not canceled or blocked by a shared concurrency group.

## Evidence and limits

Termux's primary TerminalView source (blob f4f82ad7c51efd95db914f26d39dc6a535a2f7e4) creates a BaseInputConnection, consumes commitText into the terminal and clears its Editable. Ordinary document-span corroboration cannot work against that empty IME buffer. The new regression fixture exercises Android BaseInputConnection with those documented consume-and-clear semantics; it is not installed-Termux proof.

Primary references:
- https://github.com/termux/termux-app/blob/master/terminal-view/src/main/java/com/termux/view/TerminalView.java
- https://developer.android.com/reference/android/accessibilityservice/InputMethod.AccessibilityInputConnection

Required release checks: unit tests, release lint, APK packaging, exact compiled revision, artwork hash, unchanged package ID and matching installed certificate. Actual Termux visibility/insertion, final caption behavior and the new icon on the physical Fold6 still require a device observation. The owner's 0.1.8 live dictation confirmation is baseline evidence, not proof of these new changes.

## Phone check

Install over 0.1.8 without clearing data. Open Termux, tap its terminal with Gboard visible, tap the alien, speak, Stop, and verify the words arrive once without submitting the line. Repeat in an ordinary text field: confirm live insertion, corrections and quiet completion. If the target changes, the transcript must be retained rather than inserted into another app.
