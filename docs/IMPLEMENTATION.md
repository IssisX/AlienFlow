# AlienFlow chat implementation — 0.1.30-chat.1 (31)

## Confirmed baseline

**FIXED — Cory confirmed on 18 October 2026 that 0.1.29-chat.1 no longer drains the account in the background.** Its exact APK is preserved with SHA-256 `b666160c941aa1b5a59ee08cce026161520aa1f30d2a6f3a748713cb7f78425a`. The [0.1.29 checkpoint](history/IMPLEMENTATION_CHAT_0.1.29-chat.1.md) retains the original build, test, signing and evidence details. The [incident record](BILLING_INCIDENT_2026-10-17.md) identifies the automatic-upload and idle-cancellation defects.

This efficiency update starts from `d104a2f4090e6b1c339d092e68de61f3845fb6a6` on `AlienFlow(chat)`, whose application payload is the fixed 0.1.29 source `5dbb3c8d4dc30e450887125f4ec150f75cbc1458`. Other branches are unchanged.

## Smallest cost reduction

The default for optional `live_cleanup` is now false. Words still appear live; default B light polish runs after Stop through the existing finalization path. This avoids repeatedly sending the growing transcript for another correction during speech. Explicitly saved true/false preferences remain authoritative. The setting describes the cost tradeoff.

Correction still uses `gpt-6-luna`, reasoning disabled, the same bounded read-only context, prompt, lossless chunking, and meaning-preserving merge. The final pass retains its 2.5-second combined deadline. A long recording may need several chunks within that pass; it is not an unlimited one-request guarantee. Failed, late or canceled polish retains recognized words. Filler removal and Rewrite are not enabled automatically.

Live transcription model, delay, silence gate, paid-access consent, HTTP cancellation/retry policy, recording deadlines, explicit recovery, connection behavior, and editor ownership are unchanged. Stop and Cancel still terminate auxiliary work even while dictation is idle. No automatic upload, reconnect, retry or model escalation is added.

The [cost decision](COST_EFFICIENCY_2026-10-18.md) records pricing assumptions and the boundary between an inexpensive development client and a future sponsored trial.

## Verification status

Local Gradle execution is environmentally blocked: the workspace has Java 17 but no cached Gradle/Android toolchain, and its network sandbox refused the pinned Gradle distribution download. No local compilation result is claimed. The existing GitHub Actions pipeline owns compilation, tests, lint, packaging and the three representative Android configurations. Its actual result and delivered hashes must be recorded before claiming the new APK is verified.

No paid provider request or billing-account inspection is part of this change. Automated transcript fixtures cannot establish real microphone accuracy, provider token usage or perceived correction latency. Physical Fold6 and Note10+ acceptance remains the owner's limited device scope; public trial provisioning and store publication are not implemented by this release.
