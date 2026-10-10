# Android build path implementation report

**Verified delivery:** compile/target SDK 36 on stable channel 0. [Actions run 38058270344](https://github.com/IssisX/AlienFlow/actions/runs/38058270344) completed successfully for `c76b3ccffd7b3072da1a179ebf5f19b39cc7238e`: 36 tests passed, release lint had zero errors, assembly/package verification/upload passed. Private signing and downloaded artifact hashes were then verified. See IMPLEMENTATION.md and RELEASE_VERIFICATION.json. The initial SDK 37 configuration below is historical; the final section records the observed SDK failure and correction.

## Status

Implemented Android Gradle configuration, verified Gradle wrapper, and the GitHub Actions unsigned release bundle path. No commit, push, workflow dispatch, signing operation, private key, or repository signing secret was created by this agent. The lead subsequently established dependency resolution, compilation, unit tests, lint and APK/package verification through the successful run above. The original local syntax checks alone did not establish those outcomes.

The native source owner selected Android Views and direct encrypted/atomic JSON storage for the first APK, so the build intentionally has no Compose, Room, DataStore, KSP, or separate Kotlin Android plugin. The lead approved this implementation choice. App source remains owned by the source implementer.

## Exact toolchain and publication evidence

| Item | Configuration | Verified evidence |
| --- | --- | --- |
| Android Gradle plugin | `com.android.application:9.4.0` | Exact Google Maven POM fetched successfully; official release table lists Gradle 9.6.0, JDK 17, SDK Build Tools 36.0.0, maximum API 37. |
| Gradle | `9.6.0` | Upstream wrapper JAR and scripts fetched from `gradle/gradle` tag `v9.6.0`; distribution and wrapper checksums fetched from official distribution service. |
| JDK | Temurin `17.0.20+101` | Official Adoptium latest Java 17 Linux/x64 JDK endpoint returned this exact version. Exact setup-java provisioning succeeded in Actions. |
| Built-in Kotlin | Expected `2.2.10` | Exact AGP 9.4.0 POM declares `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10` and stdlib 2.2.10. Bundle metadata explicitly describes this as expected from the published AGP POM, rather than an independently reported resolved compiler version. |
| Android SDK | Compile/target `36`, min `33`, Build Tools `36.0.0` | Exact Google SDK metadata and successful Actions installation after the documented API 37 package failure. |

Primary source URLs:

- https://developer.android.com/build/releases/agp-9-4-0-release-notes
- https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.4.0/gradle-9.4.0.pom
- https://developer.android.com/build/migrate-to-built-in-kotlin — built-in Kotlin compilerOptions DSL and JVM target compatibility.
- https://services.gradle.org/distributions/gradle-9.6.0-bin.zip.sha256
- https://services.gradle.org/distributions/gradle-9.6.0-wrapper.jar.sha256
- https://raw.githubusercontent.com/gradle/gradle/v9.6.0/gradle/wrapper/gradle-wrapper.jar
- https://raw.githubusercontent.com/gradle/gradle/v9.6.0/gradlew
- https://raw.githubusercontent.com/gradle/gradle/v9.6.0/gradlew.bat
- https://raw.githubusercontent.com/gradle/gradle/v9.6.0/LICENSE — copied into `gradle/wrapper/LICENSE`.
- https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse

Verified SHA-256 values:

- Gradle 9.6.0 binary ZIP: `bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01`.
- Gradle 9.6.0 wrapper JAR: `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`.

The wrapper property enforces the ZIP checksum. The workflow independently verifies the checked-in JAR against its published checksum before executing it. `gradle/actions/setup-gradle` also performs wrapper validation and supplies the single Gradle cache mechanism. setup-java caching is not enabled.

## Dependencies

| Coordinate | Exact version | Evidence |
| --- | --- | --- |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | `1.10.2` | Exact Maven Central POM fetched. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | `1.10.2` | Exact Maven Central POM fetched. |
| `com.squareup.okhttp3:okhttp` | `4.12.0` | Exact Maven Central POM fetched; stable required Kotlin API surface. |
| `io.github.webrtc-sdk:android` | `150.7871.01` | Live Maven metadata; protocol researcher also downloaded exact AAR and inspected required public methods/native ABI. |
| `junit:junit` | `4.13.2` | Exact Maven Central POM fetched. |

Exact Maven URLs:

- https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-android/1.10.2/kotlinx-coroutines-android-1.10.2.pom
- https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-test/1.10.2/kotlinx-coroutines-test-1.10.2.pom
- https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp/4.12.0/okhttp-4.12.0.pom
- https://repo.maven.apache.org/maven2/io/github/webrtc-sdk/android/maven-metadata.xml
- https://repo.maven.apache.org/maven2/io/github/webrtc-sdk/android/150.7871.01/android-150.7871.01.aar
- https://repo.maven.apache.org/maven2/junit/junit/4.13.2/junit-4.13.2.pom

No dynamic direct dependency versions or floating action tags are used. Transitive resolution is still performed by Gradle using published metadata; no dependency lockfiles or full dependency-verification metadata have been generated because Gradle cannot run locally. The current configuration is therefore version-pinned at its declared inputs, not a hermetic build.

## Verified action commit references

Read-only `git ls-remote` against each official action repository established:

| Action | Release | Commit pin |
| --- | --- | --- |
| `actions/checkout` | v5.0.0 | `08c6903cd8c0fde910a37f88322edcfb5dd907a8` |
| `actions/setup-java` | v5.0.0 | `dded0888837ed1f317902acf8a20df0ad188d165` |
| `gradle/actions/setup-gradle` | v4.4.4 | `748248ddd2a24f49513d8f472f81c3a07d4d50e1` |
| `actions/upload-artifact` | v7.0.2 | `cf430e030ddbb5b0abf93d22962f4752f3646cd9` |

Important observed detail: Gradle Actions v4.4.4 is an annotated tag. Its tag-object SHA is `da187c8e6ffbd3802e00f2477aa5a822b25f2dda`; its dereferenced commit SHA is the `748248...` value above. The workflow pins the commit, and the pinned action.yml was fetched successfully. No invented action hashes are present.

Primary definitions:

- https://github.com/actions/checkout/tree/08c6903cd8c0fde910a37f88322edcfb5dd907a8
- https://github.com/actions/setup-java/tree/dded0888837ed1f317902acf8a20df0ad188d165
- https://raw.githubusercontent.com/gradle/actions/748248ddd2a24f49513d8f472f81c3a07d4d50e1/setup-gradle/action.yml
- https://raw.githubusercontent.com/actions/upload-artifact/cf430e030ddbb5b0abf93d22962f4752f3646cd9/action.yml
- https://github.blog/changelog/2026-02-26-github-actions-now-supports-uploading-and-downloading-non-zipped-artifacts/

The exact upload action definition supports `archive: false` for one file, uses that file's basename as the artifact name, and runs on Node 24. The Ubuntu 24.04 hosted runner successfully executed it and uploaded the verified bundle. A second redundant unsigned artifact is not configured.

## Workflow and gates

`.github/workflows/android-release.yml` supports `workflow_dispatch` and intentional pushes to `build/first-apk` filtered to code/build/workflow/script paths. There are no schedules or PR triggers. Permissions are `contents: read`; checkout does not persist credentials. All runs share fixed concurrency group `alienflow-first-apk`, with `cancel-in-progress: false`. The workflow timeout is 35 minutes.

Workflow build command:

```bash
bash gradlew --no-daemon --no-configuration-cache --console=plain --stacktrace \
  :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

`pipefail` preserves a Gradle failure when tee records human-readable output. GitHub job logs expose compilation, test and lint failures; no API key/signing secret is configured or required. Successful delivery requires all three gates. Lint errors remain fatal; no baseline or broad suppression is generated. Warnings are reported. Unit-test XML must exist and prove at least one actually executed passing test; absent/no-op reports fail bundle preparation.

## Release verification and delivery

App ID `io.github.issisx.alienflow`, versionCode `1`, versionName `0.1.0` are source controlled. Release signingConfig is null and debuggable is false. Only ARM64 native libraries are packaged for the Fold6.

`scripts/package_release.py` additionally verifies:

- Exact checked-out SHA equals `GITHUB_SHA`, with no changes to tracked source during CI.
- The expected unsigned release output exists.
- SDK zipalign produces and verifies 16KiB ZIP native-library alignment (`-P 16`, ordinary 4-byte alignment).
- APK badging reports exact application ID, version and SDK bounds, with no debuggable indicator; manifest output is retained.
- `lib/arm64-v8a/libjingle_peerconnection_so.so` exists and no other native ABI is packaged.
- Every packaged native library is ELF64 with PT_LOAD alignment >=16KiB and file-offset/virtual-address congruence modulo 16KiB.
- No JAR signature metadata or APK Signing Block exists; SDK apksigner reports the expected completely unsigned APK (`Missing META-INF/MANIFEST.MF`). Unexpected signing-tool failures stop delivery.
- Required actual unit-test and release lint reports exist and pass.
- The standalone SDK apksigner JAR and SDK tooling license notices are available for redistribution in the bundle.

The protocol researcher independently inspected WebRTC 150.7871.01's ARM64 ELF: three PT_LOAD segments have p_align `0x4000` with valid modulo-16384 congruence. See `PROTOCOL.md`. Packaged-APK alignment and the script's native ELF gate remain CI checks.

Single artifact file:

`build/delivery/AlienFlow-0.1.0-unsigned-release-bundle.zip`

Archive contents:

```text
AlienFlow-0.1.0-unsigned.apk
build-info.json
reports/apk-badging.txt
reports/apk-manifest.txt
reports/unsigned-verification.txt
reports/test-lint-summary.txt
reports/unit-tests/... (human-readable Gradle test HTML)
reports/unit-test-xml/... (JUnit XML)
reports/lint-results-release.*
tooling/apksigner.jar
tooling/NOTICE* (unmodified Android SDK Build Tools notices)
tooling/source.properties
tooling/README.txt
```

Build metadata records source SHA/run, package/version, SDK levels, release signing/debug flags, native ABI/ELF alignment, configured dependency versions, Java runtime output, expected Gradle/AGP/Kotlin/SDK versions, test/lint summary, and SHA256 for all other bundle files. The workflow summary records APK and ZIP SHA256. The JSON cannot include its own hash; the containing ZIP hash provides full bundle integrity.

Private signing example, after extracting the bundle and using the lead's existing private keystore outside the repository:

```bash
java -jar tooling/apksigner.jar sign \
  --ks /private/path/release.p12 --ks-key-alias alienflow \
  --out AlienFlow-0.1.0.apk AlienFlow-0.1.0-unsigned.apk
java -jar tooling/apksigner.jar verify --verbose --print-certs AlienFlow-0.1.0.apk
```

Use the actual key alias from the private keystore. Password options are omitted so the signer prompts privately. The lead owns actual signing, certificate identity verification, delivery, and installability checks. No secret belongs in the public repository, CI cache, logs, or artifact.

## Local commands and results

- Official AGP release page/POM, Gradle distribution checksum, Gradle wrapper checksum/scripts/JAR, Adoptium Java metadata, action tag references, exact upload action schema, and exact listed dependency POMs: fetched successfully.
- `sha256sum gradle/wrapper/gradle-wrapper.jar`: matches official `497c8c...` checksum.
- `python -m py_compile scripts/package_release.py`: passed syntax compilation.
- `bash -n gradlew`: passed shell syntax check.
- Python YAML parser: workflow parsed, exactly one job, every action reference has a full 40-character SHA. This is structural parsing, not an Actions execution test.
- Python tomllib parser: version catalog parsed and exact dependency versions inspected.
- `git diff --check`: passed for tracked edits; implementation files are new/untracked until lead integration, so this is not a comprehensive whitespace check for all new files.
- `./gradlew --version`: local permission denied on workspace file execute mode. CI invokes `bash gradlew` to avoid reliance on the scratch filesystem's executable bit.
- `bash gradlew --version`: wrapper starts successfully but Java's distribution download fails with `java.net.SocketException: Network is unreachable`. Did not bypass network policy or use an alternative release-assets host.

## Remaining limits

The SDK, javac toolchain, full Gradle distribution, and Android compilation are not available locally. The first real Actions run must establish toolchain availability and actual source/dependency compatibility, lint outcomes, report locations, native APK packaging, SDK signing-tool output, and artifact upload/download behavior. Any failure should be fixed using actual CI evidence without weakening identity/test/lint gates.

Hosted Ubuntu image and its SDK command-line manager are runner-provided mutable inputs; Java patch version, SDK requested package versions, action commits, Gradle/AGP, and direct dependency coordinates are pinned, but this is not a reproducibility claim. The SDK apksigner bundle is required to include its actual installed NOTICE file(s); absence fails rather than silently redistributing tooling without notices.

CI proves build/static checks only. Microphone access, provider account/model access, real WebRTC sessions, accessibility editor compatibility, long-session recovery, and signed APK install/update behavior still require device/runtime validation by the lead/user.

## CI correction — stable SDK platform availability (2026-10-10)

This section supersedes the initial compile/target API37 configuration described earlier in this report.

### Observed failure

The lead supplied actual CI evidence for source `659088d25082f70ddd65dca1be2fbb1cd45a6cb4`, run `38057134203`, job `114227802359`: Java `17.0.20+101` downloaded, pinned actions/wrapper checks passed, but the exact SDK installation step failed at 13:48:37 UTC with `Warning: Failed to find package 'platforms;android-37'`. The application compiler, unit tests, release lint, release assembly, and packaging gates did not run. A later cache-cleanup `BUILD SUCCESSFUL` was unrelated to app validation.

The initial AGP release-table verification correctly established maximum supported API37, but incorrectly treated that compatibility maximum as evidence that a stable API37 SDK package was published. That inference is corrected here.

### Primary publication evidence checked for the fix

Fetched and parsed Google's live SDK repository XML:

https://dl.google.com/android/repository/repository2-3.xml

The metadata explicitly lists:

| Package | Revision | Channel | Published archive |
| --- | --- | --- | --- |
| `platforms;android-36` | 2 | `channel-0` (stable) | `platform-36_r02.zip` |
| `build-tools;36.0.0` | 36.0.0 | `channel-0` (stable) | `build-tools_r36_linux.zip` for Linux |

The exact package `platforms;android-37` is absent from that repository metadata. API36.1 is also listed, but the app does not need its minor-API extensions, so API36 is the coherent stable choice.

Metadata archive evidence (recorded as published; no claim of downloading these archives locally):

- API36 revision2: size 65,878,410 bytes, published SHA1 `2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576`.
- Build Tools36 Linux: size 63,737,259 bytes, published SHA1 `b0b6376977657e8ad9b969bacf4093601da2c6fb`.

Official Android16 setup documentation explicitly prescribes `compileSdk = 36` and `targetSdk = 36`:

https://developer.android.com/about/versions/16/setup-sdk

Official SDK Platform release notes list Android16/API36 as stable:

https://developer.android.com/tools/releases/platforms

### Patch applied

- `app/build.gradle.kts`: compileSdk `37 → 36`, targetSdk `37 → 36`; minSdk remains33 and Build Tools remains36.0.0.
- `.github/workflows/android-release.yml`: `COMPILE_SDK` `37 → 36`; SDK manager now explicitly uses `--channel=0`, requesting only stable `platforms;android-36` and `build-tools;36.0.0`.
- `scripts/package_release.py`: expected targetSDK `37 → 36` and bundle build-info compileSDK `37 → 36`, preserving coherent APK identity validation and metadata.

The corrected configured tuple is AGP9.4.0 / Gradle9.6.0 / Temurin17.0.20+101 / Build Tools36.0.0 / compile36 / target36 / min33. Package/version, direct dependencies, action commits, release flags, test/lint gates, source provenance, unsigned checks, and native16KiB alignment checks remain as specified.

### Verification and next gate

Python syntax compilation passed. Workflow YAML parsed and its API36 value was asserted. Parsed primary SDK XML assertions verified both chosen packages use stable channel-0 and the API37 package is absent. `git diff --check` passed. The patch contains only the assigned build/workflow/packaging paths plus this report append. No application source, repository state, remote write, CI invocation, signing code, private key, or secret was changed.

The next Actions run must establish actual application compilation and test/lint/APK gates. SDK platform availability is now supported by primary package metadata instead of an AGP compatibility maximum; successful install/build is still not claimed before that run.
