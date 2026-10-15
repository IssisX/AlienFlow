#!/usr/bin/env python3
"""Replay phone-metadata and field-switch cases against the shipped chat APK source."""
from pathlib import Path
import json, os, shutil, subprocess, xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
BASE = 'adda87cd9bc2e0f3cb491a35ab9a8d9877347c4f'
REPORT = ROOT / 'build/app-switch-baseline'
WORK = Path(os.environ['RUNNER_TEMP']) / 'alienflow-023-switch-regression'
TEST = Path('app/src/test/java/io/github/issisx/alienflow/EditorBridgeNodeRouteTest.kt')
EXPECTED = {'defaultWindowConfirmsActualTextWhenAbsoluteCallbackStaysAtZero',
            'repeatedNewFieldsAcceptSpeechWithoutRestartingTheCoordinator'}
REPORT.mkdir(parents=True, exist_ok=True)
subprocess.run(['git', 'worktree', 'add', '--detach', str(WORK), BASE], cwd=ROOT, check=True)
shutil.copy2(ROOT / TEST, WORK / TEST)
command = ['bash', 'gradlew', '--no-daemon', '--no-configuration-cache', '--console=plain', ':app:testDebugUnitTest']
for name in sorted(EXPECTED):
    command += ['--tests', '*.EditorBridgeNodeRouteTest.' + name]
with (REPORT / 'gradle.log').open('w') as out:
    rc = subprocess.run(command, cwd=WORK, stdout=out, stderr=subprocess.STDOUT).returncode
xml = WORK / 'app/build/test-results/testDebugUnitTest/TEST-io.github.issisx.alienflow.EditorBridgeNodeRouteTest.xml'
if not xml.exists():
    raise RuntimeError('Baseline failed before producing a JUnit report')
shutil.copy2(xml, REPORT / xml.name)
suite = ET.parse(xml).getroot()
failed = {case.attrib['name'] for case in suite.findall('testcase') if case.find('failure') is not None}
failure_types = {case.attrib['name']: case.find('failure').attrib.get('type', '')
                 for case in suite.findall('testcase') if case.find('failure') is not None}
record = {'baseline_sha': BASE, 'candidate_sha': os.environ['GITHUB_SHA'], 'exit_code': rc,
          'tests': int(suite.attrib['tests']), 'expected_failures': sorted(EXPECTED),
          'actual_failures': sorted(failed), 'errors': int(suite.attrib['errors']),
          'failure_types': failure_types,
          'scope': 'Production coordinator/bridge and Android default window; broker and transcript fixtures'}
(REPORT / 'reproduction.json').write_text(json.dumps(record, indent=2) + '\n')
assert rc != 0 and failed == EXPECTED and record['errors'] == 0 and record['tests'] == 2 and int(suite.attrib.get('skipped', 0)) == 0, record
assert set(failure_types.values()) <= {'java.lang.AssertionError', 'org.junit.ComparisonFailure'}, record
print(json.dumps(record, indent=2))
