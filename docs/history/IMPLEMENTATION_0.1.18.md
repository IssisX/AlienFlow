# AlienFlow2 implementation checkpoint — 0.1.18

Repository: IssisX/AlienFlow. Branch: AlienFlow2. Date: 2026-10-12. VersionCode: 19.

## Report and diagnosis

The owner reports that 0.1.17 now works in ordinary inputs but fails in the Termux terminal, which used to work. Exact phone diagnostics were not supplied. Baseline local 8d769eb5bbfd03cf2fd82941aa020860d97f91a5 and remote 114e74d601c880581b067dee8aa17573d16b592f share tree 1ccf4c6f84226a9f965518eabaf98b97e44e5352. The checkout was clean; preserve build/first-apk and the working ordinary-field path.

Termux eligibility and final insertion exist, but the coordinator's targetEvent unconditionally called pumpWrite despite displayLive=false. While recording, the terminal adapter rejects premature writes; pumpWrite then clears the editor binding. During finishing, the same event can send a partial result before the last transcript, consuming the terminal's single permitted commit. A method-level write gate is missing: rendering honored the preference, event-triggered writes did not. This defect explains a failing source path; its contribution to the exact phone failure and when it first became observable remain unverified. No eligibility rule, keyboard API, service, model or ordinary editing algorithm is changed.

## Repair and acceptance

pumpWrite centrally blocks event/live calls for terminal and explicit end-only targets. Only tryFinish can authorize finalDelivery after audio drain, commit acknowledgements, transcript completion, optional bounded polish and write state are ready. Termux receives the finished result once, preserves its existing input, strips action controls and never sends Enter. Focus loss retains the result without writing into another field. The same gate also preserves the existing end-only document preference. Live ordinary fields keep the existing dispatch path.

Added tests exercise public coordinator start/event/stop, production bridge, framework BaseInputConnection consume-and-clear behavior, existing terminal input, Unicode and control sanitization, active and finishing accessibility events, late final recognition, exactly-once delivery, focus loss, and the end-only document preference. Accessibility broker/remote IPC, key and recognition events are explicit fixtures; no provider or physical microphone path is claimed. The new tests were not run against the old implementation; source inspection was the discriminator. One corrected release build will run the complete existing suite plus these tests, release lint and assembly.

## Verification

Final source 185665b2451e1ce3b9a78e2cb4ba4697bd70e581 (tree 9fe35381143839860f557e052292f9003d5e737a) passed the single CI run 38310603246: 192 tests, zero failures/errors/skips and zero lint errors (38 non-error issues). Verified all 134 package-member hashes, compiled source in DEX and approved artwork. Signed with the original update certificate, with all 25 APK ZIP payload entries unchanged. Saved AlienFlow-0.1.18.apk successfully and verified SHA256 after saving. Full evidence: RELEASE_VERIFICATION_0.1.18.json. Keep the 0.1.17 Chrome window/tail fixes, typing/composition protections, background Ready service, microphone-stop ordering, cancellable cleanup budgets, no Enter, selected keyboard, approved artwork and cost reductions.

Existing exact evidence: RELEASE_VERIFICATION_0.1.17.json and history/IMPLEMENTATION_0.1.17.md. Standing authorization is in AGENTS.md. Installed Termux/Fold6 acceptance remains unverified until phone observation; no paid API call is made for this repair.
