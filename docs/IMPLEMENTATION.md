# AlienFlow implementation checkpoint — 0.1.8

Repository: IssisX/AlienFlow. Target branch: build/first-apk. Baseline: 768f46e5b63637dd74bf2edaaad746f6607aef8b. Date: 2026-10-12.

The complete prior 0.1.7 checkpoint is preserved at [history/IMPLEMENTATION_0.1.7.md](history/IMPLEMENTATION_0.1.7.md). Product requirements and runtime evidence are separate.

## Observed failure

After updating to 0.1.7 the owner supplied two screenshots. Saved-recording recovery had successfully recognized a deliberately dictated short phrase. Another screenshot showed a transient notice ending in “No microphone audio captured”; the beginning of that notice is cropped. This proves saved WAV recovery works but does not establish the initiating live failure code.

## Concrete code defect and repair

The 0.1.6 WebSocket replacement used `wss://api.openai.com/v1/realtime?model=gpt-live-transcribe`, erroneously treating a transcription-only model as a conversational Realtime model in the URL. The official OpenAI Node SDK uses `intent=transcription` for transcription-only connections and specifies the transcription model inside the session configuration. See [current primary-source research](RESEARCH_2026-10-12_LIVE_HANDSHAKE.md). This is a concrete routing bug, but the cropped screenshot does not prove it was the only reason for the owner's stop.

0.1.8 introduces RealtimeHandshake so both normal dictation and the explicit key/model check use the same transcription-intent WebSocket and `session.update`. The explicit check waits for an acknowledged transcription session and selected model and then closes before audio capture. A mere key acceptance, HTTP upgrade or session-created notification no longer counts as passing. The one Android AudioRecord input path is unchanged.

Early HTTP upgrade failures are recorded even when they arrive before onOpen or newWebSocket returns. Connection generation ownership prevents a failed socket from reviving. Only bounded typed failure metadata (HTTP status, code, type, param, request ID) enters the private diagnostic; never arbitrary server error messages, keys, dictated text or audio.

Overlay failure notices measure their actual height and stay within the usable screen bounds above the keyboard; their complete metadata remains copyable in Diagnostics. The approved Gray art, 75% default opacity, encrypted key, recovery WAV/history formats, guarded editor, package identity and signing certificate are preserved.

## Verification boundary

At the initial source checkpoint, regression tests exercise actual OkHttp WebSocket upgrades/frames against local MockWebServer, the production transport's synchronous failure race, and error notice placement. The real provider and hardware are not mocked into a claimed acceptance pass. Complete CI compilation/tests, release lint and artifact verification are required before signing. Record outcomes against the exact compiled SHA.

## Fold6 acceptance still pending

Install 0.1.8 (versionCode 9) over 0.1.7 without clearing data. First run Check key & transcription model: it must say live connection accepted after acknowledged configuration, not just token issuance. Focus a text field with Gboard, tap the alien, speak and observe live words, pause for a completed turn, then stop and confirm microphone release. If it fails, copy Diagnostics immediately and capture the fresh AF-N01 code/param; old WebRTC history may still appear. Passing GitHub tests is NOT physical-device proof.
