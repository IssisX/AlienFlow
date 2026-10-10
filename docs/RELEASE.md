# Build and release AlienFlow

## Stable installation identity

- Repository: [IssisX/AlienFlow](https://github.com/IssisX/AlienFlow).
- Package: `io.github.issisx.alienflow`.
- Initial version: `0.1.0`, versionCode `1`.
- First APK: ARM64, Android 13 (API 33) or newer.
- Permanent certificate SHA-256: `d79e74b3a4c24ee225d1220c2c374c6f958520d4f3257fcfb399ff09bf5c965f`.

The private signing backup is saved separately from this repository. It contains the permanent PKCS12 key, its password, certificate, and restore instructions. Keep that backup private. Every normal update must use this key and package ID, with a higher versionCode, to preserve the installed app and its private data.

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
