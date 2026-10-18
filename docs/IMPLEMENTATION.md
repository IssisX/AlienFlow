# AlienFlow chat implementation — 0.1.29-chat.1 (30)

The owner confirms the latest balance drain occurred on Claude’s newer build. This candidate integrates `claude/alienflow` at `b96e59cdbc8ba38d28e5da50666c4efad8ea1305` (compiled .28 source `eff228559b8fc26e1a0ae7dbd15e903649d341eb`) into the isolated `AlienFlow(chat)` branch. Claude’s branch is not changed. Its editor, live-polish, keyboard-placement, Android 11 support and interface work are retained. [Historical Claude checkpoint](history/IMPLEMENTATION_CLAUDE_0.1.28-claude.1.md).

## Billing incident repair

[Incident and repair contract](BILLING_INCIDENT_2026-10-17.md) records the three reported balance drains, approximately $25 total. The claim in the prior checkpoint that nothing ran in the background was incorrect: an interrupted session could start a paid WAV upload after becoming idle, and Stop/Cancel ignored that upload. These paths also remain in the .28 source. No account billing records have been inspected, so exact charge attribution is still unverified.

Paid API access begins paused on both existing and new installations. Home and Account expose Pause all API activity. Recovery is explicit, visibly paid, cancellable when idle, limited to two minutes, and canceled when leaving its screen or locking the phone. Removing the key also pauses active work. All HTTP calls use actual cancellation, a 45-second deadline and no automatic connection retry. Local fallback no longer reconnects or uploads automatically.

Claude’s 15-second quiet default remains; values are bounded to 1–30 seconds and legacy Off means 30 seconds. The maximum recording length remains adjustable but is capped at five minutes (default five). Independent coordinator and capture-thread deadlines protect against missing audio/UI callbacks. The main deadline uses normal Stop to finish captured words; the capture-thread deadline closes the socket and retains unfinished audio if the UI cannot stop it. These are duration/request safeguards, not an account dollar cap. Active app switching remains enabled. Cancellation cannot refund work already processed by the provider.

The existing silence gate, light-polish merge, live unacknowledged-item ownership, bounded read-only context, keyboard choice and Termux final-only/no-Enter behavior remain. API 36, minimum API 30 and APK-only development remain.

## Evidence status

Preliminary run https://github.com/IssisX/AlienFlow/actions/runs/39074832610 reproduced both billing shutdown defects against .25, then ran 247 candidate unit tests: the spending-safety tests passed; two diagnostics assertions failed because their hard-coded version codes were stale. That run is not a passing release and is superseded by this integration. Diagnostics now assert the generated version identity.

Final source: `5dbb3c8d4dc30e450887125f4ec150f75cbc1458`; source tree `2aac0427fc8bf51f239677386ec6243ef4f33b28`. [Workflow 39126064823](https://github.com/IssisX/AlienFlow/actions/runs/39126064823) passed all six jobs: API 30, 31, 33, 34, 35 and 36 emulator configurations. Both billing defects reproduced by assertion against compiled .28, then the repaired suite passed **285 tests, zero failures/errors/skips**. Release lint reports 48 issues and **zero errors**. API 36 Chromium and installed Termux tests passed with real editor IPC and scripted speech.

Verified the bundle's GitHub artifact digest and all 244 manifest members. Signed the release with the original certificate `d79e74b3a4c24ee225d1220c2c374c6f958520d4f3257fcfb399ff09bf5c965f` using APK v3; every pre-signing APK entry remains byte-identical. Signed APK SHA-256: `b666160c941aa1b5a59ee08cce026161520aa1f30d2a6f3a748713cb7f78425a`. ZIP SHA-256: `94b76f98ae03d90d32688629294e332d5314bd4d1cc1c15101f8c1531c3baf63`; it contains only the byte-identical signed APK. APK version is 0.1.29-chat.1 (30), minimum API 30, target API 36. No AAB or shared release was published.

The preceding run 39125072693 had 285 tests with two imported fixtures missing explicit paid-access enablement; that setup was corrected. Run 39125977298 was superseded during connection-check notification ownership correction. These preliminary runs are not the delivered artifact.

**Owner's narrowed device scope, 18 October:** routine coverage is now three representative phone configurations: Android 12/API 31 for Samsung Note10+ OS coverage, Android 15/API 35, and Android 16/API 36. Drop the broader tablet/foldable/oldest-API matrix. This workflow-only change follows the already completed six-job build and does not alter or rebuild the signed APK. Physical Note10+ and Fold6 acceptance remain outstanding; an emulator is not Samsung/One UI proof.

No paid-provider request, billing-account inspection, physical phone test or installed-update acceptance was performed. Public paid release remains blocked.
