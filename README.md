# AlienFlow — isolated chat branch

Repository: **IssisX/AlienFlow**. Working branch: **AlienFlow(chat)**. This branch preserves the previous chat work and merges the complete **AlienFlow2 0.1.19** source at `3be1cd2b5bac1206ad3f9025b64d1b55e5c2eac1`. AlienFlow2 is a branch in this repository, not a separate repository. No writes were made to AlienFlow2, main or build/first-apk.

## Signed development update: 0.1.20-chat.1 (21)

Install the signed APK delivered in the development conversation over the existing AlienFlow installation. Do not uninstall or clear data. The original package ID and signing certificate are retained. The unsigned intermediate inside the GitHub Actions bundle is not an installer.

Termux receives the completed transcript once after Stop, without Enter or control keys and without replacing existing terminal input. The existing premature-write fix is preserved. A new guard prevents control-only output from becoming a blank terminal fragment or consuming the one delivery. Ordinary fields retain 0.1.19 live revisions, including its IME-only suffix preservation fix. The microphone, network/model choices, accepted icon, opacity, settings, history and key storage were not redesigned or migrated.

## Actual verification

[Actions run 38398347454](https://github.com/IssisX/AlienFlow/actions/runs/38398347454), compiled source `683ab2dcc5902d41ed5b283f08d6a8054def1b2d`: **198 unit tests passed**, no failures/errors/skips, release lint zero errors/38 warnings, and the installed-Termux Android 16 editor integration passed. The integration uses real screen taps, Android accessibility IPC and official Termux v0.118.3. It checks delayed final delivery, no partial/duplicate terminal input, an ordinary field with live updates, and a second Termux dictation after switching back without resetting accessibility.

Recognition callbacks and the inactive audio transport are explicit fixtures. This proves editor integration under the tested emulator conditions, not the owner's physical microphone, OpenAI account, Gboard, tmux/Codex or Fold6. An initial coordinate-only tap harness failed on the return transition; the final test verifies the actual topmost rendered touch target before tapping once. No application behavior assertion was removed.

The downloaded artifact and all 157 manifest member hashes were checked. The final APK signature matches the original certificate and all 25 application payload entries are unchanged after signing. The packaged icon and relevant settings/history/indicator renders were inspected. See [release verification](docs/RELEASE_VERIFICATION_0.1.20-chat.1.json), [signer output](docs/APK_SIGNATURE_0.1.20-chat.1.txt), and [current checkpoint](docs/IMPLEMENTATION.md).

## Phone check

With the usual keyboard selected, focus Termux, tap the alien, speak, and tap Stop. Text should arrive once without Enter. Switch to an ordinary text field, verify live words, then return to Termux for a second dictation. If a failure remains, open **Settings → Help & about → Diagnostics / share report** and provide the fresh version-specific report. Old native WebRTC exits are not evidence of a new failure.

## Google Play status

This update is not submitted or store-ready. [Paid-release readiness](docs/PLAY_RELEASE_READINESS.md) records the remaining disclosure/consent, accessibility/foreground-service declarations, public privacy policy, Data safety, account/cost model, App Bundle/signing and real-device testing gates. No Play Console action, public credentials, billing integration or telemetry was added.
