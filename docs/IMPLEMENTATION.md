# AlienFlow chat implementation — 0.1.29-chat.1 (30) candidate

The owner confirms the latest balance drain occurred on Claude’s newer build. This candidate integrates `claude/alienflow` at `b96e59cdbc8ba38d28e5da50666c4efad8ea1305` (compiled .28 source `eff228559b8fc26e1a0ae7dbd15e903649d341eb`) into the isolated `AlienFlow(chat)` branch. Claude’s branch is not changed. Its editor, live-polish, keyboard-placement, Android 11 support and interface work are retained. [Historical Claude checkpoint](history/IMPLEMENTATION_CLAUDE_0.1.28-claude.1.md).

## Billing incident repair

[Incident and repair contract](BILLING_INCIDENT_2026-10-17.md) records the three reported balance drains, approximately $25 total. The claim in the prior checkpoint that nothing ran in the background was incorrect: an interrupted session could start a paid WAV upload after becoming idle, and Stop/Cancel ignored that upload. These paths also remain in the .28 source. No account billing records have been inspected, so exact charge attribution is still unverified.

Paid API access begins paused on both existing and new installations. Home and Account expose Pause all API activity. Recovery is explicit, visibly paid, cancellable when idle, limited to two minutes, and canceled when leaving its screen or locking the phone. Removing the key also pauses active work. All HTTP calls use actual cancellation, a 45-second deadline and no automatic connection retry. Local fallback no longer reconnects or uploads automatically.

Claude’s 15-second quiet default remains; values are bounded to 1–30 seconds and legacy Off means 30 seconds. The maximum recording length remains adjustable but is capped at five minutes (default five). Independent coordinator and capture-thread deadlines protect against missing audio/UI callbacks. The main deadline uses normal Stop to finish captured words; the capture-thread deadline closes the socket and retains unfinished audio if the UI cannot stop it. These are duration/request safeguards, not an account dollar cap. Active app switching remains enabled. Cancellation cannot refund work already processed by the provider.

The existing silence gate, light-polish merge, live unacknowledged-item ownership, bounded read-only context, keyboard choice and Termux final-only/no-Enter behavior remain. API 36, minimum API 30 and APK-only development remain.

## Evidence status

Preliminary run https://github.com/IssisX/AlienFlow/actions/runs/39074832610 reproduced both billing shutdown defects against .25, then ran 247 candidate unit tests: the spending-safety tests passed; two diagnostics assertions failed because their hard-coded version codes were stale. That run is not a passing release and is superseded by this integration. Diagnostics now assert the generated version identity.

The final candidate must reproduce the billing defects against compiled .28, pass the combined unit/lint suite and all inherited emulator gates, and have its artifact and original signer verified. Final verification is pending. No paid-provider request or physical Fold6 test has been performed. Public paid release remains blocked.
