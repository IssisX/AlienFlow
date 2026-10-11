# AlienFlow current checkpoint — 0.1.5

Repository: IssisX/AlienFlow. Branch: build/first-apk. Baseline: d148e9da7191eff78d49a674a92118a79775a071. Date: 2026-10-11.

## Scope and observations

The owner reports continued mic-start exits on 0.1.4, now described as graceful, and needs an accessible way to send evidence. The exact generated Gray alien was approved. No Android exit record, Java stack, or native trace from the phone has yet been supplied. The green indicator alone does not establish receipt of microphone frames by AlienFlow, or upload to OpenAI. “Thinking failed” did not undo the published source: the baseline includes the source handoff workflow from that interrupted turn.

The prior checkpoint is preserved verbatim in history/IMPLEMENTATION_0.1.4.md. All earlier source/history, release metadata, signing identity, user preferences, key storage, transcript history and recovery files are preserved. The signed 0.1.4 payload was compared to its original unsigned bundle and all entries match. The 0.1.3/0.1.4 DEX files do contain the previous diagnostic label/class; this does not establish that the owner could reach that UI, and the new UI does not depend on those instructions.

## Implemented repair and evidence collection

- EditorBridge now uses AccessibilityService.findFocus(FOCUS_INPUT) across windows. The old rootInActiveWindow search follows the currently touched window, which can be the bubble, rather than keyboard focus. Missing/currently changed input is still rejected; no stale-target fallback or blind mutation was added. A broker-fixture regression exercises the production resolver with an active overlay and a distinct keyboard-focused editor; physical Samsung broker behavior is a separate gate.
- Accessibility teardown detaches the service from the coordinator before publishing a terminal status or removing its views. No status update is intentionally routed through a destroyed overlay.
- The landing screen identifies the installed version and puts **Diagnostics / share report** immediately below the title/version. The same entry is in the long-press bubble menu. A dedicated DiagnosticsActivity contains the real preview, Share, Copy, Save and Refresh actions. The old duplicate diagnostic subsection was removed. Share uses Android's chooser and requires an explicit user action; nothing is sent automatically.
- The report includes current build/source/certificate, device/API/ABI, microphone and notification grant state, accessibility/capture binding, an 80-event metadata trace, latest editor AF-E code, and caught/uncaught Java failure locations. Android ApplicationExitInfo supplies up to five historical exits, separating process death from app-controlled recording stops. System exit descriptions and arbitrary exception messages are excluded.
- When Android retains a native tombstone, a bounded parser extracts signal numbers and up to 24 code-location frames from the crashing thread. Raw memory, abort messages, logs, file descriptors and command-line strings are neither shared nor saved by the parser. No trace is reported as no available trace, not proof of no crash.
- An explicit three-second Android microphone probe reads PCM and counts samples, with no WebRTC, API request, recording file or text insertion. Leaving its activity cancels it and releases AudioRecord. Its result does not establish realtime transcription success.
- The approved artwork is scaled/encoded as a packaged WebP, not redrawn. Both overlay and launcher load that asset. Default 75% idle opacity and the existing sliders remain unchanged.

## Research correction

The earlier “two microphone demands caused the crash” claim was not established. The fork explicitly coordinates client and native demand using separate flags; the original PROTOCOL.md already described that mechanism. The 0.1.4 experiment failed on the phone. This change does not introduce another speculative microphone backend/dependency/effect change. Current primary references and the narrower conclusions are in RESEARCH_2026-10-11.md.

## Verified 0.1.5 delivery

Compiled source: `c289cb98f5d1dabffcc8eadf6fad98dbc586efc5`, tree `600cf1386cdd024d451607c8c8deec84c7c5c822`. Later documentation-only commits record these outcomes without changing the compiled code.

- The initial run at `43b6bdbf29e426347715441ad2b67ea25cc5016a` failed during Gradle configuration because AGP rejects a Provider supplied to the legacy Android SourceSet API. No application test/build pass was claimed for that run. The generated artwork was registered through the documented variant generated-source API, carrying its task dependency; no compatibility override or disabled check was added.
- [Actions run 38139391856](https://github.com/IssisX/AlienFlow/actions/runs/38139391856) passed all 69 tests, with zero failures/errors/skips. This includes six production-focus-resolver executions with a simulated OS broker, three native-metadata parser tests, and two actual activity/click-handler tests, alongside the existing suites.
- MainActivity and DiagnosticsActivity were rendered under Robolectric API 35 at 822×1782. Both images were visually inspected. The diagnostics entry is visible below the title/version; Copy places the actual report on the clipboard; Share uses the same text in Android's chooser; Save launches the system document picker. These are framework-level tests, not physical phone screenshots or a completed file-save/device/microphone integration test.
- Release lint: zero errors, 29 warnings, all inspected. They concern deliberate synchronous diagnostic persistence, pinned-version/SDK recommendations, ARM64-only coverage, localization, absolute overlay coordinates and launcher-theme resource polish. No error baseline or new suppression was used.
- APK assembly, application/version identity, native 16 KiB alignment, and artifact upload passed. GitHub artifact `11702214281` SHA-256 `7edbffea6c2d98b21190db3ea18fd5c02f970f2d68eb86be5266b5bb23e4fa02` matched the downloaded bundle. All 39 manifest-listed member hashes matched.
- The approved WebP survived packaging byte-for-byte with SHA-256 `8850ac9bbd07c876ba00e5b4339d1b126a14d631194b07379dde0ac676888940`. AAPT shortened its packaged path to `res/0p.webp`; the source drawable identifier remains in the resource table. The DEX contains the exact compiled source identifier, version and diagnostic UI labels.
- The final signed APK is 17,086,706 bytes with SHA-256 `923055310de532dfd2194fa403140fe264cc963c426db52f1b927911ff20341a`. APK v3 verification passed using original certificate `d79e74b3a4c24ee225d1220c2c374c6f958520d4f3257fcfb399ff09bf5c965f`. All 26 original payload entries match the unsigned candidate; the signer added only its three META-INF signature metadata entries.
- Local source whitespace checks passed excluding the intentionally verbatim historical Markdown copy (its original hard-break whitespace was retained). XML/Python syntax and the bounded native-parser smoke test passed. No local Android SDK build, emulator, paid provider call or physical phone test was run.

The exact machine-readable record is RELEASE_VERIFICATION_0.1.5.json and actual signer output is APK_SIGNATURE_0.1.5.txt.

## Remaining acceptance

The original shutdown on the Fold6 remains unresolved until the same phone path succeeds or its diagnostic report identifies the failure. Install the signed update in place, verify Version 0.1.5 (6), and try one dictation. If it stops/exits, open the top-level **Diagnostics / share report** and copy or save the report. Android exit timestamps plus session events distinguish graceful stop, Java/native crash, permission/update termination or missing system evidence. The three-second probe is optional and proves only basic AudioRecord reads.

Do not equate a passing build, simulated broker test or framework screenshot with real microphone/provider/editor success. Preserve the product requirements, existing signing identity, saved settings and user work. A report with no native trace is incomplete evidence, not a no-crash finding.
