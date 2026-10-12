# AlienFlow2 implementation checkpoint — 0.1.11

Repository: IssisX/AlienFlow. Branch: AlienFlow2. Baseline: 132cc25dd0349b3adc083ebdd536c809e05f5ce3. Date: 2026-10-12. VersionCode: 12.

## Owner request and source diagnosis

The owner reports a one-to-two-second tap-to-listening wait and cannot tell when it is safe to speak. The accepted artwork stays. Routine status text remains suppressed beside the icon. Speed and a small polished readiness indicator are the requested release scope.

The 0.1.10 transport starts AudioRecord only after an authenticated WebSocket and session-update acknowledgement. That serial dependency necessarily places network setup before microphone availability. Routine startup checkpoints also synchronously commit preferences on the tap/capture path. These are confirmed source mechanisms, not a measured attribution of every millisecond on the Fold6.

## Implemented behavior

- One transport starts its physical microphone and WebSocket concurrently, only after the foreground microphone service has been promoted. The first accepted PCM frame drives readiness. No idle microphone or background paid session is introduced.
- A bounded 15-second startup FIFO preserves all captured PCM and local turn boundaries until validated session acknowledgement. Its single transport lock orders replay before new frames; successful replay is consumed once. A send failure, overflow, cancellation, stale socket or acknowledgement timeout cannot fake an intact stream. The same complete private WAV remains the recovery authority.
- Stop first stops and joins the real capture task. If setup is still pending, finalization awaits the validated connection for at most 15 seconds, then flushes and commits in capture order or retains the WAV. Starting and immediately stopping cannot silently omit the first words.
- AudioRecord reads 20ms PCM frames instead of 100ms. For a nonempty final network turn shorter than 100ms, the network envelope gets only the missing silence; the private WAV and audio-coverage metadata contain actual microphone bytes. No empty commit or fabricated recording is produced.
- The documented gpt-live-transcribe delay changes from low to minimal. This requests earlier partial text and can trade accuracy for latency; it is not a fixed millisecond guarantee. Existing word-preserving cleanup and guarded editor ownership remain.
- The approved icon gets a tiny dual-contrast status jewel: pale rotating arc while the microphone starts; steady green dot once actual frames are captured and can be buffered; thin cyan rim once the validated server session is connected; amber dot for local recovery capture; small amber arc while finishing. A server acknowledgement alone never means microphone-ready. Neither elapsed time nor animation controls readiness. The existing nonfocusable overlay and selected keyboard remain.
- Routine startup checkpoint fsyncs are serialized on a dedicated I/O owner. The diagnostic stage and trace update in memory immediately. Fatal Kotlin failures drain that queue before writing their private error record, with a bounded wait. An abrupt native/process kill can precede the latest asynchronous checkpoint; Android exit evidence and previous durable checkpoints remain available.
- Diagnostics records monotonic tap-to-first-frame, tap-to-session-acknowledgement and tap-to-first-text observations at the coordinator boundary. It records numeric durations only. These values are actual phone observations after use, not synthetic benchmark promises.

## Verification contract

Required regressions use the production transport, startup FIFO, actual PCM/WAV pipeline, state selector and native Android Canvas renderer with explicitly simulated microphone/network boundaries. They cover capture before connection, quick Stop before acknowledgement, turn ordering, one-shot replay, failed replay, short final envelope, in-flight final read, access denial after capture, cancellation and late acknowledgement. Existing editor, terminal, synchronization, focus, storage, crash privacy and handshake gates remain required.

Actual activity and production-indicator renders on both light and dark backgrounds are required in the release bundle. Source 0ed89b514b0adb0626f16a61381dd799d1552d7b passed all 116 tests with zero skipped, release lint with 32 issues and zero errors, release compilation, package verification and all 62 bundle member hashes. Both production-indicator native renders were visually inspected. The APK was signed with the existing certificate; all 25 payload entries remain unchanged. Exact run/artifact/signature/hash evidence is in RELEASE_VERIFICATION_0.1.11.json. No real provider account or physical Fold6 was exercised by these JVM/CI checks.

## Phone acceptance still required

Install over 0.1.10. Tap and begin speaking when the green dot appears; a cyan rim confirms the remote session. Try a short phrase with immediate Stop while startup is still connecting. Check Diagnostics startup timings on Wi-Fi and mobile data. Compare recognized words, names, numbers and negations with 0.1.10 because minimal delay has a latency/accuracy tradeoff. Recheck Termux, then a fresh app/Chrome field, manual edits and the diagnostic-notice setting. Actual provider latency, accuracy and hardware/editor compatibility are unverified until these phone checks.

Prior checkpoint: history/IMPLEMENTATION_0.1.10.md. Current primary references and provider decision: RESEARCH_2026-10-12_STARTUP_LATENCY.md.
