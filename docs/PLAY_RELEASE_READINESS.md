# Paid Google Play release: remaining gates

Reviewed 2026-10-13 against the current source and official Android/Play documentation. This is an engineering checklist, not a claim of approval or a public privacy policy. No Play Console submission, account connection, price selection or publication was performed.

## Keep the working product intact

The accepted silver alien/dual-border asset, quiet completion, hierarchical settings, live appearance preview, searchable history and capture-ready indicator remain. The current release work prioritizes actual input reliability; do not replace these with another untested design. Termux's one-shot insertion after Stop must be explained consistently in Help and the listing. Do not advertise universal live revision in every terminal.

## Blocking publication work

**Accessibility disclosure and affirmative consent.** The app currently opens system accessibility settings from Account & setup. Before public release, implement and test a distinct in-app disclosure in the normal enablement flow, describing focused-field/window data access, local text-preservation checks, and any actual sharing. Record affirmative consent before use; handle declining and revoking it without breaking the app. Do not claim the app primarily serves disabled users merely to obtain an exception. Complete the Play Console accessibility declaration and demonstration video. Current source is not certified compliant by having an Android permission toggle.

**Foreground-service declaration.** The manifest uses microphone and specialUse for capture, delivery and a user-visible Ready control. Prepare the corresponding Play Console description and demonstration, including idle Hide, microphone-only-after-tap, Stop, screen lock and permission loss. Special-use acceptance is subject to review; do not promise approval or hide the notification to make it look quieter.

**Privacy and commercial onboarding.** Publish an accurate accessible privacy-policy URL and link it in-app and in the listing. Complete Data safety according to actual data flow: dictated audio, transcript processing, optional vocabulary, local history/recovery and user-initiated diagnostics. Decide the public account/cost model explicitly; do not ship developer-owned keys or imply that buying the app includes unlimited third-party API usage. A policy must describe the actual shipped product, not aspirational behavior.

**Store artifact and release validation.** Prepare the Android App Bundle and Play App Signing/update-key plan while preserving the existing installation certificate for development updates. Validate installation/update, device compatibility, lifecycle, battery, latency and recovery on real phones and a closed test track before paid release. Device/keyboard and app-editor results need separate evidence from injected recognition tests. Current CI produces a signed-development-APK delivery path, not an approved store release.

## Official references checked

- Accessibility API and consent: https://support.google.com/googleplay/android-developer/answer/10964491?hl=en
- Foreground-service declaration and review: https://support.google.com/googleplay/android-developer/answer/13392821?hl=en
- User data, privacy policy and disclosures: https://support.google.com/googleplay/android-developer/answer/10144311?hl=en
- Android App Bundles: https://developer.android.com/guide/app-bundle

No extra runtime permissions, telemetry SDKs, billing backend, settings switches or public credentials were added as a substitute for this work.
