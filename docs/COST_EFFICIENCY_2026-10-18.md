# Balanced dictation cost — 18 October 2026

## Decision

Keep live recognition and useful light corrections. Default to one bounded polish pass after Stop, retaining the user's explicit live-polish preference when present. Do not change the working audio/session lifecycle to optimize a smaller text bill.

The fixed reference is AlienFlow 0.1.29-chat.1, source `5dbb3c8d4dc30e450887125f4ec150f75cbc1458`; Cory confirmed on 18 October that it no longer drains the account in the background. The efficiency candidate is 0.1.30-chat.1. The [incident contract](BILLING_INCIDENT_2026-10-17.md) remains mandatory.

## What produced unnecessary work

At the baseline, `DictationCoordinator.scheduleCleanup()` sends `ledger.stableFor(editorEpoch)` after a stable turn. That is the entire growing transcript for the field, not only newly spoken words. Its completion can schedule the next changed prefix. `CleanupMemo` reuses identical validated chunks plus identical context; new speech changes short prefixes, so it cannot eliminate those repeated requests. Stop cancels the current live request and may need another pass over the complete final text.

The new default removes those intermediate correction requests while preserving live recognition and the existing final polish. No queue, persistent cache, timer or network operation is introduced. The one shared default belongs to `LocalStore.DEFAULT_LIVE_CLEANUP`; the coordinator and settings use it. Saved preferences are not overwritten.

## Quality retained

- B light polish remains the default: sentence case, punctuation, guarded spelling corrections, and existing allowed spoken corrections.
- The current merge protects wording, names, identifiers, amounts, negation, uncertainty and profanity. It does not silently enable Rewrite or filler removal.
- Recognition remains `gpt-live-transcribe` at the existing delay. Its streaming words, silence gate and editor delivery stay intact.
- Correction remains `gpt-6-luna` with `reasoning.effort=none`, transient bounded context and no history prompt. The prompt and correction model are unchanged.
- Final polish keeps the existing 2.5-second aggregate deadline and lossless chunking. Longer recordings can require more than one request within this single pass. Errors, timeout and cancellation retain recognized words.
- Users can opt into live polish in Settings → Dictation & writing. If they explicitly saved that choice before upgrading, it remains selected; turn it off for the lower-request policy.

## Trial economics: estimates, not measured billing

Official rates checked on 18 October 2026:

| Component | Standard listed rate | Source |
| --- | --- | --- |
| Current live transcription | $0.017 per audio minute | [GPT-Live-Transcribe](https://developers.openai.com/api/docs/models/gpt-live-transcribe) |
| Completed-audio/committed-turn transcription | $0.0045 per audio minute | [GPT-Transcribe](https://developers.openai.com/api/docs/models/gpt-transcribe) |
| Current correction model | $0.10 per million input tokens; $0.50 per million output tokens | [GPT-6 Luna](https://developers.openai.com/api/docs/models/gpt-6-luna) |

Live audio is billed by duration. Sending fewer correction tokens does not lower its per-minute rate. The cheaper committed-turn model changes when speech becomes text and is not selected by this update. Model accuracy and perceived latency require representative real-audio evaluation before changing that path.

Illustrative 30-minute trial: 30 billable audio minutes, 6,000 transcript tokens, 60 final correction requests, 300 additional input tokens of instructions/context per request, and 6,000 output tokens. These are workload assumptions, not a tokenizer measurement or a guaranteed maximum.

- Live recognition: `30 × $0.017 = $0.51`.
- Correction input: `(6,000 + 60 × 300) × $0.10 / 1,000,000 = $0.0024`.
- Correction output: `6,000 × $0.50 / 1,000,000 = $0.003`.
- Combined illustrative API cost: **$0.5154 per fully used trial**, before infrastructure, taxes and other work. No cache discounts, retries or paid evaluation are assumed.

The proposed offer is **three days with up to 30 billable audio minutes, whichever ends first**. A time-only three-day trial has no comparable usage ceiling. Sponsored trials require the server-owned credentials and authoritative user entitlement/usage enforcement described in [paid release readiness](PAID_RELEASE_READINESS.md); they are not implemented in the current personal-key client.

## Evidence boundary

The source proves removal of the default live-polish scheduling path. Request-count regression fixtures exercise the production coordinator and request builder, and must be labeled as controlled transcripts/HTTP. Those counts and payload bytes are not provider-billed tokens or a percentage reduction in the complete audio bill. The unchanged billing and editor regression gates remain required. No customer key, real recording or paid API call is needed for these checks.

Real speech quality, actual token usage and provider stop-to-final latency remain unmeasured. This update does not claim that every possible optimization has been exhausted; it removes avoidable repeated work at a narrow, already-supported policy seam.
