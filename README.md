# AlienFlow — isolated chat branch

Repository **IssisX/AlienFlow**, branch **AlienFlow(chat)**. This branch preserves the prior chat repair and integrates **AlienFlow2 0.1.19** at `3be1cd2b5bac1206ad3f9025b64d1b55e5c2eac1`. Other branches are not modified.

## Candidate: 0.1.20-chat.1 (21)

Termux uses completed, corrected, once-only insertion after Stop. The historical premature-write repair is retained, not reimplemented. Ordinary fields retain 0.1.19 live editing and preservation of extra IME characters outside the allowed range. Control-only terminal output no longer consumes a text delivery or inserts a blank fragment.

The installed-Termux test now covers Termux → ordinary live dictation → Termux again, using real Android accessibility/keyboard IPC and actual screen touches. Recognition events are fixtures, not proof of a microphone or OpenAI session. Build and test results must be recorded before this candidate is described as verified.

The accepted icon, settings, history, key storage, permissions and transport are preserved. A signed versionCode 21 APK can update the existing package without uninstalling or clearing data. Never install the unsigned Actions intermediate.

Read [current checkpoint](docs/IMPLEMENTATION.md), [the historical Termux diagnosis](docs/CHAT_TERMUX_REPAIR.md), and [remaining Google Play release gates](docs/PLAY_RELEASE_READINESS.md). Source/build reports do not certify the owner's Fold6, keyboard configuration or store readiness.
