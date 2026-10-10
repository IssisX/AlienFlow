# AlienFlow implementation checkpoint

Repository: IssisX/AlienFlow. Branch: build/first-apk. Date: 2026-10-10.

## Current repair: 0.1.2 editor compatibility

Owner reports: accessibility permission is now enabled; a tap displays `This text box does not expose reliable editor information`; long press opens the menu. The key/session test is accepted. Live microphone capture and insertion have not been demonstrated. This narrows the failure to the editor-readiness path, not a dead touch handler or rejected API key.

Baseline: commit `6726053eb083feb7f3d1f7de2984b53f7f9a8469`, tree `1b6b37c97033d74b82ae47bf22f46d8c8886fa4f`. The complete prior checkpoint is preserved byte-for-byte in [history/IMPLEMENTATION_THROUGH_0.1.1.md](history/IMPLEMENTATION_THROUGH_0.1.1.md). It is historical evidence, not the current release status.

### Supported defects and repair

- The 0.1.1 bridge compared raw accessibility `node.text` with keyboard contents. Android may expose an empty editor's hint through `node.text` and set `isShowingHintText=true`. The keyboard correctly returns an empty string. This valid editor was rejected. `EditorEvidence` now distinguishes hints from user text. Actual user text that happens to equal the hint remains intact.
- An empty editor has only cursor position zero. Missing accessibility selection (-1) is normalized only when the actual node representation is empty; nonempty unknown selections remain rejected. Keyboard contents and selection must corroborate it before any write.
- `SurroundingText.offset=-1` is documented as unknown, not invalid. It is accepted only with exact full-node content and matching cursor/selection, never with partial substring matching. Nonzero offsets and inconsistent data are rejected explicitly.
- Keyboard reads previously ran synchronously on the main thread, including while reading AlienFlow's own main-thread editor. Begin/polling now use normalized node metadata; every consequential edit is independently checked against keyboard contents on IO before dispatch, followed by asynchronous readback. Main is free to respond and render the bubble. Failed/changed reads never cause blind retries.
- Session ownership, generation, write sequence and active/finishing state are checked after suspension. Cancelled work or a replaced editor cannot authorize a delayed commit. Explicit cancellation restore is scoped to the same owner/generation. No automatic submit action is introduced.
- Previously anonymous refusal paths now have distinct AF-E codes without logging private editor text. Capture transport, model identifiers, correction policy, saved key, history, package ID and 75% default opacity are unchanged.

The exact failing metadata on the owner's phone has not been captured. The hint mismatch is a concrete supported code defect that produces the reported generic message, not a claim of having inspected the phone's private field state.

### Permission ownership

The existing bubble uses `TYPE_ACCESSIBILITY_OVERLAY`, authorized by the enabled accessibility service. It is not `TYPE_APPLICATION_OVERLAY`, which needs SYSTEM_ALERT_WINDOW / Appear on top. No unrelated permission is added to fix text comparison. Microphone permission and foreground-service runtime acceptance remain separate checks. A successful key/session request does not test either editor input or captured audio.

### Verification at source checkpoint

- Local Kotlin compilation and a JVM harness exercised the actual new policy with the unchanged production ownership excerpt: hint normalization, empty cursor, full unknown-offset evidence, mismatch/partial-offset refusal, selected replacement and manual-edit rejection passed.
- Added 11 policy tests and Android-framework widget tests for empty hints, initial insertion, selected-span replacement/live revision, and actual text equal to a placeholder. Widget tests use Robolectric 4.16.1 at APIs 33 and 35, without provider requests. These exercise framework widget/InputConnection behavior, not the phone's accessibility broker or microphone.
- CI compilation, full tests, lint, APK packaging and private signing for 0.1.2 are pending at this source checkpoint. A later evidence record must tie outcomes to the compiled SHA. No existing test or lint requirement is bypassed.

Version: 0.1.2, versionCode 3. Use the existing signing key and package `io.github.issisx.alienflow`; install as an update, never instruct uninstall as a repair.

### Next device acceptance

Install the verified signed 0.1.2 APK over 0.1.1. Keep the existing key and settings. Focus AlienFlow's empty test field (or a new note with Gboard selected), tap Start/alien, and observe Listening, real live text and microphone release after Stop. A remaining AF-E code identifies the editor operation to investigate; a microphone denial is a separate capture gate. Do not declare universal editor compatibility or full live dictation verified from CI.

Outstanding broader work remains in PRODUCT_SPEC.md and the historical checkpoint: seamless inline reconnection, some history/appearance options, resource limits and real-phone compatibility/latency tests. This repair does not waive them.

### Primary references

- Android AccessibilityNodeInfo.isShowingHintText: https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#isShowingHintText()
- Android SurroundingText offset and selection semantics: https://developer.android.com/reference/android/view/inputmethod/SurroundingText
- AccessibilityInputConnection IPC/read contract: https://developer.android.com/reference/android/accessibilityservice/InputMethod.AccessibilityInputConnection
- Overlay types and permission distinction: https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY
- Robolectric published releases: https://github.com/robolectric/robolectric/releases
