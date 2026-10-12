#!/usr/bin/env python3
"""Fail closed on release identity, transport ownership, unsigned state, and CI gates."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tomllib
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "io.github.issisx.alienflow"
VERSION_CODE = 13
VERSION_NAME = "0.1.12"
MIN_SDK = 33
TARGET_SDK = 36
BUILD_TOOLS = "36.0.0"
APPROVED_ARTWORK_SHA256 = "48d0ce0aacba2fd4264100fb1af1b6a73f54290f6c5296f4adc07d3974a50671"
TRANSPORT = "android-audiorecord+openai-realtime-websocket"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def run(*args: str) -> str:
    return subprocess.run(
        args,
        cwd=ROOT,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    ).stdout


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def bytes_sha256(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def main() -> None:
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT", ""))
    tools = sdk / "build-tools" / BUILD_TOOLS
    require(tools.is_dir(), f"Android SDK Build Tools {BUILD_TOOLS} are missing")
    source_sha = run("git", "rev-parse", "HEAD").strip()
    require(source_sha == os.environ.get("GITHUB_SHA"), "Checkout SHA differs from workflow source SHA")
    require(
        not run("git", "status", "--porcelain", "--untracked-files=no").strip(),
        "Tracked source changed during the build",
    )

    raw_apk = ROOT / "app/build/outputs/apk/release/app-release-unsigned.apk"
    require(raw_apk.is_file(), "Expected unsigned release APK is missing")
    delivery = ROOT / "build/delivery"
    bundle = delivery / "bundle"
    if bundle.exists():
        shutil.rmtree(bundle)
    (bundle / "reports").mkdir(parents=True)
    apk = bundle / "AlienFlow-0.1.12-unsigned.apk"
    run(str(tools / "zipalign"), "-P", "16", "-f", "4", str(raw_apk), str(apk))
    run(str(tools / "zipalign"), "-c", "-P", "16", "4", str(apk))

    badging = run(str(tools / "aapt"), "dump", "badging", str(apk))
    identity = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    require(identity is not None, "APK package identity is unreadable")
    require(identity.groups() == (PACKAGE, str(VERSION_CODE), VERSION_NAME), "Unexpected APK package/version")
    require(f"sdkVersion:'{MIN_SDK}'" in badging, "Unexpected APK minimum SDK")
    require(f"targetSdkVersion:'{TARGET_SDK}'" in badging, "Unexpected APK target SDK")
    require("application-debuggable" not in badging, "Release APK is debuggable")
    require("native-code:" not in badging, "Release unexpectedly declares native code")

    manifest = run(str(tools / "aapt"), "dump", "xmltree", str(apk), "AndroidManifest.xml")
    debug_attribute = re.search(r"android:debuggable[^\n]*", manifest)
    if debug_attribute:
        require(
            "0xffffffff" not in debug_attribute.group(0)
            and not re.search(r"=\s*(?:true|1)\b", debug_attribute.group(0)),
            "Manifest marks APK as debuggable",
        )

    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        native_libraries = sorted(
            name for name in names if name.startswith("lib/") and name.endswith(".so")
        )
        require(not native_libraries, f"Direct transport APK must contain no native libraries: {native_libraries}")
        require(
            not any("libjingle_peerconnection_so.so" in name for name in names),
            "Crashing WebRTC native library is still packaged",
        )
        require(
            not any(re.match(r"META-INF/.*\.(SF|RSA|DSA|EC)$", name, re.I) for name in names),
            "APK contains a JAR signature",
        )
        packaged_hashes = {name: bytes_sha256(archive.read(name)) for name in names if not name.endswith("/")}
        artwork_entries = [name for name, digest in packaged_hashes.items() if digest == APPROVED_ARTWORK_SHA256]
        require(len(artwork_entries) == 1, "Approved Gray artwork is missing or duplicated in the APK")

    require(b"APK Sig Block 42" not in apk.read_bytes(), "APK already contains an APK signing block")
    signing = subprocess.run(
        [str(tools / "apksigner"), "verify", "--verbose", str(apk)],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    require(
        signing.returncode != 0 and "Missing META-INF/MANIFEST.MF" in signing.stdout,
        "apksigner did not confirm the expected completely unsigned APK",
    )

    test_files = sorted((ROOT / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
    require(bool(test_files), "Required unit-test XML reports are missing")
    tests = failures = errors = skipped = 0
    for report in test_files:
        suite = ET.parse(report).getroot()
        tests += int(suite.attrib.get("tests", 0))
        failures += int(suite.attrib.get("failures", 0))
        errors += int(suite.attrib.get("errors", 0))
        skipped += int(suite.attrib.get("skipped", 0))
    require(
        tests > 0 and tests > skipped and failures == 0 and errors == 0,
        "Unit-test gate requires executed passing tests",
    )

    lint_path = ROOT / "app/build/reports/lint-results-release.xml"
    require(lint_path.is_file(), "Required release lint XML report is missing")
    issues = ET.parse(lint_path).getroot().findall("issue")
    lint_errors = sum(issue.attrib.get("severity", "").lower() in ("error", "fatal") for issue in issues)
    require(lint_errors == 0, "Release lint reports errors")

    reports = bundle / "reports"
    shutil.copytree(ROOT / "app/build/test-results/testDebugUnitTest", reports / "unit-test-xml")
    shutil.copytree(ROOT / "app/build/reports/tests/testDebugUnitTest", reports / "unit-tests")
    ui_proof = ROOT / "app/build/reports/ui-proof"
    require(
        (ui_proof / "main-0.1.12.png").is_file()
        and (ui_proof / "diagnostics-0.1.12.png").is_file()
        and (ui_proof / "indicator-light-0.1.12.png").is_file()
        and (ui_proof / "indicator-dark-0.1.12.png").is_file(),
        "Actual activity render evidence is missing",
    )
    shutil.copytree(ui_proof, reports / "ui-proof")
    for report in (ROOT / "app/build/reports").glob("lint-results-release.*"):
        shutil.copy2(report, reports / report.name)
    (reports / "apk-badging.txt").write_text(badging)
    (reports / "apk-manifest.txt").write_text(manifest)
    (reports / "unsigned-verification.txt").write_text(signing.stdout)
    (reports / "transport-verification.txt").write_text(
        f"Transport: {TRANSPORT}\n"
        "Native libraries: none\n"
        f"Approved artwork entry: {artwork_entries[0]}\n"
        f"Approved artwork SHA-256: {APPROVED_ARTWORK_SHA256}\n"
    )
    (reports / "test-lint-summary.txt").write_text(
        f"Unit tests: {tests}; failures: {failures}; errors: {errors}; skipped: {skipped}\n"
        f"Release lint: {len(issues)} issues; errors/fatal: {lint_errors}\n"
        "Gradle gates: :app:testDebugUnitTest :app:lintRelease :app:assembleRelease succeeded.\n"
        "Device, microphone, provider account, and editor behavior have not been validated by CI.\n"
    )

    tooling = bundle / "tooling"
    tooling.mkdir()
    require((tools / "lib/apksigner.jar").is_file(), "Standalone SDK apksigner.jar is missing")
    shutil.copy2(tools / "lib/apksigner.jar", tooling / "apksigner.jar")
    notices = sorted(tools.glob("NOTICE*"))
    require(bool(notices), "SDK tooling redistribution notice is missing")
    for notice in notices:
        shutil.copy2(notice, tooling / notice.name)
    shutil.copy2(tools / "source.properties", tooling / "source.properties")
    (tooling / "README.txt").write_text(
        "apksigner.jar is the unmodified standalone signing tool from Android SDK Build Tools 36.0.0.\n"
        "Copyright/license notices are included from that SDK package.\n"
        "The APK is deliberately unsigned. Never generate or store a private key in this bundle or repository.\n"
        "With Java 17, sign a COPY using an existing private keystore stored outside the repository:\n"
        "java -jar tooling/apksigner.jar sign --ks /private/path/release.jks --ks-key-alias alienflow "
        "--out AlienFlow-0.1.12.apk AlienFlow-0.1.12-unsigned.apk\n"
        "Omit password options to use the private interactive password prompt.\n"
        "java -jar tooling/apksigner.jar verify --verbose --print-certs AlienFlow-0.1.12.apk\n"
    )

    versions = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text())["versions"]
    require("webrtc" not in versions, "WebRTC version remains in the release dependency catalog")
    info = {
        "source_sha": source_sha,
        "repository": os.environ.get("GITHUB_REPOSITORY"),
        "workflow_run_id": os.environ.get("GITHUB_RUN_ID"),
        "workflow_run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
        "application_id": PACKAGE,
        "version_code": VERSION_CODE,
        "version_name": VERSION_NAME,
        "min_sdk": MIN_SDK,
        "target_sdk": TARGET_SDK,
        "compile_sdk": 36,
        "debuggable": False,
        "signing": "unsigned; private release signing required before installation",
        "transport": TRANSPORT,
        "native_libraries": native_libraries,
        "approved_artwork_entry": artwork_entries[0],
        "approved_artwork_sha256": APPROVED_ARTWORK_SHA256,
        "toolchain": {
            "gradle": "9.6.0",
            "agp": versions["agp"],
            "kotlin_builtin_expected": "2.2.10",
            "kotlin_version_basis": "AGP 9.4.0 published POM; resolved compiler version not separately reported",
            "build_tools": BUILD_TOOLS,
            "java": run("java", "-version").strip(),
        },
        "dependencies": versions,
        "tests": {
            "executed": tests - skipped,
            "total": tests,
            "failures": failures,
            "errors": errors,
            "skipped": skipped,
            "device_tests": "not run",
        },
        "lint": {"issues": len(issues), "errors": lint_errors},
        "sha256": {
            str(path.relative_to(bundle)): sha256(path)
            for path in sorted(bundle.rglob("*"))
            if path.is_file()
        },
    }
    (bundle / "build-info.json").write_text(json.dumps(info, indent=2) + "\n")

    zip_path = delivery / "AlienFlow-0.1.12-unsigned-release-bundle.zip"
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(bundle.rglob("*")):
            if path.is_file():
                archive.write(path, str(path.relative_to(bundle)))
    print(f"Verified unsigned APK: {sha256(apk)}")
    print(f"Delivery bundle: {zip_path.relative_to(ROOT)} sha256={sha256(zip_path)}")

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as stream:
            stream.write(
                f"## Verified unsigned release\n\nSource: `{source_sha}`\n\n"
                f"Package: `{PACKAGE}`; version: `{VERSION_NAME}` (`{VERSION_CODE}`); SDK: {MIN_SDK}–{TARGET_SDK}.\n\n"
                f"Unit tests: {tests - skipped} executed; {skipped} skipped. Lint errors: {lint_errors}. Native libraries: none.\n\n"
                f"Transport: `{TRANSPORT}`.\n\n"
                f"APK SHA-256: `{sha256(apk)}`\n\nBundle SHA-256: `{sha256(zip_path)}`\n\n"
                "Private release signing is still required before installation.\n"
            )


if __name__ == "__main__":
    main()
