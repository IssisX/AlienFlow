# Live transcription handshake, primary-source review — 2026-10-12

1. Official OpenAI Node SDK at [internal-base.ts](https://raw.githubusercontent.com/openai/openai-node/master/src/realtime/internal-base.ts) defines mutually exclusive model-backed and transcription-intent Realtime connections. Its URL builder sets `intent=transcription` for the latter and `model` for conversational sessions. The shipped 0.1.6/0.1.7 code incorrectly used `?model=gpt-live-transcribe` for a transcription-only session.

2. Current [Realtime transcription guide](https://developers.openai.com/api/docs/guides/realtime-transcription): send a `session.update` with `type: transcription`, `audio.input.format` of `audio/pcm` at 24 kHz, and `audio.input.transcription.model=gpt-live-transcribe`. Append Base64 PCM16 frames, client-commit turns with `input_audio_buffer.commit`, process delta and completed transcript events by item ID. Explicitly keep turn_detection null. The existing audio config is retained.

3. Pinned OkHttp 4.12.0 provides a WebSocket.Factory and MockWebServer with an actual HTTP upgrade. Local tests can check the production request URL, update event, acknowledged session, rejected type/model and HTTP failure without user credentials or provider spend.

Limits: An acknowledged loopback WebSocket and passing unit tests do not establish a successful OpenAI account session or microphone behavior on the physical Fold6. The screenshots' cropped error prefix cannot be inferred.
