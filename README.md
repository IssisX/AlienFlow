# AlienFlow

Personal Android dictation with a movable alien bubble, live text in the focused field, very light corrections, and your own OpenAI API key.

## Current signed update: 0.1.5 (6)

This update fixes the editor lookup so it follows keyboard input focus across windows instead of the currently touched window, which may be AlienFlow's bubble. It also detaches the accessibility service before terminal UI updates during teardown. The original phone shutdown is not declared resolved: actual device acceptance remains open.

**Diagnostics / share report** is now directly below the version on the landing screen and in the alien's long-press menu. The dedicated screen offers Share, Copy, Save and Refresh. Its report includes the installed version/source, permission and service state, recent session-stage events, handled/uncaught Java failure locations and Android's recorded process exit reasons. When Android retains a native crash trace, only signal and code-location frames are extracted. It does not share API keys, dictated text, audio, raw memory or arbitrary exception messages. Nothing is uploaded automatically.

The optional three-second microphone probe reads Android audio and reports sample counts without saving or uploading audio. It does not prove WebRTC or provider operation.

Both the bubble and launcher use the exact approved Gray alien image, scaled and encoded for the app rather than redesigned. The 75% idle opacity and existing sliders remain.

## Install and report

Install **AlienFlow-0.1.5.apk** over the existing installation. Do not uninstall or clear data. The application ID and permanent signing certificate are unchanged, with versionCode 6.

Open AlienFlow and confirm **Version 0.1.5 (6)**. Try one dictation. If it stops or exits, return to AlienFlow and tap **Diagnostics / share report**, then **Copy diagnostic report**, and paste the result into the development conversation. The same entry is in the long-press bubble menu. A report may also include prior Android exit records; absence of a trace is not proof of no crash.

The GitHub Actions bundle contains an unsigned intermediate APK. Install the separately delivered signed APK, not that intermediate file.

## Verified scope

[Actions run 38139391856](https://github.com/IssisX/AlienFlow/actions/runs/38139391856), compiled source `c289cb98f5d1dabffcc8eadf6fad98dbc586efc5`, passed **69 tests**, release lint with **zero errors and 29 warnings**, assembly, identity/native alignment checks and artifact upload. The actual main/diagnostic activities were rendered under Robolectric; the top entry and real Copy, Share and Save-intent handlers were exercised. The focus tests simulate Android's focus broker and do not replace a Samsung device test.

Downloaded bundle hashes, all 39 listed member digests, the packaged approved artwork, compiled source identifier, and the final APK signature were checked. All 26 original APK payload entries remained identical after signing; only signature metadata was added. [Release verification](docs/RELEASE_VERIFICATION_0.1.5.json) and [signature output](docs/APK_SIGNATURE_0.1.5.txt) identify the exact installer.

Physical installation/update, microphone capture, native transport and live typing on the Fold6 remain unverified. Passing automated checks is not full product acceptance.

## Product defaults and continuity

Movable Gray alien; 75% idle opacity; adjustable main behavior settings; existing keyboard retained; live in-field text and very light correction; wording, negation, numbers, slang and profanity preserved; seven days of text history by default; no AlienFlow word allowance. Provider, connection, file-format and device limits still apply. Existing history and key-storage formats were not migrated or reset in this update.

## Engineering handoff

Start with `AGENTS.md`, [product requirements](docs/PRODUCT_SPEC.md), [current checkpoint](docs/IMPLEMENTATION.md), and [current primary-source research](docs/RESEARCH_2026-10-11.md). The branch is `build/first-apk`; PR #1 remains the draft integration record pending device acceptance. The signing key stays outside the repository and CI. Prior release records and the verbatim [0.1.4 checkpoint](docs/history/IMPLEMENTATION_0.1.4.md) remain available.

The previous duplicate-microphone-demand explanation was not proven. The WebRTC fork explicitly coordinates independent/native recording demands; see the corrected research. Do not repeat that explanation as the established phone crash cause.
