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

## Verification status

At this working checkpoint, local XML/Python syntax and whitespace checks passed. Added regression tests cover production focus routing, native trace parsing and privacy exclusions, and the actual main/diagnostic activities and copy/share/save actions. CI must execute those tests and supply real activity renders before release packaging. Android compilation, final APK signing and device results are not yet claimed here; the delivery evidence record will name the exact compiled source and outcomes.

The phone crash remains unresolved unless the same device path succeeds. An update should be installed in place (same io.github.issisx.alienflow certificate, versionCode 6). One new report is sufficient to distinguish graceful stop, Java crash, native crash, permission change or missing system evidence. Do not equate a passing build or simulated broker test with that device proof.
