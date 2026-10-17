#!/usr/bin/env python3
"""Run unchanged shutdown tests against the shipped .25 source; no provider traffic."""
from pathlib import Path
import json, os, shutil, subprocess, xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
BASE = '39a8e71bcbdff6397e056c211c053bf07b96da5c'
WORK = Path(os.environ['RUNNER_TEMP']) / 'alienflow-billing-baseline'
REPORT = ROOT / 'build/billing-baseline'
REPORT.mkdir(parents=True, exist_ok=True)
subprocess.run(['git','worktree','add','--detach',str(WORK),BASE],cwd=ROOT,check=True)
TEST = Path('app/src/test/java/io/github/issisx/alienflow/BillingShutdownRegressionTest.kt')
shutil.copy2(ROOT/TEST,WORK/TEST)
with (REPORT/'gradle.log').open('w') as log:
    rc = subprocess.run(['bash','gradlew','--no-daemon','--no-configuration-cache','--console=plain',':app:testDebugUnitTest','--tests','*.BillingShutdownRegressionTest'],cwd=WORK,stdout=log,stderr=subprocess.STDOUT).returncode
xml = WORK/'app/build/test-results/testDebugUnitTest/TEST-io.github.issisx.alienflow.BillingShutdownRegressionTest.xml'
if not xml.exists():
    raise RuntimeError(f'No baseline JUnit report: setup/build failed, rc={rc}')
suite = ET.parse(xml).getroot(); shutil.copy2(xml,REPORT/xml.name)
failed = {t.attrib['name']: t.find('failure').attrib.get('type') for t in suite.findall('testcase') if t.find('failure') is not None}
expected = {'stoppedInterruptedSessionDoesNotAutomaticallyUploadTheRecordingAgain','cancelClosesRecoveryHttpEvenWhenDictationIsAlreadyIdle'}
record = dict(baseline_sha=BASE, candidate_sha=os.environ['GITHUB_SHA'], exit_code=rc, tests=int(suite.attrib['tests']), errors=int(suite.attrib['errors']), failures=failed,
              scope='Production coordinator and HTTP against loopback; simulated WAV, no paid requests. Does not attribute account charges.')
(REPORT/'reproduction.json').write_text(json.dumps(record,indent=2)+'\n')
assert rc != 0 and record['tests']==2 and record['errors']==0 and set(failed)==expected and set(failed.values()) <= {'java.lang.AssertionError','org.junit.ComparisonFailure'}, record
print(json.dumps(record))
