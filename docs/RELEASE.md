# Build and release AlienFlow

## Stable installation identity

- Repository: [IssisX/AlienFlow](https://github.com/IssisX/AlienFlow).
- Package: `io.github.issisx.alienflow`.
- Initial version: `0.1.0`, versionCode `1`.
- First APK: ARM64, Android 13 (API 33) or newer.
- Permanent certificate SHA-256: `d79e74b3a4c24ee225d1220c2c374c6f958520d4f3257fcfb399ff09bf5c965f`.

The private signing backup is saved separately from this repository. It contains the permanent PKCS12 key, its password, certificate, and restore instructions. Keep that backup private. Every normal update must use this key and package ID, with a higher versionCode, to preserve the installed app and its private data.

## Verified 0.1.0 test delivery

The signed APK was delivered on 2026-10-10. [Release verification](RELEASE_VERIFICATION.json) records its source, run, size and hashes; [APK signature output](APK_SIGNATURE.txt) records successful verification with the permanent certificate. Exact compiled source: `c76b3ccffd7b3072da1a179ebf5f19b39cc7238e`. [Actions run 38058270344](https://github.com/IssisX/AlienFlow/actions/runs/38058270344) passed 36 tests, release lint with zero errors, assembly and package checks.

This release requires Android 13 or newer on ARM64. It is a signed test build; phone installation, provider/account access, editor behavior and update preservation remain unverified. The Actions artifact is an unsigned bundle with a 14-day retention period; use the separately delivered signed APK for installation.

## Build pipeline

The `Android release candidate` workflow runs on manual dispatch and intentional source pushes to `build/first-apk`. It pins the Java, Gradle, Android Gradle Plugin, Android SDK, native WebRTC library and workflow actions. The source of truth for versions is the version catalog, app build file, wrapper properties and workflow.

The command run in Actions is:

```bash
bash gradlew --no-daemon --no-configuration-cache --console=plain --stacktrace \
  :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
python3 scripts/package_release.py
```

The packaging script requires passing executed unit tests and zero release lint errors, checks the exact source revision, package/version/SDK identity, non-debuggable configuration, ARM64 WebRTC library, 16 KiB APK alignment, and unsigned state. It includes test/lint reports, build metadata and SHA-256 hashes in the delivery bundle. Tests run in CI do not establish Android-device behavior.

The workflow deliberately compiles an unsigned release. An unsigned APK cannot be installed. Its bundle includes the Android SDK standalone `apksigner.jar` and applicable tooling notices for the private signing step.

## Private signing

Use the saved permanent key outside this repository. Do not create a replacement key just because the build environment is new. Do not put the key, password, OpenAI key, dictation text or recordings in build artifacts or logs.

Load the saved password into `ALIENFLOW_SIGNING_PASSWORD` without echoing it and sign a copy of the aligned APK:

```bash
java -jar tooling/apksigner.jar sign \
  --ks /private/path/alienflow-release.p12 \
  --ks-key-alias alienflow \
  --ks-pass env:ALIENFLOW_SIGNING_PASSWORD \
  --key-pass env:ALIENFLOW_SIGNING_PASSWORD \
  --out AlienFlow-0.1.0.apk AlienFlow-0.1.0-unsigned.apk
java -jar tooling/apksigner.jar verify --verbose --print-certs AlienFlow-0.1.0.apk
```

Require successful APK verification and the certificate fingerprint above. Record the signed APK hash alongside the exact compiled source SHA and CI run URL. Never describe an unsigned build as an installable APK or use a debug key as a permanent release identity.

## Future updates

1. Read `AGENTS.md`, `PRODUCT_SPEC.md`, `IMPLEMENTATION.md` and the current verification record.
2. Implement and review the actual change. Preserve unrelated user work and stored data.
3. Increment versionCode; update the filename/version constants in the packaging script and workflow.
4. Build, inspect the actual workflow results, and sign with the existing permanent key.
5. Verify the certificate and install over the prior version on the test device. Confirm settings, history, pending recordings and bubble positions survive.
6. Record what was tested and any remaining failures. Deliver the verified signed APK.

## Signed 0.1.1 test update · 2026-10-10

- Source: `a3d0aee38adb73b545b31db20e806d6f493d76e8` (the later documentation-only commit is not the compiled APK revision).
- [Actions run 38063401303](https://github.com/IssisX/AlienFlow/actions/runs/38063401303) passed 37 tests, lint with zero errors, assembly and packaging.
- Signed APK: `AlienFlow-0.1.1.apk`, versionCode `2`, same applicationId and release signing certificate as 0.1.0.
- SHA-256: `438c9bb3b1fd6a876ac173a66bf58c376b20c679aab90d7523ff48f5a7dc9a0f`. Certificate SHA-256: `d79e74b3a4c24ee225d1220c2c374c6f958520d4f3257fcfb399ff09bf5c965f`.
- Use the **signed APK** delivered in the conversation for updates. The GitHub Actions bundle contains the unsigned intermediate APK, which is not installable until separately signed with the permanent key.
- Phone tap feedback, cold mic capture and live editing are pending runtime observation. The user should report the new bubble status message if dictation still does not start.

## Signed 0.1.3 diagnostic update · 2026-10-10

- Compiled source `972fbab4e28c8addd8f02ac55062d470a8e9964c` on `build/first-apk`, with only subsequent documentation changes.
- [GitHub Actions run 38066975001](https://github.com/IssisX/AlienFlow/actions/runs/38066975001): 56 unit tests passed, release lint zero errors and 21 warnings/issues, APK assembly and identity/16-KiB alignment checks passed.
- Signed `AlienFlow-0.1.3.apk` (versionCode 4), 17,045,689 bytes. SHA-256 `e29174db72a5e195b8d36a008bab874e1a3e9fb17ce6d2d9372b8aadc1b0e7d9`. Same original certificate SHA-256 `d79e74b3a4c24ee225d1220c2c374c6f958520d4f3257fcfb399ff09bf5c965f`. No new keystore; private key was not put in the repo or Actions.
- This diagnostic build preserves the installed package and data. It does NOT prove or fix the phone crash; see `IMPLEMENTATION.md` and `RELEASE_VERIFICATION_0.1.3.json` for the acceptance boundary.
