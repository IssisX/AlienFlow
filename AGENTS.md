# AlienFlow implementation contract

Repository: IssisX/AlienFlow. Working branch for this checkout: AlienFlow(chat). Application ID: io.github.issisx.alienflow.

- AlienFlow(chat) is the owner-authorized isolated repair branch copied from AlienFlow2 at 185665b2451e1ce3b9a78e2cb4ba4697bd70e581. Do not merge, reset, or write AlienFlow2, build/first-apk or main. The owner now requests the 0.1.19 source from AlienFlow2 at 3be1cd2b5bac1206ad3f9025b64d1b55e5c2eac1. Integrate into this branch only; preserve its prior Termux repair and tests. Version 0.1.17 remains the historical failing regression baseline, not the current product version.
- The owner gives standing approval for routine AlienFlow(chat) source edits, commits, remote updates, existing CI builds, signing and APK delivery. Continue those authorized actions without asking again; this does not authorize publishing private keys or taking over unrelated destinations.

Read docs/PRODUCT_SPEC.md for the product requirements and docs/IMPLEMENTATION.md for actual implementation/evidence status. Requirements are not runtime evidence.

- Keep the selected keyboard. The accessibility overlay is nonfocusable.
- Default to live in-field text and very light corrections after Stop. Preserve explicitly saved live-polish choices. No per-app policies or application word quota.
- One coordinator owns dictation state; one editor bridge performs guarded edits and confirms outcomes.
- Preserve unrelated text, user edits, negation, names, numbers, slang, and profanity. Never submit a field.
- Phone-held OpenAI keys are encrypted at rest. Never put keys, recordings, dictated text, or signing private keys in source, logs, CI artifacts, or caches.
- A successful build proves compilation, not microphone access or compatibility with every editor. Report untested device behavior explicitly.
- Agents edit only assigned paths. The lead owns commits, remote writes, workflow runs, signing, and delivery.
- Keep exact dependency versions in the build configuration. Tests must exercise actual state/ordering/ownership logic.

- Current repair imports the reviewed 0.1.22 keyboard-coordinate fix from AlienFlow2 into this branch only. Preserve the chat terminal guard, all prior history and the source branch. Current phone baseline is 0.1.21 (22), source 5e248ee3a9c4c91a0c78580f0917d641e7035b15. API 36 and APK-only development remain required; no AAB work until final store preparation.

- 17 October billing incident: integrate the current Claude source (`b96e59cdbc8ba38d28e5da50666c4efad8ea1305`) into AlienFlow(chat) only, preserving its work and leaving claude/alienflow unchanged. Candidate 0.1.29-chat.1 (30) must start paid API access paused, remove automatic recovery uploads, cancel every idle API operation, and bound recording lifetime. No paid provider tests or API-credit replenishment. Retain imported API 30 minimum support and existing compatibility gates. See docs/BILLING_INCIDENT_2026-10-17.md.

- 18 October owner override: limit device work to representative common phones, explicitly including Samsung Note10+. Do not expand toward exhaustive device coverage. Routine CI is Android 12/API 31 (Note10+ OS coverage), Android 15/API 35 phone, and the existing Android 16/API 36 release job. Emulator evidence does not certify Samsung hardware/One UI. Keep Note10+ and the owner's Fold6 as physical acceptance priorities. This supersedes the broader matrix requirement above; do not delay delivery of an already-tested APK to rerun only a coverage-configuration change.

- 18 October efficiency request: the owner confirms 0.1.29-chat.1 no longer drains the account in the background. Preserve that APK and its billing repair. Version 0.1.30-chat.1 changes only the default timing of optional polish to one bounded pass after Stop; live recognition, paid-access consent, actual cancellation, deadlines, no automatic uploads/reconnects, and all editor safeguards remain. Keep the existing inexpensive correction model and meaning-preserving merge. No paid API evaluation or trial backend is authorized by this optimization.
