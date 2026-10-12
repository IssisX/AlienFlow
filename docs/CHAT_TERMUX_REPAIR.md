# AlienFlow(chat) — isolated Termux repair

Owner request (2026-10-12): copy AlienFlow2, preserve the reported working 0.1.17 behavior everywhere else, fix the Termux terminal. Actual repository is IssisX/AlienFlow; the source is branch AlienFlow2. The new AlienFlow(chat) branch copied source 185665b2451e1ce3b9a78e2cb4ba4697bd70e581 without writing to another branch. That source already includes a proposed 0.1.18 repair, which is retained with explicit provenance rather than reinvented. Shipped 0.1.17 source is 7ebe095bfaa83456690b46d8934c0cad2590a94a.

## Supported defect

An accessibility/selection event calls coordinator.targetEvent(), which previously called pumpWrite without respecting the final-only Termux route. A provisional result during recording therefore enters a terminal adapter that rejects it and clears the binding. During finishing the same callback can consume the one terminal commit before the last transcript arrives. Termux's InputConnection consumes committed text immediately; it is not a document span to repeatedly replace.

The inherited seven-line coordinator repair centrally blocks non-final writes for terminal/end-only targets. Only tryFinish authorizes final delivery after existing capture drain, recognition and bounded cleanup gates. No new production transport, editor eligibility, Chrome input algorithm, keyboard, storage or icon changes are introduced here.

## Verification, not agent agreement

The added CI baseline step runs the exact same TerminalCoordinatorTest against shipped 0.1.17 in a separate worktree. It must expose exactly two specified failures while the focus-loss protection case passes. It fails closed on compilation problems, unrelated errors, unexpected passes or additional failures. The candidate must then pass the complete suite.

A separate instrumentation test installs official Termux v0.118.3 on a disposable API36 emulator and drives the real floating bubble, accessibility service, cross-process InputConnection and terminal echo. Transcript events are explicit fixtures and an unstarted transport is injected; it does NOT test hardware microphone capture or OpenAI. No API request is made. Existing terminal input must remain; no provisional text appears; the final phrase appears once after Stop and late-final recognition. An Enter would be detectable as duplicate cat echo. Physical Fold6 acceptance remains a separate observation.

Only test dependencies are added (AndroidX runner 1.7.0 and junit extension 1.3.0). Test code and transcript fixtures are excluded from release runtime. New delivery version 0.1.18-chat.1 (code 20) distinguishes this branch's installer from the other agent's code 19. Same package and permanent signing key; updates must not require uninstalling.

Current state: tests written and scheduled next; no completed results claimed. Historical implementation files on the copied branch are historical records, not evidence that these new gates have passed.

## Primary sources checked

- https://raw.githubusercontent.com/termux/termux-app/master/terminal-view/src/main/java/com/termux/view/TerminalView.java — commitText and finishComposingText consume/clear the IME buffer, onCheckIsTextEditor returns true, terminal TYPE_NULL is intentional.
- https://developer.android.com/reference/android/accessibilityservice/InputMethod.AccessibilityInputConnection — accessibility commit/selection contract.
- https://developer.android.com/reference/android/app/Instrumentation — use UiAutomation across applications and DONT_SUPPRESS_ACCESSIBILITY_SERVICES while exercising AlienFlow.
- https://github.com/termux/termux-app/releases/tag/v0.118.3 — official pinned installed Termux test package and published checksum.

Do not edit AlienFlow2, build/first-apk or main. Do not claim paid-release or universal-editor readiness from these tests.
