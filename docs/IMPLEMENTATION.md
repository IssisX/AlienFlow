# AlienFlow2 implementation checkpoint — 0.1.20

Repository: IssisX/AlienFlow. Branch: AlienFlow2. Date: 2026-10-13. VersionCode: 21.

## Observation and unresolved failure

The owner reports 0.1.19 working in tested ordinary apps but still failing in Termux. A follow-up answer says the bubble is absent in ChatGPT while working in other apps. These are bounded observations, not universal compatibility. No fresh failure diagnostic or installed Termux version was supplied. The earlier 0.1.18 25/27-character report cannot establish the current Termux or missing-bubble cause.

Inspected current upstream Termux TerminalView and Android 16 BaseInputConnection/RemoteInputConnectionImpl. The upstream terminal exposes a generic View, uses TYPE_NULL or class-zero visible-password/no-suggestions flags, and consumes/clears committed text; its contract matches the existing terminal adapter. Existing source already gates terminal commits until finalization. Plausible remaining boundaries include unavailable input connection, unresolved accessibility focus, or target invalidation; evidence does not uniquely select one. No new Termux insertion or focus-eligibility patch is justified, and no device fix is claimed.

## Implemented

Diagnostic capture now preserves one last unavailable external-input snapshot across opening AlienFlow's own diagnostic screen. It records sanitized package/class identifiers, input type, field ID, connection/start flags and resolver counts, never field text, credentials or audio. A bounded terminal-stage value records binding, dispatch, rejection or exception; dispatch is explicitly not receipt confirmation. This addresses a verified evidence-loss mechanism: the old latest-focus fields were overwritten by subsequent app focus, including the diagnostic screen.

The project already compiled and targeted API 36. Release CI now builds both APK and AAB. Pinned bundletool 1.18.3 is SHA-256 checked against the upstream release digest, validates the bundle, and dumps its manifest. Packaging checks matching package/version/minimum/target SDK, non-debuggable state, source SHA in DEX, unsigned state and no native libraries; it retains the AAB and reports in the existing delivery ZIP. Direct APK signing and delivery use the original certificate. Play App Signing enrollment/upload-key setup are future Console actions and have not been performed. Preserve the existing app-signing identity when enrolling if APK-to-Play update continuity is required; do not export private keys into this repository or CI artifacts.

## Verification

Exact source 658c9186056e84f91d12f34addf823e645200dd4 (tree 1a26d7cc616590239e3b80c200b9fefb1884073e) passed the single workflow 38399312969: 198 tests, zero failures/errors/skips, release lint zero errors (38 non-error issues), APK assembly and AAB assembly/validation. All 141 bundle-member hashes were verified. The APK uses the original certificate with all 25 payload entries unchanged. The signed APK and unsigned AAB were saved and their hashes verified afterward. Exact evidence: RELEASE_VERIFICATION_0.1.20.json. The added service test drives a missing external input, then switches to AlienFlow and verifies that the failure metadata survives without the private editor text. Existing terminal/document regressions remain required. SDK 35 framework fixtures do not certify installed Termux, ChatGPT, Android 16 or provider behavior. Termux insertion and intermittent ChatGPT bubble availability remain unresolved requirements; request a fresh diagnostic immediately after each failure using this diagnostic build.

## Primary sources

- https://github.com/termux/termux-app/blob/master/terminal-view/src/main/java/com/termux/view/TerminalView.java
- https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/core/java/android/view/inputmethod/BaseInputConnection.java
- https://github.com/aosp-mirror/platform_frameworks_base/blob/android16-release/core/java/android/view/inputmethod/RemoteInputConnectionImpl.java
- https://support.google.com/googleplay/android-developer/answer/11926878
- https://developer.android.com/guide/app-bundle
- https://developer.android.com/studio/publish/app-signing
- https://github.com/google/bundletool/releases/tag/1.18.3

Historical proof: RELEASE_VERIFICATION_0.1.19.json. Standing authority: AGENTS.md. Preserve the concurrent build/first-apk branch.
