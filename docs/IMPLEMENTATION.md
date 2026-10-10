# AlienFlow implementation checkpoint

Repository: `IssisX/AlienFlow`  
Branch: `build/first-apk`  
Date: 2026-10-10

## Current delivery: 0.1.4

Compiled source: `b39de2b1b73e89c29bb918dd5710a207084240e6`  
Git tree: `5f7ee32ba2433e2ebaae585090ac40db0f3c47cc`  
Workflow: [run 38069975789](https://github.com/IssisX/AlienFlow/actions/runs/38069975789)  
Package: `io.github.issisx.alienflow`  
Version: `0.1.4`, versionCode `5`

The later documentation commit does not alter the compiled application.

## Phone evidence that drove this change

- Accessibility is enabled and both tap and long-press reach the bubble.
- The OpenAI client-secret/session probe succeeds.
- On 0.1.3, the Android microphone privacy indicator appears and the process force-closes roughly one second later. This proves microphone activation before death but does not identify the exact native instruction.
- A blank ChatGPT input showed `[AF-E08] Finish the keyboard's composing word before dictating` despite no entered composing word.
- The owner did not see the previously described diagnostic UI. It is not used as evidence for this repair.

## Supported repairs

### 1. One owner for microphone capture

The prior data-channel OPEN path invoked both `PeerConnection.setAudioRecording(true)` and `JavaAudioDeviceModule.requestStartRecording()`. That established two recording demands at the observed microphone-start boundary. Version 0.1.4 removes the independent JavaAudioDeviceModule demand and lets the peer own one start/stop lifecycle.

Hardware acoustic echo cancellation and noise suppression are disabled. AlienFlow is transcription-only and plays no remote audio; these effects added device-specific audio initialization without buying the product objective. Startup assertions/linkage/runtime exceptions that remain in Java are converted into visible failure states. A native signal remains outside Java catch semantics.

This is a supported cause at the exact seam and timing, not yet a device-proven fix.

### 2. Explicit composition evidence

The old accessibility callback used `candidatesEnd >= candidatesStart`, so a zero-length `0..0` range was incorrectly marked as a composing word. It also treated unknown composition as active for nonempty node text. Version 0.1.4 requires a positive-length candidate range or a real composing span.

Safety checks remain consequential: before each write, complete keyboard text and selection must corroborate the accessibility node. Partial offsets, changed text, changed selection, protected fields, and stale ownership still reject the write. No blind replacement or automatic submit was added.

### 3. Gray alien artwork

The bright green cartoon bubble and launcher artwork are replaced with an original graphite/silver Gray alien: elongated head, dark almond eyes, restrained highlights, small facial detail, and a cyan active ring.

## Verification

GitHub Actions executed the actual changed revision:

- 58 tests executed; zero failures, errors, or skips.
- Release lint: zero errors, 22 warnings.
- Debug/release Kotlin compilation passed.
- Release APK assembly passed.
- Package ID, version, minimum/target SDK, non-debuggable state, ARM64 ABI, and 16 KiB alignment checks passed.
- Unsigned bundle artifact `11676427952`, SHA-256 `c68cb044ac4bd3641db173404bd771b77d8a51f56c51ab3dfb3c289bf4feade3`.
- All 31 build-manifest file hashes were independently verified after download.
- Signed APK `AlienFlow-0.1.4.apk`: 17,045,689 bytes, SHA-256 `4a5edca4562d4b72d7a3b069951191d0fd271047342d012449d76ac4264f8abf`.
- APK Signature Scheme v3 verified with permanent certificate SHA-256 `d79e74b3a4c24ee225d1220c2c374c6f958520d4f3257fcfb399ff09bf5c965f`.
- All 25 unsigned APK payload entries remained byte-identical after signing.

Exact evidence is in `RELEASE_VERIFICATION_0.1.4.json` and `APK_SIGNATURE_0.1.4.txt`.

## Remaining acceptance gate

Install the signed update over 0.1.3 without clearing data. On the Fold6:

1. Focus an empty note and tap once.
2. Confirm the process remains alive and reaches Listening.
3. Speak a short phrase and observe live text.
4. Stop and confirm microphone release.
5. Repeat in the empty ChatGPT field and confirm AF-E08 is absent unless an actual composing span exists.

A passing build does not establish those runtime outcomes. If the app still crashes, classify it as unresolved and gather the Android exit reason or native tombstone before another microphone architecture change.

## Preserved contracts

- Existing keyboard stays selected.
- One coordinator owns dictation state; one editor bridge owns edits.
- No app word allowance.
- No `SYSTEM_ALERT_WINDOW`; the bubble is an accessibility overlay.
- Phone API key remains encrypted at rest and absent from repo/CI/logs.
- Existing data and installation identity are preserved through the in-place update.
- Requirements and broader unfinished work remain in `PRODUCT_SPEC.md`.
