> **Release blocker, 17 October 2026:** owner reports three unintended balance drains (~$25), latest on Claude’s build. Automatic recovery/idle-cancellation defects are being repaired. Prior cost-control work does not establish billing safety. See [incident](BILLING_INCIDENT_2026-10-17.md); no paid public launch is certified.

# Paid release readiness

The owner intends to charge for AlienFlow on Google Play. A development APK, passing unit tests, and injected recognition events do not establish paid-release readiness. Public release is **not signed off**.

## Current evidence boundary

The existing pipeline exercises Android 16 Chromium/WebView, native EditText, and installed official Termux. The input IPC and terminal echo are real; speech recognition and microphone-readiness events are fixtures. Exact source, counts and failures belong in each release verification record. The 0.1.25 candidate adds contextual cleanup, final-phrase cleanup, protected typo handling and mid-sentence boundaries.

The 15 October download failure was addressed by restoring the stored 0.1.24 signed APK and checking its original SHA-256. User-side download success is not observable here. Deliver 0.1.25 as an APK plus a ZIP containing byte-identical APK bytes.

## Required acceptance evidence before charging customers

| Area | Acceptance evidence still needed |
| --- | --- |
| Real speech quality | Compare human reference transcripts with actual microphone output, both provisional and final. `docs/EVALUATION.md` provides the repeatable provider-path measurement (word, case, sentence-break, meaning-word, missing-phrase and latency results, before and after polish); it has not yet been run with real recordings. Include the reported “thet/that” case, sentence case, pauses, mid-sentence insertion, names, amounts, negation, slang, accents, background noise and a long session. Record word errors, meaning errors and capitalization errors separately. |
| Real app switching | Repeatedly switch during recording among Chrome address/search/web editors, ChatGPT, Messages, native fields and Termux. Verify no missing/newly duplicated speech, no writes to the wrong field, no restart taps, and exact preservation of pre-existing and manually typed text. Speech captured between eligible fields remains in history. |
| Completion and failure | Check Stop and Cancel during startup, recording, active cleanup, final acknowledgement and storage. Exercise network loss, rejected credentials, limits, unavailable editors, microphone permission loss and screen lock. Retained work and recovery notices must agree. |
| Physical device behavior | Validate Samsung Fold6 Android 16 folded/unfolded, rotation and keyboard changes, plus a representative second device/keyboard. Emulator results are insufficient for background microphone, battery and vendor lifecycle behavior. |
| Latency and reliability | Measure tap-to-microphone, first text, revision and Stop-to-complete times using real audio. Compare the balanced recognition setting to the previous minimal setting. Establish and meet launch targets; no measured latency/accuracy claim is currently available. |
| Data and paid-product experience | Confirm upgrade/data retention, deletion/recovery, accessibility and microphone explanations, bounded surrounding-text disclosure, cost/billing clarity and the final customer account model. The current client uses the customer's own OpenAI API key and separate API billing. |
| Distribution | Perform the final Play-specific review and distribution setup when store preparation is authorized. APK-only/API 36 development continues until then. No publication, paid trial provisioning or AAB has been performed in this repair. |

Any observed dropped speech, wrong-field write, duplicated insertion, unintended submission, meaning inversion or lost user draft blocks release. Recognition quality and natural phrasing must be evaluated against actual audio; a prompt or a narrow spelling substitution list is not proof of general accuracy.

## Owner decisions — paid service model (15 October 2026)

**Status:** decided, not started. Nothing below is implemented yet. Work begins after the owner and a second tester accept a CI-green APK using the owner's own OpenAI key.

**Business model**
- Sell a Google Play subscription.
- Customers never enter an OpenAI key.
- The tester-only "use my own OpenAI key" option remains, as an advanced setting.

**Hosting:** Google Cloud.
- **Firebase Auth** for sign-in.
- **A small server on Cloud Functions or Cloud Run** holding the only OpenAI key, as a secret. It:
  - verifies the Play subscription and Play Integrity;
  - enforces a per-user usage limit;
  - issues a short-lived single-session credential for live transcription;
  - proxies the light-polish text request.
- **Firestore** for per-user usage counts.

Audio is never sent through the server.

**Verify before building**
- **UNVERIFIED:** OpenAI short-lived credentials for the live-transcription WebSocket.
- **VOLATILE:** current per-minute transcription pricing, which determines the subscription price and usage limit.

**Owner prerequisites (not yet created)**
- Google Play Console developer account.
- Google Cloud / Firebase project with billing.
- A dedicated server-side OpenAI key with a monthly spending limit.
