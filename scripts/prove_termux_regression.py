#!/usr/bin/env python3
"""Run the same regression against the shipped 0.1.17 coordinator in a separate worktree.
A failure is expected ONLY in the two named cases; other failures block the release.
No release expectations are weakened and the candidate checkout is never modified.
"""
from pathlib import Path
import json, os, shutil, subprocess, xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
BASE = '7ebe095bfaa83456690b46d8934c0cad2590a94a'
REPORT = ROOT / 'build/termux-baseline'
WORK = Path(os.environ['RUNNER_TEMP']) / 'alienflow-017-regression'
TEST = Path('app/src/test/java/io/github/issisx/alienflow/TerminalCoordinatorTest.kt')
EXPECTED = {
    'accessibilityEventsDuringSpeechKeepTheTerminalBoundAndFinalTextCommitsOnce',
    'finishingEventsCannotSendPartialTerminalTextBeforeTheLastTranscript',
}
REPORT.mkdir(parents=True, exist_ok=True)
subprocess.run(['git','worktree','add','--detach',str(WORK),BASE],cwd=ROOT,check=True)
shutil.copy2(ROOT/TEST, WORK/TEST)
with (REPORT/'gradle.log').open('w') as out:
    rc=subprocess.run(['bash','gradlew','--no-daemon','--no-configuration-cache','--console=plain',
                      ':app:testDebugUnitTest','--tests','*.TerminalCoordinatorTest'],cwd=WORK,stdout=out,stderr=subprocess.STDOUT).returncode
xml=WORK/'app/build/test-results/testDebugUnitTest/TEST-io.github.issisx.alienflow.TerminalCoordinatorTest.xml'
if not xml.exists():
    raise RuntimeError(f'No baseline JUnit report; baseline build failed before regression execution (rc={rc})')
shutil.copy2(xml, REPORT/xml.name)
suite=ET.parse(xml).getroot()
failed={c.attrib['name'] for c in suite.findall('testcase') if c.find('failure') is not None}
errors=sum(c.find('error') is not None for c in suite.findall('testcase'))
record={'baseline_sha':BASE,'candidate_sha':os.environ['GITHUB_SHA'],'tests':int(suite.attrib['tests']),
        'expected_failed_cases':sorted(EXPECTED),'actual_failed_cases':sorted(failed),'errors':errors,
        'exit_code':rc,'scope':'Robolectric with production coordinator/bridge; OS broker, recognition and terminal consumer fixtures'}
(REPORT/'reproduction.json').write_text(json.dumps(record,indent=2)+'\n')
assert rc!=0 and failed==EXPECTED and errors==0 and int(suite.attrib.get('skipped',0))==0 and int(suite.attrib['tests'])==3, record
print('REPRODUCED: shipped 0.1.17 fails exactly the two Termux regressions; changed-field protection passes.')
print(json.dumps(record,indent=2))
