# AlienFlow2 implementation checkpoint — 0.1.22

Repository: IssisX/AlienFlow. Branch: AlienFlow2. Date: 2026-10-13. VersionCode: 23.

## Runtime cause, before repair

The owner repeatedly observed a visible alien with AF-E10 in Chrome and ChatGPT. The screenshot was directly visible in the conversation; its supplied scratch path was missing. No Library screenshot read was substituted.

A disposable API 36 emulator reproduced the exact rejection using a real WebView rich editor with a CSS placeholder, the existing Gboard keyboard, an actual floating-alien touch, and real accessibility/IME IPC. At source 5e8fd1532ba7f9575585b150c4dc59bcb91f7e42, accessibility exposed `Ask ChatGPT\n`, cursor 1:1, no hint metadata; the keyboard exposed only `\n`, cursor 0:0, offset -1 and absolute selection 0:0. Binding succeeded, but the old cross-representation equality/cursor check rejected the first keyboard write with AF-E10. Baseline run 38417316318 captures delivered=false; its successful trace collection is not a successful delivery test.

This isolates the producing transition: accessibility presentation text was incorrectly treated as the keyboard's editing buffer. The two APIs need not expose identical rich-editor representations. Earlier 0.1.19–0.1.21 shared-node-text guards and simulated native-widget tests did not establish browser acceptance.

## Repair and preserved behavior

EditorBridge remains the sole writer. KeyboardEditorOwnership tracks the observed keyboard window and absolute mutation range. Accessibility identifies the focused, non-protected node; keyboard text and coordinates authorize keyboard mutations. Known offsets are self-contained. Android's default offset=-1 requires an immutable absolute IME selection that survives the read, equal selection widths and a nonnegative derived offset.

Every shared keyboard character and the entire old mutation range are checked in a fresh read before setSelection/commitText. Confirmation uses that same API, including full coverage of the changed inserted range. A short window can permit appending a tail without permitting an unseen earlier correction or cancel. No substring search, placeholder blacklist, trimming, clipboard fallback, longer timeout or automatic resend is added. Rich-editor normalization outside the owned mutation is left to the editor rather than reconstructed through SET_TEXT.

Keyboard typing retires dispatched speech from correction ownership and adopts a fresh insertion anchor for its future tail; it does not stop capture. Selected replacement, unrelated drafts, Unicode and guarded cancellation remain covered. Positively empty fields without a keyboard connection retain their guarded node-action fallback. Terminal input keeps its existing final-only one-shot adapter without Enter. Keyboard selection and the nonfocusable overlay remain unchanged.

## Verification and delivery

Compiled source dc87d202161bffe31cd13edb6a1cb3c8ded79bb6, tree c9f45a62f78ee36b5c97117aa4658bea14924ef7, passed [run 38422683700](https://github.com/IssisX/AlienFlow/actions/runs/38422683700): 208 unit tests with zero failures/errors/skips, release lint zero errors (39 non-error issues), APK assembly, and two real API 36 instrumentation tests.

The browser test first requires actual empty-editor delivery, then verifies live final revision, selected replacement in an existing draft, actual keyboard typing while dictation remains active, future speech after typing, preserved draft anchors and repeated Unicode delivery. The installed official Termux 0.118.3 test observes actual terminal echo, final insertion exactly once after Stop, no premature partials or Enter, a repeated session, live delivery after switching to the native practice field, and return to Termux. Transcription/capture readiness are explicit fixtures; no microphone or paid API call is made.

Both classes now run in one instrumentation process. Separate invocations force-stopped the target app and caused Android to mark the accessibility service crashed before the second test could bind. Results are exported directly through instrumentation; optional screenshot transfer is bounded. New builds cancel superseded builds only within the AlienFlow2 branch's concurrency group. Production source was unchanged by these harness corrections.

All 144 bundle member hashes, source identity, API 36, artwork identity and exclusion of debug/test classes from the release APK were checked. Original APK v3 signing certificate matched; all 25 APK payload entries remain unchanged after signing. Signed APK SHA-256: 9059136fe1ea4b02992dbcf76e682583f6410f1e0106c58c37b612a81f9c31b9. The APK was saved and its hash verified after saving. Full evidence and preliminary-run classifications: [RELEASE_VERIFICATION_0.1.22.json](RELEASE_VERIFICATION_0.1.22.json).

## Scope and limits

API 36 remains targeted. AAB/bundletool work remains explicitly deferred; none was built, downloaded or delivered. Recognition models, prompts and API cost settings are unchanged. Local editor context is never sent to the provider by this repair.

The API 36 emulator establishes real Chromium and stock Termux input-path behavior. It does not establish exact installed Chrome/ChatGPT/Fold6 behavior, custom terminal/tmux setups, microphone/provider performance or compatibility with every input box. No other branch was written.

## Primary references

- https://developer.android.com/reference/android/accessibilityservice/InputMethod.AccessibilityInputConnection
- https://developer.android.com/reference/android/accessibilityservice/InputMethod
- https://chromium.googlesource.com/chromium/src/+/refs/heads/main/content/public/android/java/src/org/chromium/content/browser/input/ThreadedInputConnection.java
- https://chromium.googlesource.com/chromium/src/+/refs/heads/main/content/public/android/java/src/org/chromium/content/browser/input/TextInputState.java

Standing authority: AGENTS.md. Previous checkpoint: [history/IMPLEMENTATION_0.1.21.md](history/IMPLEMENTATION_0.1.21.md).
