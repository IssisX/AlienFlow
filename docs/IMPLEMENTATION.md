# AlienFlow implementation — 0.1.26-claude.1 (27), branch `claude/alienflow`

This is a development APK built from `claude/alienflow`, which was branched from `AlienFlow(chat)` at `2b3455d` (0.1.25-chat.1). It is **not signed off for a paid public launch**. [Paid release readiness](PAID_RELEASE_READINESS.md) lists the acceptance evidence still required. Previous checkpoint: [0.1.25-chat.1](history/IMPLEMENTATION_CHAT_0.1.25-chat.1.md).

## Defects repaired

Both defects were reproduced by tests against the shipped 0.1.25-chat.1 source (`2b3455d`) before the fix. CI repeats that replay on every build (`scripts/prove_live_polish_regression.py`), and packaging refuses to proceed without it.

1. **Live text waited for each pause commit (regression since 0.1.24).**
   - The live model streams words before the client commits a turn. Every event for an item without an acknowledged commit was tagged with no field (`-1`), so the field received a turn's words only after the 850 ms pause commit was acknowledged. Fixture tests always supplied an explicit field, so this real event order was never exercised.
   - **Owner of the fix:** the transport's audio coverage. Unacknowledged speech can only belong to the current field's open buffer or to an in-flight commit, so it is assigned to the current field only when every candidate is in that field. While an earlier field's commit is unacknowledged, the words wait. An acknowledged commit always decides a turn's field, and a provisional owner never overrides it.
   - **Shipped result:** field owners `[-1, -1, -1, 0, -1]`, expected `[0, -1, -1, 0, 2]`.

2. **Light polish discarded casing and punctuation whenever one word changed.**
   - The cleanup prompt asks for spelling repair, but the guard accepted or rejected the whole answer. Any word outside the five-entry typo list threw away the answer, including the sentence-case and punctuation repairs that join turns split at pauses. Users kept the raw per-turn "Sentence. Fragment. Fragment." text.
   - **Owner of the fix:** `PolishMerge`. Words that align keep the model's case and punctuation. Other edits revert to the recognized words: proposals never add words or drop recognized ones (fillers and exact repeats only with that setting). A same-length substitution is accepted only as a near-miss spelling of an ordinary lowercase word: letters only, at least four letters, same first letter, edit distance ≤1 (≤2 from seven letters), and not a negation, modal, uncertainty, quantity, time, direction or profanity word. Names, identifiers, numbers and paths keep the recognized spelling. Rewrite and Recognition only are unchanged. The cleanup memo stores only the usable merged text.
   - **Shipped result:** the reported proposal returned `store. And then. I bought seperate tickets. For Maria.`; it now returns `store, and then I bought separate tickets for Maria.`

The account screen and the accessibility description now describe the implemented behavior:
- Switching fields sends new speech into the new field.
- With Light polish or Rewrite, up to 512 characters before and 256 after the cursor are sent as read-only context.
- Recognition only sends no field text.

## Real-audio evaluation path

[EVALUATION.md](EVALUATION.md) describes an opt-in, paid measurement with the owner's recordings. It uses the same session configuration, VAD commit policy, ledger, cleanup request and merge as the app. It reports these separately, for raw and polished text:
- word errors;
- casing on correctly recognized words;
- inserted or missing sentence breaks;
- meaning-word differences;
- missing phrases;
- latency.

It can compare recognition delay, commit silence and a transcription prompt without changing the app. It is verified against a scripted provider. **It has not been run with real recordings or a real key**: no recordings or paid-provider authorization were available.

## Unchanged

- The selected keyboard and the nonfocusable bubble with its 75% default opacity and approved artwork.
- Live in-field text, light polish by default, the final cleanup pass after Stop, bounded context, editor-generation and segment ownership, unknown-offset keyboard handling.
- Termux final-only insertion without Enter.
- The audio, provider models and prompts, `medium` delay, 850 ms commit silence, settings, history and encrypted key.
- API 36, APK-only development, application ID and signing identity. No AAB was built.

## Known limits and unknowns

- **UNVERIFIED:** pre-commit deltas use the `item_id` that `input_audio_buffer.committed` later reports. This was also the assumption of every build before 0.1.24. If a real session ever shows provisional words that are never finalized (the Stop timeout reports "Final transcription timed out"), this assumption is false.
- Each 850 ms pause still finalizes a separate transcript. Polish now repairs casing and breaks across turns, but whether a longer commit silence or a different delay improves recognition is unmeasured. Use the evaluation variants before changing defaults.
- Live cleanup resends the whole completed field text after each turn, within a 4 s budget, plus one 2.5 s pass after Stop. Long dictations may exceed these budgets and keep recognized text unpolished; the evaluation reports cleanup time.
- No physical microphone, paid provider request, Fold6 update, or real-app switching was tested in this work.

## Verification

Recorded after CI completes, below.
