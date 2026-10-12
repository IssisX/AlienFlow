# AlienFlow2 implementation checkpoint — 0.1.13

Repository: IssisX/AlienFlow. Branch: AlienFlow2. Date: 2026-10-12. VersionCode: 14.

## Owner request and release scope

Begin public-product polish. Settings must explain options to users of all skill levels, use a hierarchy and stop mixing complete transcripts into a long setup/settings screen. Preserve the accepted artwork and working dictation, including the Termux handoff repair from 0.1.12. This release changes presentation and navigation, not transcription transport or editor ownership.

## Implemented presentation

A native Home / History / Settings navigation bar stays available. Home contains a setup shortcut when needed, an actual dictation test field, coordinator status and a three-line latest-result preview. Full latest text has its own page. Settings is a six-category directory: Floating alien, Dictation & writing, Language & vocabulary, Privacy & saving, Account & setup, Help & about. Controls keep the existing preference keys/defaults and coordinator actions. Changes save automatically; ordinary preferences apply to the next session, appearance immediately. Language has named selections plus an explicit custom-code route. Vocabulary is limited to the existing 4,000-character guidance capacity and saved as typed. Troubleshooting/error-overlay control and diagnostics are under Help; account/permissions are under Setup.

History uses a native recycled ListView with two-line previews, dates and search over retained original/result text. Full text, copying, optional original comparison, status details and confirmed deletion have a separate transcript page. Recovery is a separate history subpage and preserves existing coordinator ownership, 24-hour retention, upload and no-new-field-insertion behavior. Clear-all, individual deletion, recording discard and key removal ask for confirmation inside the app. The existing JSON history storage still loads retained entries into memory; this is recycled view loading, not a paged database migration.

Presentation uses a dark charcoal palette, restrained lime accent, native controls with 48dp or larger touch targets, dp/sp sizing, readable spacing and a centered maximum 720dp reading width. Scrollable category pages support narrow windows and large fonts. System-bar, cutout and IME insets protect navigation and editable fields. The platform Back callback and in-app back button return to the actual entry page. Home draft, search, navigation stack and page scroll positions survive activity recreation. An unsaved API key is excluded from instance-state/autofill. The floating alien menu opens Settings directly, reusing an existing activity when possible. Accepted launcher/overlay artwork is unchanged.

Primary Android references: [View insets](https://developer.android.com/develop/ui/views/layout/edge-to-edge), [Back navigation](https://developer.android.com/guide/navigation/custom-back). No UI layer owns a second dictation or editor state machine.

## Verification contract

Required CI gates remain all native/unit regressions, release lint and release assembly. New activity tests must exercise real navigation, existing preference persistence, language/vocabulary saving, virtualized history rows/full-text search/copy/deletion confirmation, draft restoration, unsaved credential exclusion, the Settings intent and actual narrow/large-font/unfolded renders. Diagnostics export and the accepted readiness indicator render gates remain. Native rendered screenshots are emulator-framework evidence under Robolectric, not installed Fold6 proof. Build/signature/delivery results will be recorded after they run.

## Phone acceptance remaining

Install over the existing version without clearing app data. Check saved key/preferences/history survive; use every settings category; verify the six category pages and bottom navigation on folded/unfolded screens and with larger text. Search, open, copy and delete a transcript. Confirm the floating menu opens Settings. Start dictation in the test field, Termux and Chrome; verify focus handoff and the readiness indicator remain. This work cannot claim physical microphone, OpenAI account, keyboard or Fold6 acceptance from CI.

Prior checkpoint: history/IMPLEMENTATION_0.1.12.md.
