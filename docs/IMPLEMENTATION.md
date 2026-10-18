# AlienFlow chat implementation — 0.1.30-chat.1 (31)

## Confirmed baseline

**FIXED — Cory confirmed on 18 October 2026 that 0.1.29-chat.1 no longer drains the account in the background.** Its exact APK is preserved with SHA-256 `b666160c941aa1b5a59ee08cce026161520aa1f30d2a6f3a748713cb7f78425a`. The [0.1.29 checkpoint](history/IMPLEMENTATION_CHAT_0.1.29-chat.1.md) retains the original build, test, signing and evidence details. The [incident record](BILLING_INCIDENT_2026-10-17.md) identifies the automatic-upload and idle-cancellation defects.

This efficiency update starts from `d104a2f4090e6b1c339d092e68de61f3845fb6a6` on `AlienFlow(chat)`, whose application payload is the fixed 0.1.29 source `5dbb3c8d4dc30e450887125f4ec150f75cbc1458`. Other branches are unchanged.

## Smallest cost reduction

The default for optional `live_cleanup` is now false. Words still appear live; default B light polish runs after Stop through the existing finalization path. This avoids repeatedly sending the growing transcript for another correction during speech. Explicitly saved true/false preferences remain authoritative. The setting describes the cost tradeoff.

Correction still uses `gpt-6-luna`, reasoning disabled, the same bounded read-only context, prompt, lossless chunking, and meaning-preserving merge. The final pass retains its 2.5-second combined deadline. A long recording may need several chunks within that pass; it is not an unlimited one-request guarantee. Failed, late or canceled polish retains recognized words. Filler removal and Rewrite are not enabled automatically.

Live transcription model, delay, silence gate, paid-access consent, HTTP cancellation/retry policy, recording deadlines, explicit recovery, connection behavior, and editor ownership are unchanged. Stop and Cancel still terminate auxiliary work even while dictation is idle. No automatic upload, reconnect, retry or model escalation is added.

The [cost decision](COST_EFFICIENCY_2026-10-18.md) records pricing assumptions and the boundary between an inexpensive development client and a future sponsored trial.

## Verified delivery — 18 October 2026

Compiled source: `ebd4e04ed438043ca239b6882de0fd95028cdc85`; source tree: `5c073bd0c6d20bd5c6b47dcfc86b5622206f6c4c`. A later documentation-only commit records this delivery and is not the compiled APK revision.

[Actions run 39135390672](https://github.com/IssisX/AlienFlow/actions/runs/39135390672) completed successfully on the exact source. All three jobs passed: Android API 31 phone, Android API 35 phone, and the release job with Android API 36 browser/installed-Termux integration. Editor checks use scripted transcripts; no paid provider or physical microphone is involved.

- **293 unit tests executed; zero failures, errors or skips.** All eight new `BalancedCleanupTest` cases passed, covering default and saved timing preferences, live-word delivery, verbatim mode, real-call cancellation, late callbacks, the 2.5-second aggregate deadline, and actual serialized request counts.
- Existing billing-shutdown reproduction identified the same two failures in the old 0.1.28 baseline; both corresponding candidate regression tests passed. Historical Termux, app-switch, writing-quality and live-polish reproduction gates also passed.
- Release lint: **zero errors; 48 issues**. Debug/instrumentation and release APK assembly passed. Package identity, SDK 30–36, non-debuggable state, unsigned intermediate and alignment were checked by the release packager.
- Local source comparison verified nine critical transport, HTTP, correction and capture files are byte-identical to the fixed 0.1.29 source. The coordinator differs only in its two references to the shared default; lifecycle method bodies are unchanged.

The controlled six-phrase fixture recorded:

| Policy | Correction requests | Total HTTP request-body UTF-8 bytes | Serialized input UTF-8 bytes |
| --- | ---: | ---: | ---: |
| Default polish after Stop | 1 | 812 | 113 |
| Explicit live-polish opt-in | 6 | 4,537 | 343 |

Both fixture paths retained the same final text. These measurements demonstrate removal of repeated correction work; they are not billed token counts, real-audio quality results or a percentage reduction in the audio bill.

### APK provenance and signing

Actions artifact `12140230619` was verified against its SHA-256 `ef8283db78b6b3c8d04390cdb0ecf2eb8dda300119373d0247809b833c8eb1e0`; all **255 manifest member hashes** matched, and the release DEX contained the exact compiled source SHA. Private signing used the original key outside the repository. APK v3 verification passed with the permanent certificate `d79e74b3a4c24ee225d1220c2c374c6f958520d4f3257fcfb399ff09bf5c965f`. All 25 original APK payload entries remained byte-identical; only signature metadata was added.

| Delivered file | Bytes | SHA-256 |
| --- | ---: | --- |
| `AlienFlow-0.1.30-chat.1.apk` | 4,671,640 | `be7c8520655b4d7b99b7c9441bc819411fcfece36868656d0f424ada3f326389` |
| `AlienFlow-0.1.30-chat.1.zip` | 1,689,679 | `4e3913368a9a1a3d473fe307c683d81c4e99c83ed15cba1fdd2ef6d050dbf0f9` |

The ZIP contains only the byte-identical signed APK. The signed APK, ZIP and `AlienFlow-0.1.30-chat.1-verification.json` were delivered alongside the preserved 0.1.29 APK and its FIXED release note. The verification record contains the source, CI jobs, suite counts, request fixture, hashes and evidence limits.

### Remaining evidence limits

Local Gradle execution was environmentally blocked by the workspace's unavailable cached toolchain and network sandbox; compilation and Android verification above ran in CI. No local compilation result is claimed.

No paid provider request or billing-account inspection is part of this change. Automated transcript fixtures cannot establish real microphone accuracy, provider token usage or perceived correction latency. Physical Fold6 and Note10+ acceptance remains the owner's limited device scope; public trial provisioning and store publication are not implemented by this release.
