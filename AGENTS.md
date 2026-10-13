# AlienFlow implementation contract

Repository: IssisX/AlienFlow. Working branch for this checkout: AlienFlow(chat). Application ID: io.github.issisx.alienflow.

- AlienFlow(chat) is the owner-authorized isolated repair branch copied from AlienFlow2 at 185665b2451e1ce3b9a78e2cb4ba4697bd70e581. Do not merge, reset, or write AlienFlow2, build/first-apk or main. The owner now requests the 0.1.19 source from AlienFlow2 at 3be1cd2b5bac1206ad3f9025b64d1b55e5c2eac1. Integrate into this branch only; preserve its prior Termux repair and tests. Version 0.1.17 remains the historical failing regression baseline, not the current product version.
- The owner gives standing approval for routine AlienFlow(chat) source edits, commits, remote updates, existing CI builds, signing and APK delivery. Continue those authorized actions without asking again; this does not authorize publishing private keys or taking over unrelated destinations.

Read docs/PRODUCT_SPEC.md for the product requirements and docs/IMPLEMENTATION.md for actual implementation/evidence status. Requirements are not runtime evidence.

- Keep the selected keyboard. The accessibility overlay is nonfocusable.
- Default to live in-field text and very light live corrections. No per-app policies or application word quota.
- One coordinator owns dictation state; one editor bridge performs guarded edits and confirms outcomes.
- Preserve unrelated text, user edits, negation, names, numbers, slang, and profanity. Never submit a field.
- Phone-held OpenAI keys are encrypted at rest. Never put keys, recordings, dictated text, or signing private keys in source, logs, CI artifacts, or caches.
- A successful build proves compilation, not microphone access or compatibility with every editor. Report untested device behavior explicitly.
- Agents edit only assigned paths. The lead owns commits, remote writes, workflow runs, signing, and delivery.
- Keep exact dependency versions in the build configuration. Tests must exercise actual state/ordering/ownership logic.

- Current repair imports the reviewed 0.1.22 keyboard-coordinate fix from AlienFlow2 into this branch only. Preserve the chat terminal guard, all prior history and the source branch. Current phone baseline is 0.1.21 (22), source 5e248ee3a9c4c91a0c78580f0917d641e7035b15. API 36 and APK-only development remain required; no AAB work until final store preparation.
