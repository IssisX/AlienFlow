# AlienFlow implementation checkpoint — 0.1.6

Repository: `IssisX/AlienFlow`  
Branch: `build/first-apk`  
Compiled source: `a60c0278f554b9805e93240f9d324bcbbc902502`  
Date: 2026-10-11

The full prior 0.1.5 checkpoint is preserved at [history/IMPLEMENTATION_0.1.5.md](history/IMPLEMENTATION_0.1.5.md). This file is the current authority.

## Observed failure and supported cause

The owner's Galaxy Z Fold6 diagnostic report supplied the missing discriminator:

- Android 16/API 36, ARM64, microphone and notification permissions granted, accessibility bound.
- The isolated Android microphone probe captured 70,560 PCM samples successfully.
- No Java/Kotlin uncaught exception was captured.
- Android recorded multiple native crashes. The retained tombstone shows signal 6 / abort and every application-owned crashing-thread frame inside `base.apk!libjingle_peerconnection_so.so`, build ID `8939406b3b9fa259`.

This establishes that basic Android microphone capture works under the granted permission and that the delivered native WebRTC library owned the observed process death. It does not identify the exact internal WebRTC assertion because Android did not retain symbols or the abort message, but it is sufficient to stop patching around that library.

## Repair

AlienFlow no longer depends on or packages WebRTC.

`RealtimeTransport` now owns one direct path:

1. Open an authenticated OpenAI Realtime WebSocket.
2. Send a transcription `session.update` for `gpt-live-transcribe` with 24 kHz PCM input and `turn_detection: null`.
3. After the server acknowledges the session, create one Android `AudioRecord` using `VOICE_RECOGNITION`, mono PCM16 at 24 kHz.
4. Persist each PCM chunk to the existing `PendingAudio` WAV owner.
5. Base64-encode the same raw PCM bytes and send `input_audio_buffer.append`.
6. Use the existing conservative client-side speech/silence detector to send `input_audio_buffer.commit`.
7. Feed returned transcript delta/completion events into the existing transcript ledger and guarded editor bridge.

The transport keeps bounded WebSocket queue ownership, exact connection generations, explicit local-only/reconnect state, and audio-coverage uncertainty. Connection loss preserves local capture/recovery rather than pretending remote coverage succeeded.

## Preserved contracts

The following were not reset or migrated:

- Application ID and permanent signing certificate.
- Encrypted API-key storage.
- Preferences, bubble positions, 75% default opacity, and approved Gray artwork.
- History and pending WAV formats.
- Accessibility overlay and guarded editor ownership.
- Transcript ordering, correction modes, profanity/text preservation, cancellation, and recovery semantics.

The foreground microphone service still owns Android's microphone lifecycle. The service rechecks `RECORD_AUDIO` immediately before `AudioRecord` creation. There is no `SYSTEM_ALERT_WINDOW` substitution and no hidden activity workaround.

## Current API contract

Official OpenAI documentation checked on 2026-10-11 specifies `gpt-live-transcribe` transcription sessions, 24 kHz `audio/pcm`, Base64 `input_audio_buffer.append`, explicit `input_audio_buffer.commit`, and client-side VAD when turn detection is null. AlienFlow implements that contract over WebSocket. The personal BYOK design keeps the account key encrypted on the phone; this remains a personal-app tradeoff, not a recommended public multi-user secret architecture.

## Verification tied to the compiled revision

[GitHub Actions run 38159376803](https://github.com/IssisX/AlienFlow/actions/runs/38159376803) passed for source `a60c0278f554b9805e93240f9d324bcbbc902502`:

- 73 tests executed; zero failures, errors, or skips.
- Test suites: crash journal 2, diagnostics UI 2, dictation logic 15, editor evidence 12, editor widgets 6, focus routing 6, native trace parser 3, pending audio 22, realtime protocol/capture policy 5.
- Release lint: zero errors, 28 warnings. No baseline or weakened gate was added.
- Release assembly and package/version identity passed.
- Packaging failed closed unless the APK had no native libraries, no `native-code:` declaration, and no `libjingle_peerconnection_so.so`.
- The exact approved artwork SHA-256 was found once in the APK.
- Framework UI renders for version 0.1.6 and the diagnostics controls were produced.

Artifact `11709204998`, `AlienFlow-0.1.6-unsigned-release-bundle.zip`:

- Size: 3,200,109 bytes.
- SHA-256: `95a838b85bf61be6ce5d055297cb31352287451e3878e44967c6e95da8ac95b1`.
- All 39 member hashes in `build-info.json` were verified after download.

Final signed APK:

- File: `AlienFlow-0.1.6.apk`.
- Size: 4,323,480 bytes.
- SHA-256: `17db5338ac297d1820a4acbc17bfd08f3e8acecf158b1bdb4cb7ab88d90dccd5`.
- APK Signature Scheme v3 verified.
- Certificate SHA-256: `d79e74b3a4c24ee225d1220c2c374c6f958520d4f3257fcfb399ff09bf5c965f`.
- All 25 unsigned payload entries remained byte-identical; only `META-INF/ALIENFLO.RSA`, `META-INF/ALIENFLO.SF`, and `META-INF/MANIFEST.MF` were added by signing.

Machine-readable evidence is in [RELEASE_VERIFICATION_0.1.6.json](RELEASE_VERIFICATION_0.1.6.json). Actual signer output is in [APK_SIGNATURE_0.1.6.txt](APK_SIGNATURE_0.1.6.txt).

## Remaining acceptance gate

The Fold6 must still exercise the changed path. A passing build does not prove the phone, editor, network, or provider integration.

Required first run:

1. Install over 0.1.5 without clearing data and confirm version 0.1.6 (7).
2. Focus an empty ordinary field with Gboard.
3. Tap the alien; confirm the process remains alive and reaches `Listening`.
4. Speak, pause, observe transcript text, then stop.
5. Confirm the microphone indicator clears and history/recovery state is truthful.

If it fails, collect the new diagnostics. Old `libjingle` crash records can remain in Android's historical list; the relevant evidence is the newest timestamp and the 0.1.6 session events. Do not call the native crash fixed until the same phone path survives.
