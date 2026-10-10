# AlienFlow 👽

Personal native Android dictation with a movable alien bubble, live text in the focused field, conservative corrections, and a user-supplied OpenAI API key.

## Current signed test build: 0.1.4

AlienFlow 0.1.4 targets two phone-observed failures from 0.1.3:

- Android showed the microphone privacy indicator and the process died about one second later. The previous realtime path gave microphone ownership to the WebRTC peer and also requested a second independent `JavaAudioDeviceModule` recording demand at that same boundary. 0.1.4 removes the duplicate demand, uses one peer-owned microphone lifecycle, and disables hardware AEC/noise suppression that this transcription-only app does not need.
- A blank ChatGPT field incorrectly produced `[AF-E08] Finish the keyboard's composing word`. The accessibility callback treated a zero-length candidate range such as `0..0` as active composition and treated unknown composition as proof. 0.1.4 requires explicit positive-length composition evidence while retaining full text/cursor corroboration before any edit.

The bubble and launcher now use a restrained graphite/silver “Gray” alien with dark almond eyes and a cyan active ring instead of the bright green cartoon head.

[GitHub Actions run 38069975789](https://github.com/IssisX/AlienFlow/actions/runs/38069975789) passed 58 tests, release lint with zero errors, release assembly, package identity checks, and 16 KiB native/ZIP alignment for compiled source `b39de2b1b73e89c29bb918dd5710a207084240e6`. The signed installer uses the original package and certificate. See [0.1.4 verification](docs/RELEASE_VERIFICATION_0.1.4.json).

This build still requires the Fold6 runtime check. CI cannot prove that the native microphone crash is gone or that live text works in every editor.

## Install and test

Install the signed `AlienFlow-0.1.4.apk` over the existing version. Do not uninstall or clear app data; the package ID and signing identity are unchanged.

1. Focus a blank text field with Gboard selected.
2. Tap the alien once. Confirm the process stays alive and reaches **Listening**.
3. Speak one short phrase and confirm words appear.
4. Tap again and confirm the microphone privacy indicator disappears.
5. Test an empty ChatGPT field; a zero-length keyboard candidate range must no longer trigger AF-E08.

If the app still dies, preserve the exact timing and last visible state. Do not attribute the remaining failure without runtime evidence.

## Product defaults

- Movable Gray alien bubble, approximately Gboard microphone size.
- 75% opaque idle default, with size and opacity sliders.
- Existing keyboard remains selected.
- Live text in the focused field.
- Very light correction by default; wording, numbers, negation, slang, and profanity are preserved.
- Seven days of local text history by default.
- No AlienFlow word quota; provider, connectivity, storage, and device limits still apply.

## Engineering handoff

Read `AGENTS.md`, `docs/PRODUCT_SPEC.md`, and `docs/IMPLEMENTATION.md` before editing. The implementation branch is `build/first-apk`; pull request #1 remains the integration record. GitHub Actions builds an unsigned bundle. The permanent signing key stays outside GitHub and CI.
