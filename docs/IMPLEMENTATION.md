# AlienFlow2 implementation checkpoint — 0.1.21

Repository: IssisX/AlienFlow. Branch: AlienFlow2. Date: 2026-10-13. VersionCode: 22.

## Observed failure and research

The latest owner screenshot shows ChatGPT's selected composer, visible alien and [AF-E10] Editor sync timed out. This is an insertion/synchronization rejection, not evidence of a missing bubble in that screenshot. It does not include surrounding-text offset or failure metadata, so its exact producing case remains unknown. The screenshot is directly visible in the conversation; its supplied scratch attachment path did not exist and was not substituted with a Library screenshot read.

Primary Android 16 InputConnection source establishes a supported case the app rejected: the default getSurroundingText concatenates bounded before/selected/after text and returns offset=-1. AndroidX RecordingInputConnection does not override that method. AlienFlow requested bounded text but accepted unknown-offset windows only when they equaled the full accessibility field. A long draft using this default method necessarily fails the old guard, independently of increasing the timeout. This is a source-confirmed compatibility defect; without a fresh runtime report it is not established as the exact screenshot cause.

Also read the current independent AlienFlow(chat) checkpoint and its Library verification JSON. That branch exercised official Termux 0.118.3 on an API 36 emulator through actual overlay touches and accessibility IPC, with recognition fixtures. Its production coordinator/editor matched 0.1.19; its only terminal delta rejects control-only converted text before consuming delivery. This is bounded integration evidence, not Fold6/Gboard/tmux/provider acceptance. No writes were made to AlienFlow(chat), main or build/first-apk, and no generic Termux rewrite is warranted by that evidence.

## Repair

The service retains absolute selection supplied by onStartInput EditorInfo.initialSelStart/End and onUpdateSelection, clearing it when input ends. A read receives that immutable selection only if the identical observation survives across the keyboard IPC. Unknown-offset partial windows can then anchor at absolute selection minus local selection. Both absolute endpoints must match the focused node, selection widths must agree, every shared character must match and the complete mutation must be covered. No substring search, normalization, timeout increase or resending is added. Explicit-offset and full-text paths retain their existing guards. Missing or contradictory callbacks cannot authorize a partial-window edit. Diagnostics include numeric absolute selection metadata.

The changed-path test invokes Android's actual default InputConnection method through a Java proxy delegating native EditText operations, not a fabricated offset window. It verifies offset=-1 and a bounded result in a long draft, selected replacement, Unicode corrections, preservation of prefix/suffix, typing between speech updates and a contradictory callback dispatching no selection/commit. Accessibility broker/IPC and selection callback delivery remain explicit fixtures. No physical-device claim is inferred.

## Build scope correction

The owner explicitly deferred AAB work until final store preparation. Routine CI no longer downloads bundletool, builds bundleRelease, validates or packages AABs. API 36 remains targeted. Existing delivered .20 AAB and historical records are retained, not treated as a current deliverable. Direct signed APK delivery uses the existing certificate. Recognition requests, prompts and model choices are unchanged.

## Verification

Pending exact-source unit tests, release lint, APK assembly, identity/archive checks, original-certificate signing and saved-file verification. Actual ChatGPT/Fold6 and Termux/tmux/provider acceptance remain unverified for this revision. The screenshot alone cannot identify all causes sharing AF-E10.

## Primary references

- https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/core/java/android/view/inputmethod/InputConnection.java
- https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/text/input/RecordingInputConnection.android.kt
- https://developer.android.com/reference/android/accessibilityservice/InputMethod
- https://developer.android.com/reference/android/view/inputmethod/SurroundingText
- Independent branch verification: RELEASE_VERIFICATION_0.1.20-chat.1.json at AlienFlow(chat) 2ada0793ea4d0888a39c12eb3acc406e588a0be4, compiled source 683ab2dcc5902d41ed5b283f08d6a8054def1b2d.

Standing authority: AGENTS.md. Historical delivery: RELEASE_VERIFICATION_0.1.20.json.
