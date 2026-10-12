# Native WebRTC and live transcription integration evidence

Research date: 2026-10-10. Read-only implementation research; no app/build source changes, subagents, account key use, or paid API calls. This report establishes available interfaces, not a successful device/API connection.

## 1. Pinned Android artifact

Use `implementation("io.github.webrtc-sdk:android:150.7871.01")` from Maven Central. The unprefixed artifact supplies `org.webrtc`; `android-prefixed` instead supplies `livekit.org.webrtc`.

Authoritative sources fetched:

- [Maintainer README](https://github.com/webrtc-sdk/android) names this coordinate.
- [Maven metadata](https://repo.maven.apache.org/maven2/io/github/webrtc-sdk/android/maven-metadata.xml) reports latest/release `150.7871.01`, lastUpdated `20260905071517`.
- [Published POM](https://repo.maven.apache.org/maven2/io/github/webrtc-sdk/android/150.7871.01/android-150.7871.01.pom) declares AAR packaging.
- [Exact AAR](https://repo.maven.apache.org/maven2/io/github/webrtc-sdk/android/150.7871.01/android-150.7871.01.aar) was downloaded and inspected: 49,147,033 bytes; SHA-256 `0a1627b1a48c2bc17d9a40d62fc47bd45166f44a311e95917f147c402de379b0`; native libraries for arm64-v8a, armeabi-v7a, x86_64 and x86. Manifest minSdk21; classfile major61 (Java17). ARM64 ELF64 program headers were inspected: all three PT_LOAD alignments are 0x4000 (16KiB); offsets/virtual addresses are respectively (0x0,0x0), (0xb8b0c0,0xb8f0c0), (0xbb6530,0xbbe530), with correct congruence modulo16KiB. This establishes ELF load alignment; APK zip alignment remains a packaging gate.

No local `javap` exists. Public signatures and selected ownership bytecode below were read from this exact AAR's `classes.jar` using a classfile parser. Moving branch source has already changed `requestStartRecording` and `prewarmRecording` return types to boolean; the pinned AAR returns void. Do not copy return-value assumptions from moving source.

## 2. Exact relevant Java signatures in the pinned AAR

```java
PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions options); // static void
PeerConnectionFactory.builder(); // static PeerConnectionFactory.Builder
PeerConnectionFactory.InitializationOptions.builder(Context context);
// InitializationOptions.Builder:
createInitializationOptions(); // InitializationOptions
// PeerConnectionFactory.Builder:
setAudioDeviceModule(AudioDeviceModule adm); // Builder
createPeerConnectionFactory(); // PeerConnectionFactory
// PeerConnectionFactory:
createAudioSource(MediaConstraints constraints); // AudioSource
createAudioTrack(String id, AudioSource source); // AudioTrack
createPeerConnection(PeerConnection.RTCConfiguration config, PeerConnection.Observer observer); // PeerConnection nullable
void dispose();

JavaAudioDeviceModule.builder(Context context); // static Builder
// JavaAudioDeviceModule.Builder:
setSamplesReadyCallback(JavaAudioDeviceModule.SamplesReadyCallback callback); // Builder
setAudioRecordStateCallback(JavaAudioDeviceModule.AudioRecordStateCallback callback); // Builder
setAudioRecordErrorCallback(JavaAudioDeviceModule.AudioRecordErrorCallback callback); // Builder
setInputSampleRate(int rate); // Builder
setAudioFormat(int androidAudioFormat); // Builder
setUseStereoInput(boolean enabled); // Builder
createAudioDeviceModule(); // JavaAudioDeviceModule
// JavaAudioDeviceModule:
void requestStartRecording();
void requestStartRecording(AudioProcessingOptions options);
void requestStopRecording();
void prewarmRecording();
void setAudioRecordEnabled(boolean enabled);
void setMicrophoneMute(boolean mute);
void release();
// SamplesReadyCallback:
void onWebRtcAudioRecordSamplesReady(JavaAudioDeviceModule.AudioSamples samples);
// AudioSamples:
int getAudioFormat(); int getChannelCount(); int getSampleRate(); byte[] getData();
// AudioRecordStateCallback:
void onWebRtcAudioRecordStart(); void onWebRtcAudioRecordStop();
// AudioRecordErrorCallback:
void onWebRtcAudioRecordInitError(String errorMessage);
void onWebRtcAudioRecordStartError(JavaAudioDeviceModule.AudioRecordStartErrorCode code, String errorMessage);
void onWebRtcAudioRecordError(String errorMessage);

// PeerConnection:
RtpSender addTrack(MediaStreamTrack track);
RtpSender addTrack(MediaStreamTrack track, List<String> streamIds);
DataChannel createDataChannel(String label, DataChannel.Init init);
void createOffer(SdpObserver observer, MediaConstraints constraints);
void setLocalDescription(SdpObserver observer, SessionDescription offer);
void setRemoteDescription(SdpObserver observer, SessionDescription answer);
SessionDescription getLocalDescription();
void setAudioRecording(boolean recording);
void setAudioPlayout(boolean playout);
void close(); void dispose();
// SdpObserver:
void onCreateSuccess(SessionDescription sdp); void onSetSuccess();
void onCreateFailure(String error); void onSetFailure(String error);
// DataChannel:
void registerObserver(DataChannel.Observer observer); void unregisterObserver();
DataChannel.State state(); long bufferedAmount();
boolean send(DataChannel.Buffer buffer); void close(); void dispose();
// DataChannel.Buffer:
public final ByteBuffer data; public final boolean binary;
public Buffer(ByteBuffer data, boolean binary);
// DataChannel.Observer:
void onBufferedAmountChange(long previousAmount); void onStateChange(); void onMessage(DataChannel.Buffer buffer);
```

Source references for common APIs: [PeerConnection](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/api/org/webrtc/PeerConnection.java), [DataChannel](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/api/org/webrtc/DataChannel.java), [upstream JavaAudioDeviceModule](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/api/org/webrtc/audio/JavaAudioDeviceModule.java). Fork extensions: [fork ADM](https://raw.githubusercontent.com/webrtc-sdk/webrtc/m150_release/sdk/android/api/org/webrtc/audio/JavaAudioDeviceModule.java), [fork audio record](https://raw.githubusercontent.com/webrtc-sdk/webrtc/m150_release/sdk/android/src/java/org/webrtc/audio/WebRtcAudioRecord.java). Moving sources explain behavior; exact pinned classfile signatures take precedence.

## 3. Shared capture and lifecycle

The exact AAR bytecode establishes client-owned capture independently of native sender demand: ADM.requestStartRecording calls audioInput.initRecordingIfNeeded and startRecordingIfNeeded. The latter sets clientCalledStartRecording=true. Native stopRecording clears nativeCalledStartRecording and nativeCalledInitRecording; stopRecordingIfNeededImpl returns without stopping if either clientCalledStartRecording or nativeCalledStartRecording remains true. requestStopRecording clears client ownership.

Therefore retain one ADM, factory, audio source and track during a dictation session; set client recording ownership on user start and recreate only peer/datachannel during connection loss. This is a source-backed implementation route, still a device gate. Release both native sender demand and client demand on user stop; requestStopRecording alone can leave recording active while native demand is true. Use real start/stop/error callbacks and observed samples to report capture state; void requestStartRecording does not report success.

SamplesReadyCallback is documented by upstream as a debug hook; the fork exposes it and creates a new copied byte array for each callback. Use it conservatively and pin/test the dependency. It runs on the urgent audio recording thread, typically in 10ms frames. Do not write files, call HTTP, or block there: enqueue bounded work. Actual sample format/rate/channels come from callback metadata. Default format is Android PCM16 and mono; default rate is device native rate, not guaranteed 24kHz. Explicitly requesting PCM16/mono is sensible; do not confuse the capture/WAV format with negotiated RTP codec (Opus commonly uses a 48kHz SDP clock). Fail visibly for an unexpected format or normalize correctly.

DataChannel onMessage ByteBuffer is freed after the callback; copy data before asynchronous dispatch. DataChannel.send consumes the supplied ByteBuffer position and returns whether send succeeded. Check OPEN and return status. Use an ordered reliable data channel (`oai-events`, ordinary Init defaults), but ordering applies to data events only.

PeerConnection.dispose cannot safely be called from PeerConnection.Observer/DataChannel.Observer; schedule teardown after callback unwinds on a serialized owner. Unregister/close/dispose the channel once, then dispose the peer. Final session cleanup disposes local audio track/source/factory and releases ADM exactly once. Reconnect must not dispose shared capture resources. Never assume peer recreation replays retained audio.

## 4. OpenAI handshake and events

Official sources fetched:

- [Live transcription guide](https://developers.openai.com/api/docs/guides/realtime-transcription).
- [Client secret reference](https://developers.openai.com/api/reference/resources/realtime/subresources/client_secrets/methods/create).
- [WebRTC guide, Realtime tab](https://developers.openai.com/api/docs/guides/voice-webrtc?voice-api=realtime).
- [Client events](https://developers.openai.com/api/reference/resources/realtime/client-events).
- [Server events markdown](https://developers.openai.com/api/reference/resources/realtime/server-events.md), fetched directly because HTML exceeded web retrieval size.
- [Calls reference](https://developers.openai.com/api/reference/resources/realtime/subresources/calls/methods/create).
- [General session lifecycle](https://developers.openai.com/api/docs/guides/realtime-conversations).

Product spec 6.2 matches a documented assembly: POST `/v1/realtime/client_secrets` using standard BYOK bearer; JSON `{"session":{"type":"transcription","audio":{"input":{"transcription":{"model":"gpt-live-transcribe"},"turn_detection":null}}}}`; response token is top-level `value`. Add track and create `oai-events` before offer; createOffer then await setLocalDescription success; POST raw SDP to `/v1/realtime/calls` with ephemeral bearer and Content-Type application/sdp; await setRemoteDescription answer success. Do not use a query model or old beta endpoint.

The dedicated calls multipart reference describes type realtime only, while client_secret reference admits transcription and live guide links WebRTC. Thus the transcription-scoped ephemeral raw-SDP assembly remains an explicit integration gate, not a tested fact. Account access is also unverified.

Live model emits `conversation.item.input_audio_transcription.delta` as speech arrives; final `conversation.item.input_audio_transcription.completed` follows explicit `input_audio_buffer.commit`. It does not support server_vad or semantic_vad. `languages:["en"]` is the live language setting, not singular `language`; keywords/prompt are supported. Omit delay initially: live guide supports it but client-secret reference still describes support only for gpt-realtime-whisper.

Event ledger:

- Delta: item_id, optional content_index, optional delta, server event_id. Accumulate provisional text by connection generation/item/content, deduplicate server event IDs.
- Completed: item_id, content_index, transcript, event_id, usage. Replace that item's provisional text. Completion order across turns is explicitly not guaranteed.
- Failed transcription: item_id, content_index, error. Preserve corresponding audio and mark failed.
- input_audio_buffer.committed: item_id, previous_item_id nullable, server event_id. This does not echo the client commit event ID. Maintain an ordered outstanding commit queue and provider predecessor ledger; do not sort opaque IDs or use completion arrival order.
- Generic error.error.event_id may refer to the failed client event. Server event_id is independent.

No JSON input_audio_buffer.append should duplicate audio already sent over the live media track. A local callback/VAD boundary does not prove the final corresponding RTP samples arrived before a datachannel commit: media and SCTP have separate transmission paths. Validate trailing-audio behavior and drain margin with real speech. Commit on actual nonempty speech turns; empty input commit is an API error. Wait for datachannel OPEN and session acknowledgement; tolerate events arriving before commit acknowledgement. Stopping native capture and waiting for final completion need separate lifecycle states and a bounded finalization timeout retaining unresolved audio.

Ephemeral expiration controls creation of new sessions, not the lifetime of an established one. Mint fresh credentials for a replacement connection. General Realtime guide says max60 minutes; transcription-specific limit not independently stated by the live model guide. Renew conservatively at a turn boundary and verify runtime behavior. Old-generation callbacks must never update current turn/editor state.

## 5. Accessibility signatures requested by app owner

API33+ [InputMethod](https://developer.android.com/reference/android/accessibilityservice/InputMethod): `final AccessibilityInputConnection getCurrentInputConnection()` (nullable), `final EditorInfo getCurrentInputEditorInfo()` (nullable), `onStartInput(EditorInfo, boolean)`, `onFinishInput()`, `onUpdateSelection(int,int,int,int,int,int)`.

[AccessibilityInputConnection](https://developer.android.com/reference/android/accessibilityservice/InputMethod.AccessibilityInputConnection): `void commitText(CharSequence, int, TextAttribute)` (nullable TextAttribute), `SurroundingText getSurroundingText(int beforeLength,int afterLength,int flags)` (nullable), `void setSelection(int start,int end)`. Coordinates count Java UTF16 characters. getSurroundingText can take seconds and fail; treat unavailable/unknown offset readback as inability to prove safe replacement. Mutation methods return void: verify readback, not an assumed boolean success. No composing/batch API is exposed here.

## 6. Outstanding runtime gate

Build and run pinned library on Fold6; prove cold bubble microphone start with app activity closed; confirm capture callback PCM rate/channel/format and ongoing samples while a peer is closed/recreated; prove actual user stop releases microphone. With user-authorized paid setup testing, prove transcription secret accepted, raw SDP call accepted, live delta before first commit, final matching item after commit, second turn and measured trailing-audio boundary. Force drop mid-turn and ensure startup/drop audio stays durable and recovery results precede newly captured speech without duplicate editor text. Prove finalization and stale callback behavior. This research made no paid call and cannot attest those outcomes.

## 7. Follow-up integration seams

For finalization without closing the data channel, native source confirms `PeerConnection.setAudioRecording(false)` -> PeerConnection::SetAudioRecording -> AudioState::SetRecording(false) -> native ADM.StopRecording. Follow with Java ADM.requestStopRecording to clear client ownership; then microphone can stop while peer/DC remains available for final commit/completion. Track.setEnabled(false) alone is not sufficient microphone-release evidence. Call on serialized owner; native method blocks on worker roundtrip. Observe actual stop callback; drain margin and resulting transcript remain a runtime gate. Sources: [native peer](https://raw.githubusercontent.com/webrtc-sdk/webrtc/m150_release/pc/peer_connection.cc), [audio state](https://raw.githubusercontent.com/webrtc-sdk/webrtc/m150_release/audio/audio_state.cc). Avoid using setAudioRecordEnabled(false) as the main final-stop contract; it switches to recordingless zero-buffer behavior rather than releasing both ownership requests.

Recovery upload confirmed by [official file transcription guide](https://developers.openai.com/api/docs/guides/speech-to-text): POST `/v1/audio/transcriptions` with multipart file and model=gpt-transcribe returns JSON `text` and `languages`. API access untested. Use WAV headers matching actual callback metadata or explicitly normalized audio.

GET_TEXT_WITH_STYLES should return Spanned with spans per Android contract, but arbitrary editor implementations may omit them. A composing span is sufficient to refuse initial edits; its absence is not universal proof of no composing state. Combine accessibility InputMethod selection candidatesStart/candidatesEnd and lifecycle state; preserve safe failure for unavailable readback or unknown offsets. Mutation calls remain void and require readback confirmation.
