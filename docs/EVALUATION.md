# Real-audio evaluation

Unit tests and emulator runs use scripted recognition events. They prove ordering, ownership and editor behavior, not recognition quality. This harness measures the provider path with **your recordings**. It uses the same session configuration, client VAD commit policy, transcript ledger, cleanup request and light-polish merge as the app. Only the microphone and the text field are replaced: audio comes from a file and the result is scored against your reference transcript.

It calls the OpenAI API with your key and is billed to that account. It never runs in CI or in a normal build.

## Prepare recordings

Put pairs into one private folder outside the repository:

| File | Content |
| --- | --- |
| `NAME.wav` | PCM16, mono, 24 000 Hz (the app's capture format). Other formats are refused, not resampled: `ffmpeg -i in.m4a -ac 1 -ar 24000 -sample_fmt s16 NAME.wav` |
| `NAME.txt` | Exactly what you said, written the way you want it to appear: casing, punctuation, names, numbers. |
| `NAME.before.txt` / `NAME.after.txt` (optional) | Existing field text around the cursor, to measure a mid-sentence insertion. |

Cover the reported problems: natural pauses inside sentences, sentence case, names, amounts, negation and uncertainty, slang/profanity, the "thet/that" case, background noise and one long session. Record on the Fold6 if possible; device microphones differ.

## Run

```bash
export OPENAI_API_KEY=...            # environment only; never written to files or logs
export ALIENFLOW_EVAL_DIR=/private/recordings
bash gradlew -PalienflowEval :app:testDebugUnitTest --tests '*RealAudioEvaluation*'
```

Optional comparisons. Each value is a comma-separated list, and every combination runs against every recording:

| Variable | Default (= app) | Purpose |
| --- | --- | --- |
| `ALIENFLOW_EVAL_DELAY` | `medium` | Recognition delay: `minimal,low,medium,high,xhigh`. Higher delays give the model more audio context. |
| `ALIENFLOW_EVAL_SILENCE_MS` | `850` | Trailing silence before a turn is committed. Every commit becomes a separately finalized transcript. |
| `ALIENFLOW_EVAL_PROMPT` | empty | A transcription `prompt` describing the setting. The app sends none; measure before adopting one. |
| `ALIENFLOW_EVAL_MODE` | `B` | `A` = recognition only, `B` = light polish. |
| `ALIENFLOW_EVAL_LANGUAGE` | `en` | Same as the app's language setting. |

Recordings stream in real time, so a 1-minute recording takes about a minute per variant.

## Report

The run writes `app/build/alienflow-eval/report-<UTC time>.json`. **It contains your dictated text.** Keep it private and don't commit or upload it. For each recording and variant, `raw` (recognition) and `polished` (after cleanup) are scored separately:

- `word_error_rate` with `substitutions`, `deletions` and `insertions`: lexical errors, ignoring case and edge punctuation.
- `case_errors` / `case_error_rate`: casing differences on words that were recognized correctly. Excessive capitalization shows up here.
- `extra_sentence_breaks` / `missing_sentence_breaks`: sentence-ending punctuation inserted or lost between words. A pause cut into "Sentence. Fragment." shows up as an extra break.
- `meaning_words_missing` / `meaning_words_added`: negation, modality, uncertainty, quantities, times and numbers that differ from the reference. Any entry is a meaning risk; read the text.
- `missing_phrases`: three or more consecutive reference words that are absent.
- `latency_ms`: first text after speech onset (real-time runs only), each commit-to-final time, last final after the end of audio, and cleanup duration.

Cleanup is measured as one pass over the whole recognized text, the same as the final pass after Stop. Live cleanup during speech polishes growing prefixes and can differ slightly.

The run fails if any recording hit a protocol or provider problem; the reason is recorded as metadata only. Quality numbers never fail the run. They are evidence to compare variants and builds.

## What it cannot establish

It doesn't measure microphone capture on the phone, accessibility/editor behavior, app switching, or the Fold6 lifecycle. A live-model change on OpenAI's side can change results; record the date and source revision (included in the report) with any conclusion.
