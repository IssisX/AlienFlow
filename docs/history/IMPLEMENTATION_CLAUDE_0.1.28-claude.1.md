> Historical checkpoint. The statement that nothing runs in the background is incorrect; see ../BILLING_INCIDENT_2026-10-17.md for the later automatic-upload finding.

# AlienFlow implementation — 0.1.28-claude.1 (29), branch `claude/alienflow`

This is a development APK built from `claude/alienflow`. 0.1.27-claude.1 and 0.1.28-claude.1 add the cost controls and interface changes below to 0.1.26-claude.1; everything else in this document is unchanged from 0.1.26-claude.1. The branch was created from `AlienFlow(chat)` at `2b3455d` (0.1.25-chat.1). It is **not signed off for a paid public launch**. [Paid release readiness](PAID_RELEASE_READINESS.md) lists the acceptance evidence still required. Previous checkpoint: [0.1.25-chat.1](history/IMPLEMENTATION_CHAT_0.1.25-chat.1.md).

## Cost controls (0.1.27–0.1.28)

The owner reported unexpected OpenAI charges. The app calls OpenAI only during an active dictation, a Check connection, or a recovery of saved audio; nothing runs in the background. Within a dictation it spent more than needed:

1. **Silence was streamed and billed.** Every microphone chunk was sent, including silence. A forgotten session, with the quiet-pause stop Off by default, streamed until someone stopped it. Now `SilenceGate` sends speech and the first 2 s of each pause, then holds silence locally. The next speech sends the most recent 0.5 s first so the first word is not clipped. Voiced audio is never held or dropped, every 850 ms commit still follows its speech, and the local recovery recording stays complete.
2. **No default stop.** "Stop after a quiet pause" now defaults to 15 seconds (0.1.27 used 30), adjustable from Off to 60 seconds.
3. **No session limit.** A dictation stops after "Maximum dictation length" (default 15 minutes, adjustable from 1 to 30, never Off), through the normal Stop path, so captured speech is still finished and saved. The status says why it stopped.
4. **Polish re-requested unchanged text.** The per-recording cleanup memo now remembers "no edits needed" answers, so each pause no longer re-sends earlier text that needed no change.

Limits: speech quieter than the voice threshold for more than 2 s is sent only from its last 0.5 s and otherwise stays in the local recording, where recovery can still transcribe it. The quiet stop also applies before the first word. The actual billing reduction has not been measured.

## Interface (0.1.28-claude.1)

The owner asked for a crisper, more professional look. The app screens now use one neutral dark palette with a single blue accent, smaller corner radii, tighter spacing and a consistent type scale. The home screen drops the marketing tagline for a plain "Dictation" heading, and screen descriptions say what each page contains. Option descriptions are shown in secondary text. Button labels, navigation and the floating alien artwork are unchanged.

## Defects repaired (0.1.26-claude.1)

Both defects were reproduced by tests against the shipped 0.1.25-chat.1 source (`2b3455d`) before the fix. CI repeats that replay on every build (`scripts/prove_live_polish_regression.py`), and packaging refuses to proceed without it.

1. **Live text waited for each pause commit (regression since 0.1.24).**
   - The live model streams words before the client commits a turn. Every event for an item without an acknowledged commit was tagged with no field (`-1`), so the field received a turn's words only after the 850 ms pause commit was acknowledged. Fixture tests always supplied an explicit field, so this real event order was never exercised.
   - **Owner of the fix:** the transport's audio coverage. Unacknowledged speech can only belong to the current field's open buffer or to an in-flight commit, so it is assigned to the current field only when every candidate is in that field. While an earlier field's commit is unacknowledged, the words wait. An acknowledged commit always decides a turn's field, and a provisional owner never overrides it.
   - **Shipped result:** field owners `[-1, -1, -1, 0, -1]`, expected `[0, -1, -1, 0, 2]`.

2. **Light polish discarded casing and punctuation whenever one word changed.**
   - The cleanup prompt asks for spelling repair, but the guard accepted or rejected the whole answer. Any word outside the five-entry typo list threw away the answer, including the sentence-case and punctuation repairs that join turns split at pauses. Users kept the raw per-turn "Sentence. Fragment. Fragment." text.
   - **Owner of the fix:** `PolishMerge`. Words that align keep the model's case and punctuation. Other edits revert to the recognized words: proposals never add words or drop recognized ones (fillers and exact repeats only with that setting). A same-length substitution is accepted only as a near-miss spelling of an ordinary lowercase word: letters only, at least four letters, same first letter, edit distance ≤1 (≤2 from seven letters), and not a negation, modal, uncertainty, quantity, time, direction or profanity word. Names, identifiers, numbers and paths keep the recognized spelling. Rewrite and Recognition only are unchanged. The cleanup memo stores only the usable merged text.
   - **Shipped result:** the reported proposal returned `store. And then. I bought seperate tickets. For Maria.`; it now returns `store, and then I bought separate tickets for Maria.`

3. **The alien could sit on top of the keyboard.**
   - The owner reports that Gboard's microphone key sometimes does nothing. A new emulator assertion found an overlap on an Android 15 tablet: the alien at `Rect(2094, 1353 - 2190, 1449)` was inside the keyboard window `Rect(0, 864 - 2560, 1600)`.
   - The alien's window is stacked above the keyboard, so it takes taps meant for the keys under it. Placement used the IME inset reported to the overlay itself, which is absent for a window above the keyboard.
   - **Owner of the fix:** the service now reads the input-method window from the accessibility window list and moves the alien above any keyboard it would overlap. The saved position returns when the keyboard closes.
   - This is a supported mechanism for the report, not a confirmed cause on the owner's phone.

## Device support

The app is to be sold on Google Play, so the minimum is now **Android 11 (API 30)**; it was Android 13. The Galaxy Note10+ ends at Android 12.

- **Android 13 and newer:** unchanged, full behavior.
- **Android 11 and 12:** Android provides no accessibility keyboard session there. Dictation goes live into empty text fields through the existing guarded node-action path. Fields with text and Termux are refused with AF-E17 and nothing is written.
- **Android 10:** excluded. Keeping the alien off the keyboard depends on Android 11 window-inset APIs.

Every Android 13 call goes through one guarded accessor, and release lint (NewApi) is clean at API 30. CI runs the real-editor emulator tests on Android 11 (medium phone), 12 (Pixel 6), 13 (small phone), 14 (7.6-inch foldable), 15 (Pixel tablet) and 16 (Pixel 6). Emulators are not physical-device evidence.

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
- The provider models and prompts, `medium` delay, 850 ms commit silence, history and encrypted key. Settings are unchanged except the quiet-pause default and the dictation length limit above.
- API 36, APK-only development, application ID and signing identity. No AAB was built.

## Known limits and unknowns

- **UNVERIFIED:** pre-commit deltas use the `item_id` that `input_audio_buffer.committed` later reports. This was also the assumption of every build before 0.1.24. If a real session ever shows provisional words that are never finalized (the Stop timeout reports "Final transcription timed out"), this assumption is false.
- Each 850 ms pause still finalizes a separate transcript. Polish now repairs casing and breaks across turns, but whether a longer commit silence or a different delay improves recognition is unmeasured. Use the evaluation variants before changing defaults.
- Live cleanup resends the whole completed field text after each turn, within a 4 s budget, plus one 2.5 s pass after Stop. Long dictations may exceed these budgets and keep recognized text unpolished; the evaluation reports cleanup time.
- **Revisions in browser text boxes can stop while the keyboard has a word marked as composing.** Gboard on the Android 13/14 images (2022–23 builds) marks the word before the cursor as composing after every outside insertion. In browser editors that report an unknown offset, AlienFlow can only revise by deleting before the cursor, and Android then counts the deletion from the start of the composing region (a unit test reproduced "Browspeech"). So AlienFlow keeps the earlier words exactly as written and continues with new speech. Fields with known offsets revise normally under such a mark. The Android 15 and 16 images do not show the re-marking. Whether current Gboard on real phones does is **UNVERIFIED**.
- The owner's Gboard microphone report has a supported mechanism (the keyboard overlap above), but it isn't confirmed on the phone. If it recurs, check whether the alien sits on or next to the keyboard, and whether turning AlienFlow off under Accessibility brings the microphone back.
- No physical microphone, paid provider request, Fold6 update, Note10+ install, or real-app switching was tested in this work.

## Verification

**0.1.28-claude.1 (29):** compiled source `eff228559b8fc26e1a0ae7dbd15e903649d341eb`. [Actions run 38921042130](https://github.com/IssisX/AlienFlow/actions/runs/38921042130) passed every job: 268 unit tests with no failures, errors or skips; release lint 0 errors; the shipped-source replays; the Android 16 emulator; and the Android 11–15 device matrix. Unsigned APK SHA-256 `e21047c06a9013fa031c36f7bd8f4e8adec0b9f7004aa13618e44aaf108b28d6` (bundle digest `sha256:270e9462228d9cedf4e75fd7b2580d6fe52764087128dc39c7e3c789c305f0e9`). The cost controls and the interface were not tested on a physical phone or with a real provider.

**0.1.26-claude.1 (27):**

Compiled source `2b1cd79f9ca98d19dc5cfe280c5d803a6c6098e3`. [Actions run 38798611692](https://github.com/IssisX/AlienFlow/actions/runs/38798611692) passed every job:

- **Release job:**
  - Shipped-source replays reproduce exactly their expected failures:
    - 0.1.17 Termux;
    - 0.1.23 app switch and stale cursor;
    - 0.1.24 writing;
    - 0.1.25 live ownership and light polish.
  - 256 unit tests, with no failures, errors or skips.
  - Release lint: 0 errors, 48 warnings.
  - Release, debug and instrumentation assembly.
  - Android 16 emulator (Pixel 6): real Chromium editor, the official installed Termux 0.118.3, and active app switching.
  - Packaging checks, giving an unsigned bundle.
- **Device matrix, real-editor emulator tests:**
  - Android 11, medium phone: empty-field live dictation; existing text refused (AF-E17).
  - Android 12, Pixel 6 profile: the same.
  - Android 13, small phone: browser, Termux and app switching. The browser revision was kept as written under a keyboard composing mark (the specified, text-intact outcome).
  - Android 14, 7.6-inch foldable: the same as Android 13.
  - Android 15, Pixel tablet: browser revision applied, plus Termux and app switching.
- **Tap checks:** every emulator tap requires the alien to be topmost and clear of any visible keyboard window.

**Delivered file:** the unsigned APK from the release bundle (artifact 12002944410, digest `sha256:83fc539141ee1c418d00970ec37d8a0ca35bc419e61ed32f2d9736d0a683dfc7`).
- APK SHA-256 `8868f33349be5adde812d5d12438b7400a471ca57131dfc4aff884dc0ffcaa95`.
- Package `io.github.issisx.alienflow`, version 0.1.26-claude.1 (27), minSdk 30, targetSdk 36.

**Not done here:**
- No signed APK: the private signing key is not available to this environment.
- No physical device, real microphone or paid provider request was used.
- Speech events in every test are fixtures.
