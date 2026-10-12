# AlienFlow2 implementation checkpoint — 0.1.16

Repository: IssisX/AlienFlow. Branch: AlienFlow2. Date: 2026-10-12. VersionCode: 17.

## Objective and cost ownership

Reduce paid request overhead without changing microphone capture, low-latency gpt-live-transcribe, recognition frames, original keyboard/editor ownership, or correction modes. The owner provides standing authorization for routine AlienFlow2 commits, remote updates, existing workflow builds, signing and APK delivery; do not repeat permission requests for those actions. This does not represent a public launch or provision a paid trial account.

Current official pricing bills gpt-live-transcribe by audio duration ($0.017 per minute on 2026-10-12), rather than transcription-context tokens. Removing vocabulary does not reduce that flat audio-minute charge. Do not gate out quiet audio, stop natural thinking pauses, or switch recognition models on an unmeasured cost claim. No savings percentage for the user's actual bill has been measured. GPT-6 Luna cleanup has token-based input/output pricing; its default medium reasoning was previously left unspecified.

## Implemented efficiency changes

Personal vocabulary is opt-in and off by default. Existing saved text remains intact. Both actual capture and connection-check paths call LocalStore.vocabularyForSession, so a disabled list cannot enter a request. Enabled entries use documented literal keywords instead of a free-form prompt, with case-insensitive duplicate removal and invalid keyword/control-character exclusion. There is no automatic history or project-word-list prompt. Settings explains the option and how to keep a short list.

Cleanup remains on gpt-6-luna and retains A/B/C behavior and the lexical/meaning guard. Instructions are shorter; reasoning.effort is explicitly none. Output ceilings scale conservatively with input UTF-8 bytes (with separate rewrite headroom) inside the prior 2,048-token ceiling. Unused output allowance is not billed; lowering that ceiling alone is not a claimed token saving. Actual recognized text is not shortened, and long dictations retain the existing lossless chunking.

A per-recording bounded LRU memo reuses accepted cleanup results for identical source chunks with the same correction mode and filler setting. Invalid results and request failures are not cached. The memo is owned by the single cleanup job and is replaced at recording start, finalization and cancel; an old canceled job cannot populate the next recording's instance. No disk, cross-recording, user-profile, API-key or transcript-history cache was added. Repeated speech remains repeated speech in the field; only its redundant cleanup request is omitted.

Public-key trial credentials and three-day expiry are not implemented here. The owner's master API key must remain server-side; the future trial needs a backend to grant scoped session credentials and enforce expiry/spend. The APK continues to use a user-entered encrypted key and contains no sponsor key.

## Verification status

Pending exact-revision GitHub Actions tests, release lint and assembly, package-member hashes, unchanged icon, signed update certificate/payload, save and checkpoint. Added request-policy and loopback HTTP tests cover real serialized cleanup bodies, no-reasoning configuration, UTF-8-aware output limits, vocabulary opt-in/retention, documented keyword shape, duplicate reuse, guard rejection, and bounded eviction. These are not live OpenAI billing, model-quality or Fold6 measurements. Preserve the 0.1.15 capture/editor regressions, including no-space keyboard composition and protected edits.

## Primary sources

- https://developers.openai.com/api/docs/models/gpt-live-transcribe
- https://developers.openai.com/api/docs/models/gpt-6-luna
- https://developers.openai.com/api/docs/guides/realtime-transcription

Prior verified checkpoint: history/IMPLEMENTATION_0.1.15.md. Phone acceptance should compare recognition and correction quality with 0.1.15, then test vocabulary disabled and enabled. Actual invoice savings and the public trial remain unverified.
