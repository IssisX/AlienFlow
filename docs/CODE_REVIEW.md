# AlienFlow first test APK — independent code review

Date: 2026-10-10. Reviewer: /root/review_first_apk.

## Verdict and exact scope

**No remaining blocking or important source defect was identified in the final reviewed candidate for a first test APK.** The lead subsequently supplied successful exact-source CI4 and APK-signing evidence, recorded below. This verdict does not establish physical installation, microphone/editor behavior, provider account access, or completion of all product requirements. The two minor follow-ups below and the disclosed first-APK limits remain.

- Baseline main: `773aa09b99fbc9bf5e906158f05ad1d9706cb861`, README only.
- Reviewed candidate: `c76b3ccffd7b3072da1a179ebf5f19b39cc7238e`.
- Reviewed Git tree: `c5eed8056f59e16399da70f06bd49b18714a9aa8`.
- CI4: [Actions run 38058270344](https://github.com/IssisX/AlienFlow/actions/runs/38058270344), subsequently reported successful by the lead: 36 passing tests, release lint with zero errors, assembly, packaging and upload. Signing outcomes supplied by the lead are recorded below and in its final handoff.
- Final read-only comparison checked all 44 tracked files against the reviewed commit. No worktree content differed. SHA-256 values are recorded below.

Read AGENTS.md, PRODUCT_SPEC.md, IMPLEMENTATION.md, PROTOCOL.md, all eleven app Kotlin files, both test files, manifest/resources, every Gradle/wrapper input, workflow, packaging script, and build/app/audio/release/device documentation. Re-read every changed source file and all added resources before this verdict. The app owner's unfinished initial MainActivity was not itself treated as a defect.

This reviewer changed only this report. The reviewer did not modify app/build/docs, commit, push, dispatch workflows, sign, run paid APIs, or use a physical device. Local syntax/resource checks were independently executed; CI results and the audio owner's Java reproducer are explicitly attributed below. The findings were communicated as they were found to the native owner and lead, with storage findings shared with the audio owner.

## Important findings resolved in the reviewed source

The original interim review required changes. The following repairs have now been inspected in the actual files, rather than inferred from implementation-report prose.

| Finding | Current source evidence and minimal repair inspected | Verification boundary |
| --- | --- | --- |
| R1. Native-resource creation/disposal and stale callbacks could race; terminal close could leave client-owned capture running. | RealtimeTransport.kt:18–38,50–140,179–198 serialize native lifecycle on Main, isolate HTTP on IO, check closed/capture/connection generation after suspension and in posted callbacks, and stop both native and independent client recording demand. Samples are copied before asynchronous message dispatch. PCM16 format and microphone state/error callbacks are checked; DC CLOSED and ICE failure trigger interruption. | CI3 compiled the pinned API calls. Actual microphone release, one-ADM reconnect behavior and OS interruptions still need the phone gate. |
| R2. Intentional editor detachment on disconnect was interpreted as target loss by the next monitor tick. | DictationCoordinator.kt:88–97,231–239 and EditorBridge.kt:86–88 preserve a separate detached target baseline. Local capture is no longer stopped merely because the live-writing editor is null. Real target change or screen unavailability still stops work. Peer replacement retains the shared ADM. | Static repair inspected. Disconnect during an asynchronous editor write is still an important device/replay case. |
| R3. Unproven startup/final audio coverage could be treated as success and the WAV deleted. | RealtimeTransport.kt:100–110 delays initial microphone demand until DC OPEN. AudioCoverage in DictationLogic.kt:6–22 records outgoing commit IDs, rejects duplicate acknowledgement consumption, and marks coverage uncertain on any empty-buffer rejection. RealtimeTransport.kt:161–188 marks uncertainty for failed/not-ready commits and disconnects. Coordinator error/finalization paths retain that source. | Targeted empty-commit and duplicate-ack tests are in the 14-case logic suite. A 350 ms media drain is still a runtime hypothesis, not RTP delivery proof; see integration limits. |
| R4. An all-zero header until orderly close made process-death recordings invalid; audio deletion could precede durable result storage. | PendingAudio.kt:132–176 creates and syncs authoritative PCM16 metadata before payload, updates lengths while writing, repairs actual on-disk length, and closes lock/channel even after errors. Coordinator.kt:191–228 saves text before ordinary audio deletion, reports failed deletion, and retains audio on storage failure. | Real-file fault and process-death-header tests passed CI3. Power loss cannot preserve queued/unsynced bytes; incomplete terminal frames are deliberately rejected without truncating the source. |
| R5. Recovery used the wrong language field and a whole unbounded WAV upload. | OpenAiClient.kt:55–59 now sends multipart `languages[]`, fixed filename `recording.wav`, and WAV MIME. PendingAudio.kt:246–293 streams ordered, frame-aligned parts with a default 20 MiB payload; temporary parts are removed in finally and the original is retained until complete result storage. | Actual long-file, ordering and failed-second-part tests passed CI3. The provider request itself and recognition quality at chunk boundaries remain untested. |
| R6. Finals arriving before predecessor acknowledgements could leave displayed/saved text in completion-arrival order. | TranscriptLedger in DictationLogic.kt:25–52 treats later predecessor evidence as a revision. Coordinator.kt:107–112 renders acknowledgement/predecessor updates and waits for committed-item and completed-item coverage before normal finalization. | Reverse finals, delayed predecessor and duplicate-event logic tests passed. Cross-process/provider event replay is not established by those pure logic tests. |
| R7. Password masks were class-insensitive; a nullable second read and delayed first-write flag could corrupt newer ownership state. | EditorBridge.kt:18–25 uses class-aware text/numeric password masks before explicit text reads. It takes one guarded write snapshot and checks captured generation/owner identity before delayed confirmation mutates state (57–78). Accessibility InputMethod start/finish invalidates ownership; selection callbacks track composition (AlienAccessibilityService.kt:30–43). | Pure ownership tests plus compilation passed. Actual editor IPC and keyboard interaction remain a device gate. |
| R7a. A freshly focused empty field may have no initial selection callback, so composition-unknown could reject every first dictation. | EditorBridge.kt:47 accepts only a full empty field with selection 0,0 as safe initial no-composition evidence. Unknown nonempty composition remains conservative. The final flags argument is the named `InputConnection.GET_TEXT_WITH_STYLES` constant, fixing CI3's lint error without changing semantics. | Independently checked against AOSP startup/update-selection behavior. No assumption that missing non-Parcelable composing spans proves safety. Empty/nonempty Gboard behavior still requires phone testing. |
| R8. B normalization could accept changed decimals/paths/commands; incomplete cleanup or chunk splitting could lose source content. | CorrectionGuard in DictationLogic.kt:55–94 preserves signs, decimal/path/identifier punctuation and apostrophes, and rejects unwanted negation/profanity/quantity edits. CleanupChunks preserves separators and does not split long identifiers or UTF-16 pairs. OpenAiClient.kt:47 rejects any cleanup response without completed status. C adds protected-token checks, while remaining explicitly a rewrite mode. | Numeric, apostrophe, path, profanity, negation, long-token and Unicode cases passed CI3. These checks do not prove semantic equivalence for arbitrary C output. |
| R9. Duplicate/stale recovery jobs, index-based history deletes and active-file deletion lacked stable identity. | Coordinator.kt:57–60,265–289 makes recovery single-flight, blocks a new capture while it runs, guards result publication by session, and protects owned audio. LocalStore.kt:52–73 uses history IDs and excludes current/recovering audio from pruning. MainActivity.kt:76–86 deletes by stable ID and rechecks audio ownership. | Static integration inspected. Repeated recovery taps, history refresh during capture and changing focus during recovery remain focused phone cases. |
| R10. History Off or an empty recovery result could delete the recording without a durable replacement. | Coordinator.kt:281–286 rejects wholly blank recovery and uses nullable saveResult as the actual durability decision. When history is disabled, the source remains and the status asks for copy/manual discard. Failed live insertion with history disabled also retains audio (196). | Static branch review; test with history disabled and with injected result-storage failure. Minor preference-snapshot follow-up is noted below. |
| R11. Cancel could arrive between an atomic history checkpoint and the queued Main completion, leaving a deliberately canceled record. | Coordinator.kt:198–228 wraps both disk work and Main acceptance in one finally. Only serialized terminal acceptance sets accepted=true; otherwise the exact checkpoint ID is rolled back in NonCancellable IO. Rollback failure is surfaced. Cancel increments the session before delayed status/undo and deletes audio in finally even if close reported a saved storage error (245–260). | The cancellation handoff was inspected statically. It needs a controlled Main/IO scheduling test or device exercise; it is not covered by a claim that every coordinator interleaving has been unit tested. |
| R12. Canceling a blocking multi-chunk cleanup coroutine could still launch all later paid chunk requests. | cleanChunks in DictationLogic.kt:109–121 checks coroutine activity before and after each provider request. Recovery also checks between parts. The coordinator uses this function. | The deterministic cancellation-during-chunk-one test passed CI3 and asserts one request only. The request already in flight is still blocking and is not wired to OkHttp Call.cancel. |
| R13. Screen-lock stop was inferred only from focus changes. | AlienApp.kt:16–23 registers SCREEN_OFF and checks PowerManager plus Keyguard. Coordinator start/monitor/screenUnavailable and EditorBridge.snapshot explicitly refuse/stop while unavailable, including the detached-capture path. | Static checks and compilation passed. Device must demonstrate microphone release on lock/sleep and no late editor write. |
| R14. Ordinary executor shutdown could interrupt FileChannel finalization. | PendingAudio.kt:59–71 saves/clears an inherited executor interrupt for owned termination I/O, restores it, then signals completion. Real storage exceptions remain failures. The original failed test was not weakened; a deterministic interrupted-final-worker regression was added. | CI2 exposed ClosedByInterruptException. Audio owner reproduced the mechanism on Java 17.0.20: old path failed; repaired path finalized and restored interruption. CI3 passed the original and new Kotlin regression. |
| R15. Android backup/transfer exclusion needed explicit modern rules. | Final manifest line 7 references data_extraction_rules.xml while keeping allowBackup=false. Both cloud-backup and device-transfer exclude all nine documented storage domains. Original adaptive launcher icon and @string/app_name also resolve the concrete manifest/resource warnings selected by the lead. | Independently parsed all eight manifest/resource XML files and asserted the exclusions. The lead subsequently reported CI4 AAPT/release lint passed. Actual OEM transfer behavior was not directly exercised by this reviewer. |

The 100 ms editor confirmation delay is not treated as an atomic compare-and-swap with another process. A failed/unavailable readback stops further automatic edits; it never authorizes a blind retry or uncertain undo. No Send/Search/IME-action or key-submission call was found in the editor path. The selected keyboard is not programmatically replaced.

## Minor follow-ups

These are concrete follow-ups, not reasons to withhold a controlled first test APK.

### M1 — Freeze language for a multi-part recovery job

DictationCoordinator.kt:274–278 reads `store.language()` inside each upload callback. MainActivity.kt:108 says choices apply to the next session. Changing language while a long recovery is in progress can therefore change the hint between parts of the same recording.

Minimal fix: capture recovery language once when recover(file) begins and pass that immutable value to every part. Verify with a two-part fake recovery that changes preferences after part one; both requests should retain the original hint. Avoid a paid test for this deterministic preference behavior.

### M2 — No-frame startup stop can claim that audio was saved

PendingAudio deliberately creates no file without nonempty callbacks. In Coordinator.kt:191–219, an early Stop/failure before microphone frames can still follow keepAudio=true and report “audio saved for recovery” even though there is no recording to list.

Minimal fix: derive a hasCapturedAudio result during IO finalization and choose a no-audio status when false. Do not fabricate a WAV to satisfy the message. Verify Start followed by immediate Stop before secret/SDP/DC completion: mic stays off, no upload occurs, no phantom recovery is reported.

## First-APK limits and outstanding product requirements

The following remain explicit limits even if CI4 passes. They must not be relabeled as completed requirements merely because an APK is available.

- **Reconnect is real but live gap recovery is incomplete.** The same microphone is retained and peer connection retries happen automatically. Once a gap occurs, automatic editor writes detach; the full recording is transcribed after Stop and preserved for manual copy. There is no live PCM gap replay, automatic resumption of in-field editing, or persistent per-part recovery checkpoint. A failed late chunk can cause earlier chunks to be uploaded again on retry.
- **Provider readiness and trailing media need measurement.** The implementation begins capture at DC OPEN and uses explicit commit acknowledgements/finals. It does not validate a session-ack event before starting, positively measure remote RTP coverage, or avoid every no-speech final commit. Empty/rejected/failed coverage is now retained conservatively. Test the actual transcription-scoped SDP handshake, live deltas, multiple turns, early stop and final words before claiming reliable live transcription.
- **Editor cooperation is required.** Complete matching text/selection, offset zero, stable focus and known composition are mandatory. Nonempty fields with unknown composing state and oversized/truncated/inconsistent readback are refused. AOSP composition finishing and readback timing on the user's Gboard/Fold6 still require device proof. This is not universal input-box compatibility.
- **Some accepted preferences are not implemented.** Local image/alien variants, edge snap, completed-audio history, pending-retention choices beyond 24 hours, usage/cost overview, per-entry compare/restore, and seamless live gap replay remain absent. Existing controls are wired; absence is generally disclosed in app/docs. The first test APK is not full specification acceptance.
- **Cancellation has a network boundary.** No new cleanup part starts after cancellation is observed, and stale results cannot regain editor ownership. A blocking request already sent can continue until the HTTP call returns/times out; there is no explicit recovery-cancel control. Provider-side work already accepted cannot be assumed revoked.
- **Storage is bounded where required but not unlimited.** Audio queue/callback bytes are bounded, uploads stream, and successful/retained sources follow separate deletion paths. Standard RIFF's size ceiling, available disk, process death before queued bytes reach disk, and nonrecoverable partial terminal frames remain explicit resource limits. These are not an application word quota. Expiry occurs when stored data is accessed, not via a continuously running deletion service.

## Evidence and tests still warranted

### Observed build evidence supplied by the lead

| Exact revision | Actual observed outcome |
| --- | --- |
| 659088d25082f70ddd65dca1be2fbb1cd45a6cb4 — run 38057134203 | SDK37 package installation failed before app compilation/tests/lint. API36 was then selected from published stable SDK metadata; build checks were not weakened. |
| cf391cd212df6596d709ba06d148653d842771ff — run 38057441585 | App compilation succeeded; 32 tests ran with one PendingAudio finalization failure. This was not a passing release. |
| 33ba2d8f3f7f5d40747bfe532d1af43ea52a530e — run 38057983963 | Compilation, 36-case unit-test task (14 logic + 22 storage) and release assembly passed. Lint failed on one WrongConstant error and reported 19 warnings. Packaging/upload were skipped. |
| c76b3ccffd7b3072da1a179ebf5f19b39cc7238e — run 38058270344 | Final named-constant and manifest/resource revision reviewed here. Lead reported compilation, 36 tests with zero failures/errors/skips, release lint with zero errors and 18 retained warnings, release assembly, package verification and artifact upload all passed. |

The lead then reported that all delivery-bundle entry hashes were verified and the APK was signed with the permanent key. apksigner verification succeeded with V3 and one signer. This reviewer did not independently perform signing or installation. Supplied artifact identity:

- Unsigned delivery artifact ID: `11672260565`; SHA-256 `bc881f7d35c63017d903439eb94234f4a8a147b44b6b53fddb9c378c910c453f`.
- Signing certificate SHA-256: `d79e74b3a4c24ee225d1220c2c374c6f958520d4f3257fcfb399ff09bf5c965f`.
- Final signed APK SHA-256: `79e34f8abf7ddd8dce399b9742d55c6dc028a1d2984abb8c1dfa2bf7b516ed21`.

The reviewer independently performed Python AST/TOML/YAML structural checks, action-pin/permissions inspection, XML parsing, wrapper ZIP/hash checks and shell syntax checks during review. The final XML/resource changes were rechecked. A YAML parser is not an Actions schema execution service; the build owner's upstream schema evidence is recorded in BUILD_EVIDENCE.md, and the official GitHub changelog independently supports the configured non-zipped single-file artifact option.

No build correctness blocker remains in the inspected inputs. Compile/target36, min33, Build Tools36.0.0, package/version and ARM64 policy agree between Gradle and packaging. The workflow is read-only with pinned actions and checksum-checked wrapper, builds unsigned, and requires test/lint/APK/source identity/native alignment gates. Packaging records the exact source SHA and checks completely unsigned state before private signing. Direct inputs are pinned; transitive metadata and hosted-runner inputs are not claimed hermetic.

### Focused remaining checks

1. **Cold phone start:** activity closed, no warm FGS, Gboard selected, empty native field and browser textarea. Starting must become Listening only with real PCM; live text must appear without selecting another keyboard.
2. **Editor interference:** select a span with fixed prefix/suffix; move focus to an identical-looking second field during an in-flight update, type manually, move the cursor, and lock the screen. No late write may reach the new field or remove user text. Test initial empty and nonempty composition separately.
3. **Capture lifecycle:** cancel during secret fetch, offer, SDP answer and first frames; repeat start/stop; force microphone permission/service interruption. Check actual Android mic indicator and callback cessation, not only coordinator flags.
4. **Audio coverage:** no-speech Stop; immediate Stop; Stop just after phrase commit; final spoken word at Stop; send failure; final-before-ack and duplicate error replay. Retain any source whose submitted/acknowledged coverage cannot be established.
5. **Disconnect/recovery:** drop network during speech and during an editor write, keep speaking through several monitor ticks, reconnect, then Stop. Prove one microphone, full retained file, chronological complete recovery, no insertion into a changed field, and no source deletion on failed result storage or history-disabled recovery.
6. **Cancellation/checkpoint race:** hold the Main completion after durable history save, then Cancel. The exact canceled entry and source must be discarded; a later successful session must remain. Inject rollback/delete failure and verify a visible retained-failure message instead of a crash.
7. **Installation/update:** the lead reported CI4/package/signature/hash checks passed. Install the exact signed APK on Fold6, check the actual package/version/certificate, and prove a subsequent same-key update retains the intended data. Physical installation and update behavior remain unverified.

Tests above are warranted by concrete seams and remaining proof gaps. They are not asserted passes, and this review did not authorize or perform any paid provider test.

## Primary sources used for uncertain platform/API facts

- [Android InputType](https://developer.android.com/reference/android/text/InputType): password variation requires the class bits as well.
- [AOSP accessibility InputMethod](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/accessibilityservice/InputMethod.java) and [InputMethodManager](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/inputmethod/InputMethodManager.java): ordinary start does not guarantee an initial selection callback; selection dispatch is conditional on change.
- [AOSP BaseInputConnection](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/view/inputmethod/BaseInputConnection.java) and [TextUtils](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/text/TextUtils.java): standard composing marker and parceling behavior do not make absent returned spans proof of no composition.
- [OpenAI file transcription guide](https://developers.openai.com/api/docs/guides/speech-to-text): plural language hints and provider file-upload bound.
- [OpenAI Realtime client events](https://developers.openai.com/api/reference/resources/realtime/client-events): commit acknowledgement, optional client event IDs and empty-buffer rejection.
- [Android PowerManager](https://developer.android.com/reference/android/os/PowerManager) and [Auto Backup](https://developer.android.com/identity/data/autobackup): interactive-state distinction and explicit cloud/device-transfer rules for the modern target.
- [GitHub artifact changelog](https://github.blog/changelog/2026-02-26-github-actions-now-supports-uploading-and-downloading-non-zipped-artifacts/): single-file non-zipped artifact support. Pinned native interfaces/publication details are separately documented with primary links in PROTOCOL.md and BUILD_EVIDENCE.md.

## Exact reviewed content hashes

All paths below are relative to the AlienFlow repository. The table uses SHA-256 of the contents of commit c76b3ccffd7b3072da1a179ebf5f19b39cc7238e, independently compared with the worktree after recheck. Any later app/build/resource change needs a new review and exact-source gate. Documentation-only evidence updates may record later outcomes without pretending they were known at this cutoff.

| File | SHA-256 |
| --- | --- |
| `.github/workflows/android-release.yml` | `08d1fe923ee3edf7577fe36e56276cee03a262a5a557ea664d1e4741517751da` |
| `.gitignore` | `8a22d677525400b6030909663e4822efcdf3da298fa6c90a904e0ad32f8ba16a` |
| `AGENTS.md` | `545c4548f969760cdb6e214db2640543c751fc3ad979622b6e8aaba95af20482` |
| `app/build.gradle.kts` | `d0a040358ed40a040eb13601d75b89a5bf9e6ed2b83241a0bf1b35c39ba85626` |
| `app/src/main/AndroidManifest.xml` | `28fa4a895515df11064953fa06385777e5f96453ca7583f78281582ca030dae4` |
| `app/src/main/java/io/github/issisx/alienflow/AlienAccessibilityService.kt` | `d98b39dfa50bc55dd7c86a66e4ae4ee1ec009081d2f85fdc7e80e5e53090cd43` |
| `app/src/main/java/io/github/issisx/alienflow/AlienApp.kt` | `df63411247eb4e92c3cf74ac8aa6ab8729c17eae0e35aa6a4bdab0b688c06621` |
| `app/src/main/java/io/github/issisx/alienflow/CaptureService.kt` | `800ed89448bc23ebe738b6c3131ff06ff9a111b69ee802930df9b99e1f6788fa` |
| `app/src/main/java/io/github/issisx/alienflow/DictationCoordinator.kt` | `53b0a3bd02759f47fb16709bc9ff7874ade4a8ba9c35011f94aee860b9ba0d2e` |
| `app/src/main/java/io/github/issisx/alienflow/DictationLogic.kt` | `7a895c4a9f29a454625f61878f954146c0c96edd6e6457cae6285ccd511ee177` |
| `app/src/main/java/io/github/issisx/alienflow/EditorBridge.kt` | `99fcb965e89fc7ff75448d6ef8dcf1635ecb9f34d59e8e6842f4c869aab13b99` |
| `app/src/main/java/io/github/issisx/alienflow/LocalStore.kt` | `b1b33666992ad16cce4fba19756ec814daefb2fbc4366f2dcfe27b6c27883dab` |
| `app/src/main/java/io/github/issisx/alienflow/MainActivity.kt` | `5113b5c2d7e1918de6d7d55e7efc1b859e1f2d3b131721c7feb692330c0bb653` |
| `app/src/main/java/io/github/issisx/alienflow/OpenAiClient.kt` | `b7db112928cf0929dbcafc87a7056fcbe22533e7658bb56c3130267f99656d6f` |
| `app/src/main/java/io/github/issisx/alienflow/PendingAudio.kt` | `a48ecaa07e2b5431aee42092db551ac6bd5501bd9c5139641c29637bfc32d083` |
| `app/src/main/java/io/github/issisx/alienflow/RealtimeTransport.kt` | `6f98e2881f6c7c015ed6e8bfae3e8455997b8545ce326fcb30d9d1422931ba00` |
| `app/src/main/res/drawable/alien_icon_foreground.xml` | `45ce95e871f279ea51e895a8914a96c3085b456a0738116936d415a14dc120d0` |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | `574e446a52b0bd8551757200745e3b84053dce0a8b4385ed00109e8050b43aaa` |
| `app/src/main/res/values/colors.xml` | `65a9d1650b4c8854b6fa76e59d4bf74ffdbf77c2f48cae0ebe15f255fdb3baea` |
| `app/src/main/res/values/strings.xml` | `d8aa7fcb6aee8b579a02b2b3fdd7ab10281816856b99cae8a27219da50bddfd2` |
| `app/src/main/res/values/styles.xml` | `fe636099f5ffa556e66d74a54925a9254688693542dbdf29ac105801cf0a1a80` |
| `app/src/main/res/xml/accessibility_service.xml` | `dd27f1bc822c65e8f3a1280391d1292e16395ec3fdee211802e787288ec4e7c8` |
| `app/src/main/res/xml/data_extraction_rules.xml` | `cb029b35db0e976c087100424e16e14a6256b46962398887520e51508c3b5842` |
| `app/src/test/java/io/github/issisx/alienflow/DictationLogicTest.kt` | `17c91acd12ae7c69d60a7d71c76273f74cff72a1653b8be257770948dc942610` |
| `app/src/test/java/io/github/issisx/alienflow/PendingAudioTest.kt` | `fe2fb0707fa1419d972a186e485644deb39e362d3a63d816f620e6ae0317a03c` |
| `build.gradle.kts` | `8ce5660d95ace9901683968718cecfed6e8165dbd853ee67961c00cbcbe2a972` |
| `docs/APP_IMPLEMENTATION.md` | `4ba792d681eb0ecf7028cec48bd580b76ddfcd3d16698fc070bae5228a2a2854` |
| `docs/AUDIO_STORAGE.md` | `52816147aedacc56dd63bb36cf55b351361766a14774e1ff994fedc3f67a889f` |
| `docs/BUILD_EVIDENCE.md` | `9965c231bc62cb8360e66d582a70da5e60dd0e2b3599a0e434e75ffff7d4c35b` |
| `docs/DEVICE_TESTS.md` | `c7dd26c2b3cc0f384b7d6981297ce4e228c97a3509515e715346581c5e92ebf6` |
| `docs/IMPLEMENTATION.md` | `8da35c7ace6ec94fb347852d0de8e7936a39ef3220777b6dde643eeb8e0c460a` |
| `docs/PRODUCT_SPEC.md` | `4548fa598e41b13fcfd462f548648ba6c524bbe8db7cbe30f13fb307118efed6` |
| `docs/PROTOCOL.md` | `c14bd39663a38c2010d497a38968e85db58e9ccb77bc1da73b3f5b0ccd7bd215` |
| `docs/RELEASE.md` | `cdd675f47cf21998ad13e5e4fb42ef37c77604ec618b15d10e26d9173246b087` |
| `gradle.properties` | `d82940fdbfc2da3b86a72a15aa1520144adbcd7b028ec731222fedb3c4cfd4fb` |
| `gradle/libs.versions.toml` | `beac96c13ced8d1104b6b7b566787177ca499ef0f9001e79bee382fd62dd5e79` |
| `gradle/wrapper/gradle-wrapper.jar` | `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7` |
| `gradle/wrapper/gradle-wrapper.properties` | `f34e21a981617b8ab59ecbb41b541b35dcf724e01c246e1c0fc8c5420cb66ec0` |
| `gradle/wrapper/LICENSE` | `0d542e0c8804e39aa7f37eb00da5a762149dc682d7829451287e11b938e94594` |
| `gradlew` | `e01b5c97892572c82405c02b96a3382379100e7d825ce7c48883d95c26928750` |
| `gradlew.bat` | `4e193538b4ddccbee0c05d6383731b647945c146d16d8fe243b4ff716867896f` |
| `README.md` | `f42a9ea5bc963c780f3c1721c6da29d84a82621d8642f540725f29a1368c338a` |
| `scripts/package_release.py` | `87d4b2a7a48cccb6ffa5dd65777cba79a9d14b73f8cf3e9a3d72b979d2a1bb26` |
| `settings.gradle.kts` | `77439dc5959eef4d9530762a914ae6bd7bc6779b57e284c81da6051febf24bb6` |
