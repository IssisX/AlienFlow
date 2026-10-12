# Native crash diagnosis and replacement transport — 2026-10-11

## Runtime evidence from the target phone

The Galaxy Z Fold6 report is the authoritative behavior evidence:

- Direct Android `AudioRecord` probe: 70,560 samples captured.
- Process exit: `REASON_CRASH_NATIVE`, signal 6 / `SIGABRT`.
- Crashing-thread code locations: libc `abort`, followed by sixteen frames in `base.apk!libjingle_peerconnection_so.so`.
- No Java/Kotlin failure record.

Therefore:

- Microphone permission and basic Samsung/Android capture are not the failed owner on that run.
- The packaged native WebRTC library is the supported process-crash owner.
- The exact internal WebRTC assertion remains unknown because the retained tombstone lacks symbols/abort-message content.
- Another option toggle inside the same native path would be a weaker repair than removing the crashing owner.

## Current OpenAI transcription contract

Official documentation checked on 2026-10-11:

- Realtime transcription guide: https://developers.openai.com/api/docs/guides/realtime-transcription
- Realtime client events: https://developers.openai.com/api/reference/resources/realtime/client-events
- WebSocket guide: https://developers.openai.com/api/docs/guides/voice-websockets?api=realtime

The documented transcription flow is:

- Session type `transcription`.
- Model `gpt-live-transcribe`.
- Input format `{ "type": "audio/pcm", "rate": 24000 }`.
- Base64 raw PCM16 chunks in `input_audio_buffer.append`.
- `input_audio_buffer.commit` at client-detected turn ends when `turn_detection` is null.
- Delta event `conversation.item.input_audio_transcription.delta`.
- Completion event `conversation.item.input_audio_transcription.completed`.
- Use `item_id` to reconcile turns because completion ordering is not guaranteed.

AlienFlow 0.1.6 implements that path using Android `AudioRecord` plus OkHttp WebSocket. It deliberately carries no WebRTC native library.

## Android ownership

- AudioRecord API: https://developer.android.com/reference/android/media/AudioRecord
- Foreground microphone service requirements: https://developer.android.com/develop/background-work/services/fgs/service-types#microphone
- Process-exit evidence: https://developer.android.com/reference/android/app/ApplicationExitInfo

The foreground service remains the Android lifecycle owner. The transport checks `RECORD_AUDIO` immediately before creating `AudioRecord`, releases the recorder in `finally`, and preserves captured PCM through the existing WAV recovery owner.

## Security and product boundary

OpenAI recommends a trusted server for durable production API-key ownership and recommends WebRTC for many browser/mobile voice clients. AlienFlow is a personal BYOK application whose owner explicitly chose local encrypted key storage. The direct WebSocket removes the proven crashing native dependency; it does not convert phone-held credentials into a public-distribution architecture.

## Remaining uncertainty

No automated test can establish the Fold6 end-to-end result. The repair is complete at the supported owner and verified as built, but runtime acceptance requires the actual phone to survive capture, receive transcript events, and edit the focused field.
