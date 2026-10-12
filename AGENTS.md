# AlienFlow implementation contract

Repository: IssisX/AlienFlow. Working branch for this checkout: AlienFlow(chat). Application ID: io.github.issisx.alienflow.

- AlienFlow(chat) is the owner-authorized isolated repair branch copied from AlienFlow2 at 185665b2451e1ce3b9a78e2cb4ba4697bd70e581. Do not merge, reset, or write AlienFlow2, build/first-apk or main. Version 0.1.17 is owner-confirmed working outside Termux and is the regression baseline.
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
