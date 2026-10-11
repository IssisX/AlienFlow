# AlienFlow

Personal Android dictation with a movable Gray alien bubble, live text in the focused field, conservative corrections, and a user-supplied OpenAI API key.

## Current signed update: 0.1.6 (7)

The Fold6 diagnostic report finally identified the crash owner instead of leaving us to perform interpretive dance around a green microphone dot:

- Android recorded `REASON_CRASH_NATIVE`, signal 6 (`SIGABRT`).
- The crashing thread entered `libjingle_peerconnection_so.so` in every application-owned frame retained by Android.
- AlienFlow's separate three-second Android `AudioRecord` probe captured 70,560 samples successfully.
- No Java/Kotlin exception was recorded.

That evidence supports a narrow repair: **remove the crashing native WebRTC library from the APK and replace its transport ownership**, rather than adding another permission or randomly changing microphone settings.

AlienFlow 0.1.6 uses Android `AudioRecord` for 24 kHz mono PCM16 capture and an authenticated OpenAI Realtime WebSocket for `gpt-live-transcribe`. It sends Base64 `input_audio_buffer.append` events and client-VAD `input_audio_buffer.commit` events. The existing transcript ledger, guarded editor writes, local WAV recovery, cleanup modes, history, encrypted key storage, overlay behavior, opacity settings, and approved Gray artwork remain.

The APK contains **no native libraries** and specifically no `libjingle_peerconnection_so.so`.

## Install and test

Install the separately delivered signed **AlienFlow-0.1.6.apk** over the existing installation. Do not uninstall or clear app data. Confirm the landing screen shows **Version 0.1.6 (7)**.

1. Focus an empty ordinary text field with Gboard active.
2. Tap the approved Gray alien once.
3. Confirm AlienFlow remains alive and reaches `Listening`.
4. Speak one short sentence, pause, and confirm text appears in that same field.
5. Tap the alien again and confirm the Android microphone indicator clears.

If the session stops, errors, or the process exits, open **Diagnostics / share report**, copy the new report, and preserve the newest timestamp. Older WebRTC native-crash records may remain in Android's historical list even though 0.1.6 no longer packages that library.

## Verified build scope

[GitHub Actions run 38159376803](https://github.com/IssisX/AlienFlow/actions/runs/38159376803) compiled source `a60c0278f554b9805e93240f9d324bcbbc902502` and passed:

- 73 automated tests; zero failures, errors, or skips.
- Release lint with zero errors and 28 recorded warnings.
- Release assembly, package/version identity, non-debuggable state, and unsigned-artifact checks.
- A fail-closed packaging gate proving the APK has no `.so` files or native-code declaration.
- Exact approved Gray artwork hash verification.
- Android-framework renders of the landing and diagnostics activities.

The downloaded bundle matched GitHub's SHA-256 digest. All 39 manifest-listed bundle members matched their hashes. The final APK was signed with the permanent AlienFlow certificate, verified with APK Signature Scheme v3, and compared against the unsigned payload: all 25 original entries were byte-identical; only three signature metadata entries were added.

[Release verification](docs/RELEASE_VERIFICATION_0.1.6.json) and [signature output](docs/APK_SIGNATURE_0.1.6.txt) identify the exact installer.

**Not yet proven:** the physical Fold6 live dictation path. CI can reject a bad build; it cannot impersonate your Samsung audio stack, Gboard, Chrome editor, network, or OpenAI account.

## Product defaults and continuity

- Exact approved Gray alien; movable bubble; 75% idle opacity by default with sliders.
- Existing keyboard stays selected.
- Live in-field text and very light correction by default.
- Wording, negation, numbers, slang, and profanity preserved.
- Seven days of text history by default; interrupted audio recoverable.
- No AlienFlow word quota.

The package ID, permanent signing identity, preferences, key-storage format, history format, and pending-audio format were preserved for an in-place update.

## Engineering handoff

Start with `AGENTS.md`, [product requirements](docs/PRODUCT_SPEC.md), [current checkpoint](docs/IMPLEMENTATION.md), [native-crash repair research](docs/RESEARCH_2026-10-11_NATIVE_CRASH.md), and the current release verification. The branch is `build/first-apk`; PR #1 remains the integration record until physical acceptance is complete.
