#!/usr/bin/env python3
"""Replay the live-ownership and light-polish cases against the shipped 0.1.26-claude.1 source.
Exactly these two cases must fail by assertion there; any other outcome blocks the release."""
from pathlib import Path
import json, os, shutil, subprocess, xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
BASE = '2b3455d90e9cd443367b14652eb9dfdcf74a083c'
REPORT = ROOT / 'build/live-polish-baseline'
WORK = Path(os.environ['RUNNER_TEMP']) / 'alienflow-025-live-polish-regression'
TESTS = {'RealtimeBufferedStartupTest': 'liveSpeechBeforeCommitBelongsToTheFocusedFieldUnlessAnotherFieldsCommitIsInFlight',
         'LightPolishRegressionTest': 'oneUnauthorizedWordNoLongerDiscardsSentenceCaseAndPunctuationAcrossPauses'}
SOURCE = Path('app/src/test/java/io/github/issisx/alienflow')
REPORT.mkdir(parents=True, exist_ok=True)
subprocess.run(['git', 'worktree', 'add', '--detach', str(WORK), BASE], cwd=ROOT, check=True)
command = ['bash', 'gradlew', '--no-daemon', '--no-configuration-cache', '--console=plain', ':app:testDebugUnitTest']
for suite, case in TESTS.items():
    shutil.copy2(ROOT / SOURCE / f'{suite}.kt', WORK / SOURCE / f'{suite}.kt')
    command += ['--tests', f'*.{suite}.{case}']
with (REPORT / 'gradle.log').open('w') as out:
    rc = subprocess.run(command, cwd=WORK, stdout=out, stderr=subprocess.STDOUT).returncode
failed, types, tests, errors = set(), {}, 0, 0
for suite in TESTS:
    xml = WORK / f'app/build/test-results/testDebugUnitTest/TEST-io.github.issisx.alienflow.{suite}.xml'
    if not xml.exists():
        raise RuntimeError(f'Baseline produced no JUnit report for {suite}')
    shutil.copy2(xml, REPORT / xml.name)
    root = ET.parse(xml).getroot()
    tests += int(root.attrib['tests']); errors += int(root.attrib['errors'])
    for case in root.findall('testcase'):
        failure = case.find('failure')
        if failure is not None:
            failed.add(case.attrib['name']); types[case.attrib['name']] = failure.attrib.get('type', '')
record = {'baseline_sha': BASE, 'candidate_sha': os.environ['GITHUB_SHA'], 'exit_code': rc, 'tests': tests,
          'errors': errors, 'expected_failures': sorted(TESTS.values()), 'actual_failures': sorted(failed),
          'failure_types': types}
(REPORT / 'reproduction.json').write_text(json.dumps(record, indent=2) + '\n')
assert rc != 0 and failed == set(TESTS.values()) and tests == 2 and errors == 0, record
assert set(types.values()) <= {'java.lang.AssertionError', 'org.junit.ComparisonFailure'}, record
print(json.dumps(record, indent=2))
