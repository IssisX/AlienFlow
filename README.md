# AlienFlow — `claude/alienflow` branch

Repository: IssisX/AlienFlow. Working branch: `claude/alienflow`, created from `AlienFlow(chat)` at `2b3455d` (0.1.25-chat.1). This branch writes only itself. `AlienFlow(chat)`, `AlienFlow2`, `build/first-apk` and `main` are untouched.

## Development update: 0.1.27-claude.1 (28)

- **Lower OpenAI cost:** long silences are no longer uploaded, dictation stops after 30 seconds of quiet by default and after 15 minutes at most, and polish no longer re-sends text that needed no change. AlienFlow only calls OpenAI while a dictation is running, when you tap Check connection, or when recovering a saved recording.

### 0.1.26-claude.1 (27)

- **Live text during speech:** words now reach the focused field while you speak. Since 0.1.24 they waited for each pause. When you switch fields, words that could belong to the previous field still wait for their own commit, so they never enter the new app.
- **Light polish:** casing and punctuation are applied even when the model also proposes a word change that isn't allowed. Only that word keeps its recognized form. Near-miss misspellings of ordinary words can be corrected. Names, numbers, negation, uncertainty and other meaning words keep the recognized spelling.
- **Real-audio evaluation:** [docs/EVALUATION.md](docs/EVALUATION.md) measures recognition and polish with your recordings.
- **Keyboard overlap:** the alien no longer sits on top of the keyboard. On an Android 15 tablet emulator it covered keys and would take their taps, a supported explanation for Gboard's microphone key doing nothing.
- **Android 11 and newer** (previously 13), including the Galaxy Note10+ (Android 12). On Android 11–12, dictation goes live into empty text fields. Fields with existing text and Termux need Android 13 or newer.
- **Disclosures:** the switching and surrounding-text descriptions now match actual behavior.

Details, evidence and limits: [docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md).

## Installing

CI produces an **unsigned** APK inside the Actions bundle. An unsigned APK cannot be installed. Sign it with the existing permanent key, following the private signing steps in [docs/RELEASE.md](docs/RELEASE.md), and install over the current app without uninstalling or clearing data. Application ID `io.github.issisx.alienflow` and the original certificate are unchanged.

## Phone acceptance still needed

1. Dictate with natural pauses and confirm the words appear while you speak.
2. Check that sentences continue across pauses without extra capitals.
3. Switch apps mid-dictation and confirm that nothing from the earlier app appears in the new one.
4. Insert mid-sentence and confirm that existing text stays intact.
5. Confirm Termux still receives one final insertion without Enter.

Emulator and unit tests use scripted recognition. They don't establish microphone accuracy or Fold6 behavior.

## Release scope

API 36 and APK-only development. No AAB, store submission, billing, new permissions, analytics or data migration.
