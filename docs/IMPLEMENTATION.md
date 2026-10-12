# AlienFlow2 implementation checkpoint — 0.1.12

Repository: IssisX/AlienFlow. Branch: AlienFlow2. Baseline: 0130c70afa9b5322df01afa8e0198e29a31c46fd. Date: 2026-10-12. VersionCode: 13.

## Owner observation and diagnosis

The owner reports improvement in 0.1.11 but says the bubble stops appearing in other text fields after using Termux. No new phone diagnostic or physical reproduction was supplied for this observation.

Rivals: a surviving terminal-mode flag; service death/explicit Hide/off-screen positioning; availability stranded during independent IME/accessibility focus delivery. Inspection shows input finish/start already invalidates the terminal flag. Explicit Hide is only set by its menu action. There is no established phone evidence of service death or positioning failure in this report. The visibility path does have a concrete gap: input start/finish takes one immediate focus sample, selection callbacks do not refresh bubble visibility, and the manifest's service XML omits window-content-change events. If the new node is not available during that sample and the later update arrives through either missing route, GONE can persist. This is a source-supported counterexample, not proof of the Fold6's precise event order.

## Implemented repair

The accessibility service remains the sole overlay owner. Input start/finish first invalidates the old editor binding, clears that service's accessibility cache using the Android API available since 33, and schedules a coalesced focus refresh. A bounded burst re-queries at 0, 50, 200 and 600ms while no eligible node is available and stops early when one is found; every result uses EditorBridge.focused(), never the old field or a timer-derived availability flag. Selection callbacks also refresh availability. The declared service now subscribes to window-content changes, so later browser/editor tree publication can refresh the bubble after the burst. Destroy removes pending refresh callbacks. No permanent idle polling, Always-visible workaround, inactive-window fallback or mid-dictation target adoption was introduced.

Explicit Hide, password exclusion, keyboard choice, nonfocusable overlay flags, editor ownership and never-submit behavior remain. The accepted icon, readiness states, 20ms capture frames, startup FIFO and minimal transcription delay are unchanged. Bubble Diagnostics adds only booleans for eligible input, visibility, explicit Hide, input-session state and terminal binding. It stores no field text or app package identifiers.

Primary API contracts inspected: [AccessibilityService focus/cache](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService), [InputMethod lifecycle](https://developer.android.com/reference/android/accessibilityservice/InputMethod), and [Android 16 InputMethod source](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/core/java/android/accessibilityservice/InputMethod.java). IME lifecycle and accessibility tree delivery are separate; the old node is not an authority for the next editor.

## Verification contract

The new regression suite runs the production service callbacks, attached overlay, actual visibility setter and packaged service XML under Robolectric SDK 35. The remote focus/cache broker and EditorInfo delivery are explicitly simulated. It covers Termux-to-delayed-document focus, selection-only recovery, subscribed content-change recovery, password/no-node exclusion, explicit Hide preservation, nonfocusable flags, one overlay identity, bounded refresh work and teardown. Existing editor, terminal, synchronization, capture, protocol, storage and native-render gates remain required. These checks do not prove installed Termux or physical Fold6 acceptance.

Exact compilation, test, lint, package, artifact and signature outcomes will be recorded in RELEASE_VERIFICATION_0.1.12.json after the release gates run. Prior checkpoint: history/IMPLEMENTATION_0.1.11.md.

## Phone acceptance remaining

Install over 0.1.11. Finish dictation in Termux, switch to AlienFlow's test field and Chrome, tap each field and confirm the same bubble returns without restarting accessibility. Start a new dictation in each. Repeat switching in both directions. A fresh session may edit its current field; old results must never edit the new field. Keep ordinary password/explicit Hide behavior. If it still disappears, copy Diagnostics: its availability booleans and Android service/exit evidence distinguish absent eligible focus, intentional Hide and disconnected service. Actual phone event order remains unverified here.
