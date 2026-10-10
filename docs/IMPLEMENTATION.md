# AlienFlow implementation checkpoint

Repository: IssisX/AlienFlow. Branch: build/first-apk. Date: 2026-10-10.

## 0.1.4 microphone crash and composition repair

### Observed phone evidence

- Accessibility is enabled; a short tap and long-press both reach AlienFlow.
- The OpenAI key/session probe succeeds.
- On 0.1.3 the Android microphone privacy indicator appears, then the process force-closes about one second later. That establishes microphone activation before death; it does not identify the exact native frame.
- In a separate empty ChatGPT input, AlienFlow reports `[AF-E08] Finish the keyboard's composing word before dictating`. The screenshot shows no user-entered composing word. This is a false refusal in that path.
- The owner did not find the prior diagnostic section. Do not treat an unseen diagnostic as evidence or require it as the primary fix path.

### Supported causes and repairs

1. **False composition detection.** The accessibility callback treated `candidatesStart == candidatesEnd == 0` as an active composing range because it used `end >= start`. A composing span must have positive length. Unknown composition was also treated as proof of composition for every nonempty representation. The update requires an explicit positive-length candidate range or composing span; every actual write remains corroborated against complete keyboard text and selection before mutation.
2. **Duplicate WebRTC microphone ownership at the crash boundary.** When the data channel opened, the previous code enabled peer audio *and* called `JavaAudioDeviceModule.requestStartRecording()`. The microphone indicator appears at this exact boundary. The update removes the independent client recording demand and lets the peer connection own one microphone start/stop lifecycle. It also disables hardware AEC/noise suppression because AlienFlow plays no remote audio and does not need echo cancellation for dictation. Java `AssertionError`, `LinkageError`, and runtime startup failures at this boundary are converted to visible failure states; native SIGSEGV remains outside Java catch semantics.
3. **Artwork.** The bright green, cartoon-like head is replaced in both the overlay and launcher with a restrained graphite/silver “Gray” alien: elongated head, dark almond eyes, subtle highlights, no cartoon dot-mouth, and a cyan active ring.

The WebRTC microphone fix is supported by the actual source seam and timing, and by known WebRTC Android audio-record assertion/native-failure reports. It is still a hypothesis until the Fold6 path is rerun. No fake runtime pass is claimed.

### Behavior changes

- Version 0.1.4 / versionCode 5, same package and signing identity.
- Existing API key, settings, history, opacity, and bubble positions remain in app data during an in-place update.
- Reconnect no longer uses the JavaAudioDeviceModule's second recording demand. During a reconnect grace period the existing peer-owned capture is retained; detaching/recreating the peer may still produce an audio gap, which keeps recovery marked uncertain.
- No `SYSTEM_ALERT_WINDOW` permission is added. The working bubble is an accessibility overlay; the observed failure occurs after its tap path and microphone activation.

### Verification gate

Source includes tests for zero-length versus positive-length composing ranges, absence of composition evidence, protected editor ownership, and the single-demand/no-hardware-effects capture policy. GitHub Actions must compile the exact revision, run all tests, pass release lint/package checks, and produce an unsigned bundle. The final APK must be signed with the original certificate and verified before delivery.

Phone acceptance remains mandatory: install over 0.1.3, focus an empty note, tap once, confirm the process remains alive and reaches Listening, speak a short phrase, observe text, then stop and confirm microphone release. If it still dies, collect the last app checkpoint or Android exit reason without inventing a cause.

### Primary evidence consulted

- Android accessibility input connection exposes a restricted editor API; composition cannot be finished through that interface, so AlienFlow must distinguish actual composition from absence of evidence.
- OpenAI Realtime transcription with `gpt-live-transcribe` uses manual input-buffer commits when turn detection is null.
- WebRTC Android reports include assertion/native failures at microphone enable boundaries; these support testing single ownership but do not prove this phone's precise crash frame.
