# AlienFlow implementation checkpoint

Repository: IssisX/AlienFlow. Branch: build/first-apk. Date: 2026-10-10.

## Current repair: 0.1.2 editor compatibility

Owner reports: accessibility permission is now enabled; a tap displays `This text box does not expose reliable editor information`; long press opens the menu. The key/session test is accepted. Live microphone capture and insertion have not been demonstrated. This narrows the failure to the editor-readiness path, not a dead touch handler or rejected API key.

Baseline: commit `6726053eb083feb7f3d1f7de2984b53f7f9a8469`, tree `1b6b37c97033d74b82ae47bf22f46d8c8886fa4f`. The complete prior checkpoint is preserved byte-for-byte in [history/IMPLEMENTATION_THROUGH_0.1.1.md](history/IMPLEMENTATION_THROUGH_0.1.1.md). It is historical evidence, not the current release status.

**Current delivery:** 0.1.2 is built, tested and signed. Compiled source is `c98d2a9d29adf6c1f83eafdf32b60ea2f002e6ee`; later documentation-only commits do not change the compiled application. Phone acceptance remains open.

### Supported defects and repair

- The 0.1.1 bridge compared raw accessibility `node.text` with keyboard contents. Android may expose an empty editor's hint through `node.text` and set `isShowingHintText=true`. The keyboard correctly returns an empty string. This valid editor was rejected. `EditorEvidence` now distinguishes hints from user text. Actual user text that happens to equal the hint remains intact.
- An empty editor has only cursor position zero. Missing accessibility selection (-1) is normalized only when the actual node representation is empty; nonempty unknown selections remain rejected. Keyboard contents and selection must corroborate it before any write.
- `SurroundingText.offset=-1` is documented as unknown, not invalid. It is accepted only with exact full-node content and matching cursor/selection, never with partial substring matching. Nonzero offsets and inconsistent data are rejected explicitly.
- Keyboard reads previously ran synchronously on the main thread, including while reading AlienFlow's own main-thread editor. Begin/polling now use normalized node metadata; every consequential edit is independently checked against keyboard contents on IO before dispatch, followed by asynchronous readback. Main is free to respond and render the bubble during keyboard reads. Failed/changed reads never cause blind retries.
- Session ownership, generation, write sequence and active/finishing state are checked after suspension. Cancelled work or a replaced editor cannot authorize a delayed commit. Explicit cancellation restore is scoped to the same owner/generation. No automatic submit action is introduced.
- Previously anonymous refusal paths now have distinct AF-E codes without logging private editor text. Capture transport, model identifiers, correction policy, saved key, history, package ID and 75% default opacity are unchanged.

The exact failing metadata on the owner's phone has not been captured. The hint mismatch is a concrete code defect, reproduced in Android-framework widget tests, that produces the reported generic message. This is not a claim of having inspected the phone's private field state or ruled out every other editor issue.

### Permission ownership

The existing bubble uses `TYPE_ACCESSIBILITY_OVERLAY`, authorized by the enabled accessibility service. It is not `TYPE_APPLICATION_OVERLAY`, which needs SYSTEM_ALERT_WINDOW / Appear on top. No unrelated permission is added to fix text comparison. Microphone permission and foreground-service runtime acceptance remain separate checks. A successful key/session request does not test either editor input or captured audio.

### Verification

- Local Kotlin compilation and a JVM harness exercised the actual new policy with the unchanged production ownership excerpt: hint normalization, empty cursor, full unknown-offset evidence, mismatch/partial-offset refusal, selected replacement and manual-edit rejection passed.
- [Actions run 38065273911](https://github.com/IssisX/AlienFlow/actions/runs/38065273911) completed successfully for `c98d2a9d29adf6c1f83eafdf32b60ea2f002e6ee`. All **54 tests** executed with zero failures, errors or skips: 15 existing logic, 22 existing audio, 11 new editor-policy cases, and six widget executions (three cases at each of APIs 33 and 35).
- The actual widget results reproduce the old raw hint-versus-empty-content mismatch, then pass insertion and owned-span confirmation. They also pass selected-span replacement/revision while retaining anchors, and preserve typed text equal to a placeholder. These are Robolectric 4.16.1 framework-widget/InputConnection tests, not tests of the physical Android accessibility broker, Gboard IPC, microphone or provider.
- Release lint passed with zero errors and 20 warnings. Reports retain version-update suggestions, English UI warnings, absolute overlay coordinates, ARM64 policy, one draw allocation and launcher-resource polish. No baseline, suppression or weakened test requirement was added.
- Release assembly, package identity and 16 KiB native/ZIP alignment checks passed. The artifact and all 27 manifest-listed file hashes were verified after download.
- Signed `AlienFlow-0.1.2.apk`: 17,045,689 bytes; SHA-256 `0bb8c3b016cba5d373b4f3c67300330d40f8c0dc076a8500a53b1876d5848148`. APK v3 signature verification passed with the existing certificate `d79e74b3a4c24ee225d1220c2c374c6f958520d4f3257fcfb399ff09bf5c965f`. All unsigned APK payload entries were compared with the signed APK and remained identical.
- Exact machine-readable provenance is in [RELEASE_VERIFICATION_0.1.2.json](RELEASE_VERIFICATION_0.1.2.json); actual signing-tool output is in [APK_SIGNATURE_0.1.2.txt](APK_SIGNATURE_0.1.2.txt). No runtime key or paid provider request was used for these checks.

Version: 0.1.2, versionCode 3. Same signing key and package `io.github.issisx.alienflow`; install as an update. Do not uninstall or clear data to repair this issue. Actual update/data preservation on the phone remains unobserved.

### Next device acceptance

Install the signed 0.1.2 APK over 0.1.1. Keep the existing key and settings. Focus AlienFlow's empty test field (or a new note with Gboard selected), tap Start/alien, and observe Listening, real live text and microphone release after Stop. A remaining AF-E code identifies the editor operation to investigate; a microphone denial is a separate capture gate. Do not declare universal editor compatibility or full live dictation verified from CI.

Outstanding broader work remains in PRODUCT_SPEC.md and the historical checkpoint: seamless inline reconnection, some history/appearance options, resource limits and real-phone compatibility/latency tests. This repair does not waive them.

### Primary references

- Android AccessibilityNodeInfo.isShowingHintText: https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#isShowingHintText()
- Android SurroundingText offset and selection semantics: https://developer.android.com/reference/android/view/inputmethod/SurroundingText
- AccessibilityInputConnection IPC/read contract: https://developer.android.com/reference/android/accessibilityservice/InputMethod.AccessibilityInputConnection
- Overlay types and permission distinction: https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY
- Robolectric published releases: https://github.com/robolectric/robolectric/releases

## 0.1.3 crash diagnostic · 2026-10-10

- Owner observed a full Android process exit with the system "app has a bug" popup when tapping the alien on 0.1.2. No crash stack, native tombstone, or on-device microphone startup trace is yet available; provider key/session validation is distinct from microphone operation.
- The now-passing editor-readiness logic allows entry into foreground-service and WebRTC initialization. Competing causes include a foreground-service runtime failure, native-library initialization failure, or an exception in the presentation path. None is promoted to the cause without runtime evidence.
- Added a **private, persistent crash journal** with fixed named startup checkpoints from tap through the editor, foreground service, realtime initialization, and microphone frames. Java/Kotlin uncaught failures record exception class and code locations only, with no exception messages, API keys, field text, audio, or requests. The existing Android crash mechanism still runs. Native crashes cannot be caught by this Java handler, but the last committed checkpoint remains available.
- MainActivity displays the latest private crash report/checkpoint with explicit Refresh, Copy and Clear actions. No automatic transmission or global log collection. The user decides whether to share the sanitized diagnostic.
- Source includes JVM tests verifying that private exception messages do not enter the report. No changes were made to the actual capture, transcription, correction, editor-write or authentication semantics. This update is diagnostic, **not a claim that the process crash is fixed**.
- Version 0.1.3, versionCode 4, package and signing identity unchanged. Build/test/lint and phone observations remain gates; after collecting the on-device diagnostic, repair the precise exception and rerun the affected path.

### 0.1.3 verified build and signed delivery

- [Actions run 38066975001](https://github.com/IssisX/AlienFlow/actions/runs/38066975001) completed on compiled commit `972fbab4e28c8addd8f02ac55062d470a8e9964c`. All 56 tests executed with zero failed, errored or skipped; release lint zero errors and 21 issues/warnings; release assembly/package checks and artifact upload passed.
- GitHub unsigned bundle artifact `11675465402` SHA-256 `1f5604a1112f3df83f1c2d5132747cf86ffdcbfc6ccd6c0aeeb04c94cc23af73`; every one of 29 listed member hashes verified after download.
- Signed `AlienFlow-0.1.3.apk`, versionCode 4: SHA-256 `e29174db72a5e195b8d36a008bab874e1a3e9fb17ce6d2d9372b8aadc1b0e7d9`, 17,045,689 bytes. APK v3 verification passed with the original signing certificate `d79e74b3a4c24ee225d1220c2c374c6f958520d4f3257fcfb399ff09bf5c965f`; all unsigned APK payload entries stayed identical after signing.
- Real phone crash still **unresolved**. Next gate: install the update, reproduce once, reopen AlienFlow, tap **Copy crash diagnostic**, and inspect the exact failure class/location and checkpoint before changing startup code. Java diagnostic may be absent for a native SIGSEGV or system-level force-stop; preserve that distinction.
