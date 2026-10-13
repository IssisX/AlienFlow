# AlienFlow(chat) — 0.1.19 integration and Termux verification

Repository: IssisX/AlienFlow. Working branch: AlienFlow(chat). Date: 2026-10-13.
Candidate version: 0.1.20-chat.1 (21). Build/device evidence is pending at this source checkpoint.

## Source and preservation

The requested AlienFlow2 is a branch, not a second repository. AlienFlow(chat) already existed at f24ace1d85b480005d5587b618e6cf37db48ab55 with a tested Termux repair. That work and history are preserved. This integration adds the complete upstream 0.1.19 checkpoint, 3be1cd2b5bac1206ad3f9025b64d1b55e5c2eac1 (compiled cad03c3aa1573944e7dbae8def245d2c97600680), without writing AlienFlow2, build/first-apk or main.

Both source histories are preserved verbatim in history/IMPLEMENTATION_CHAT_0.1.18-chat.1.md and history/IMPLEMENTATION_ALIENFLOW2_0.1.19.md. The approved icon, opacity, public interface, history, private key storage, package identity, background readiness, microphone/transport and model choices are not redesigned or reset.

## What the Termux repair actually is

The historical .17 coordinator attempted terminal writes during speech and while final recognition was incomplete. The terminal adapter refused a premature write or consumed its one delivery too early. The existing centralized finalDelivery gate fixes both paths. It already exists in .18/.19 and in the preserved chat branch; this turn does not claim it is a newly discovered .19 defect. The user reports Termux remains a concern, but no new .19-specific Termux failure trace was supplied.

Termux continues to receive final corrected text once after Stop, keeping existing input and never sending Enter or control keys. Live ordinary document fields retain .19's exact ranged-editor behavior, including the IME-only suffix preservation repair. The only additional runtime delta versus .19 is that a control-only result, which becomes blank after terminal control conversion, cannot consume a delivery slot or insert a blank fragment. Its regression exercises the actual TerminalInsertion owner.

## Verification plan and test boundaries

The inherited regression discriminator still runs the same three tests on shipped .17 and requires exactly the two premature-write failures; the changed-field protection case must pass. Candidate runs retain all .19 editor tests and chat tests.

The installed-Termux Android 16 emulator test now verifies a real cross-app round trip: Termux final delivery with delayed final recognition; an ordinary native field with live partial words and live final correction before Stop; then Termux again for an independent second final-only dictation. Actual injected screen taps exercise the real overlay, accessibility service and cross-process InputConnection. Existing prefix, no duplicates, no partial terminal sends and no Enter are asserted. A release requires every leg to pass, not just a unit fixture.

Recognition callbacks and an unstarted audio transport are explicit test fixtures. These checks do not exercise paid OpenAI calls, a physical microphone, Gboard, tmux/Codex, or the user's Fold6. Successful emulator output cannot certify those boundaries.

## Release polish and pending store work

The branch/version identifies this update distinctly and versionCode 21 permits an in-place update over both .19 and the previous chat build. Versioned UI artifacts, package checks and historical release records are retained. Terminal control-only output has an explicit non-delivery regression instead of a false successful send. PLAY_RELEASE_READINESS.md records the current publishing gaps after checking official Play/Android documentation; this is still a development APK.

## Next physical check

Install the signed candidate in place without clearing data. Keep the usual keyboard. Dictate in Termux and tap Stop; text should arrive once without Enter. Switch to an ordinary field and verify live updates, then return to Termux for a second dictation. If it fails, use Settings → Help & about → Diagnostics / share report and send the new version-specific session events. Do not substitute older WebRTC crash records for the current failure.
