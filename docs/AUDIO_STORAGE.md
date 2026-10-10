# Audio storage implementation report

Date: 2026-10-10. Scope: `PendingAudio.kt` and `PendingAudioTest.kt` only. No remote writes, builds, signing, provider requests, credentials, recordings, or dictated text were used by this task.

## Implemented contract

- Preserved `PendingAudio(file: File)`, `val file`, `append(data, rate, channelCount)`, and `close()`.
- Construction starts an idle writer, and neither construction nor append performs file I/O. The urgent callback copies and offers a bounded frame without waiting for disk or queue capacity. A maximum of 256 queued callbacks, 1 MiB of accepted outstanding PCM, and 64 KiB per callback bound memory. Overflow is an explicit retained failure; later audio cannot make the session appear successful.
- The first nonempty callback fixes sample rate and channel count. PCM16 sample alignment and representable WAV metadata are checked. Changed metadata is rejected; previously queued valid frames still drain. The caller must independently verify the actual callback reports Android PCM16 because the preserved append API has no sample-format parameter.
- The writer creates a new file exclusively, so a colliding filename cannot truncate an existing recording. No nonempty samples means no file and no invented sample rate.
- The writer locks the recording, writes and syncs its authoritative PCM16 header before any PCM payload, checkpoints RIFF/data sizes after each successful write, syncs recording data at approximately one-second intervals, and syncs again on close. Disk work stays on the writer.
- Close stops admission, waits for accepted append admissions and queued work, finalizes against actual stored bytes, and closes the channel/lock even after a write, header, or sync failure. Repeated close is safe and reports the retained failure. Interrupted close completes resource cleanup before restoring interruption and throwing `InterruptedIOException`.

## Recovery and upload API

```kotlin
val info: PendingAudio.WavInfo = PendingAudio.repair(file)
PendingAudio.forEachUploadChunk(file, maxPayloadBytes = 20L * 1024 * 1024) { part ->
    // Synchronously read/upload part with multipart filename "recording.wav" and audio/wav.
}
```

`WavInfo` exposes `sampleRate`, `channels`, `dataBytes`, and `bytesPerFrame`. Both helper APIs perform blocking I/O and require an inactive recording. An exclusive file lock rejects repair or chunking of an active writer's file promptly. A missing source is opened without CREATE and remains missing.

Repair validates the app's canonical 44-byte PCM16 WAV layout, sample format, channel count, sample rate, block alignment, and byte rate. It derives payload size from actual file length rather than trusting either stale size field, then durably fixes those fields. It never deletes or truncates the source. Valid empty WAVs can be inspected, but upload of empty audio fails before a provider call.

Chunking locks and repairs the source, streams ordered parts through one 64 KiB buffer, and aligns every boundary to a complete PCM frame. The default payload is 20 MiB; the requested payload plus the WAV header must remain strictly below 25,000,000 bytes. For a full-size part, it searches the last two seconds or 10% of that part, whichever is shorter, for at least 100 ms where every channel is quiet (absolute PCM amplitude at most 256). It chooses a boundary inside a qualifying quiet interval; otherwise it uses the exact frame-aligned size boundary. There is no duplicated overlap or omitted audio at either boundary.

Each valid temporary WAV uses a `.upload` suffix beside the source. This deliberately excludes it from the application's immediate `*.wav` pending-recording list. OpenAiClient must provide multipart filename `recording.wav`. Only one part exists at a time; it is removed in finally before the next callback, including callback failure or cancellation. A cleanup failure propagates and preserves the original. The existing LocalStore age cleanup of immediate children also removes stale `.upload` files left by process death on a later pending-list refresh. The helper never deletes the source. Its owner must save the complete ordered transcription result durably before deleting that source.

Exact API, temporary suffix, actual callback-format validation, asynchronous close requirement, and zero-frame behavior were communicated to the native app owner. The reviewer examined the helper and reported no helper-specific static blocker before tests were added.

## Original test coverage and subsequent evidence

At the second compiler checkpoint, `PendingAudioTest.kt` contained 21 JVM tests using actual temporary files and controlled FileChannel failure points. Coverage includes:

- Valid authoritative headers before close; active-file repair/upload rejection.
- Callback return while file opening is deliberately stalled; copy ownership and accepted-frame drain.
- Queue capacity and byte-budget overflow with explicit failure and retained accepted audio.
- Immutable capture format; invalid callback alignment, metadata, and oversized callback rejection.
- Zero-frame behavior and refusing to overwrite an existing recording.
- Sync failure resource cleanup, complete and incomplete partial writes, and interrupted close cleanup.
- Process-death size-header repair, including unsigned stale fields; corrupt/unknown headers and incomplete frames preserved unchanged.
- Explicit standard RIFF size-limit failure using a sparse file without multi-gigabyte allocation.
- Ordered, frame-aligned upload bytes, deletion before the next part, second-part upload failure, and source retention.
- Automatic stale-header repair before chunking; sustained-silence and hard-size boundaries.
- A real source over 20 MiB, streamed with bounded buffers into provider-sized files, with source digest preservation.
- Invalid upload-size bounds rejected without source mutation.

The worker initially prepared this suite without a local Kotlin/Android compiler. The follow-up below records the real CI-discovered failure and repair. The final CI4 source passed all 22 storage cases and 14 dictation cases; see IMPLEMENTATION.md for the exact source/run. Physical-device behavior remains unverified.

## Explicit first-slice limits

- This is one standard RIFF WAV, not RF64 or automatic rolling capture storage. The payload ceiling is the largest frame-aligned count no greater than `0xffffffff - 36`; reaching it is an explicit retained failure. At 48 kHz mono PCM16 it is approximately 12.4 hours. This is a file-format/resource ceiling, not an application word quota.
- Queued bytes still in process memory cannot survive process death. Metadata and on-disk PCM remain repairable; periodic syncing does not guarantee recovery of unsynced bytes after power loss or filesystem/hardware failure. A killed process also cannot execute temporary-file finally cleanup; existing retention handles orphan `.upload` files on later refresh.
- A legacy all-zero header cannot reveal trustworthy sample metadata. Unknown header layouts, malformed metadata, and an incomplete terminal PCM frame are retained and rejected rather than guessed, padded, or silently truncated. A future explicit salvage tool could recover complete prefixes from such damaged recordings.
- Close waits for underlying file I/O to finish; it cannot both guarantee file cleanup and force a deadline on an unresponsive filesystem. It must stay off the main and urgent audio threads.
- Sequential chunk transcription does not create a persistent per-part recovery checkpoint. Failure after earlier successful uploads keeps the entire original and a later retry can repeat those provider uploads. Durable per-part text and automatic reconnect/replay remain coordinator-level future work.
- Silence detection is a local amplitude heuristic, not a guarantee that a boundary is linguistically complete. Provider access, recognition quality across boundaries, capture route formats, actual crash behavior, and physical-device timing remain untested.

## Follow-up: CI-confirmed executor interrupt race

The lead supplied concrete CI evidence for source commit `cf391cd212df6596d709ba06d148653d842771ff`, Actions run `38057441585`, job `114228696584`: the application compiled and 32 JVM tests ran, with one failure. `PendingAudioTest.validAuthoritativeHeaderExistsBeforeCloseAndActiveRepairIsRefused` failed during `finishOutput()` with `ClosedByInterruptException`, wrapped at `PendingAudio.kt:157` in that revision.

### Cause and bounded repair

`ThreadPoolExecutor.shutdown()` interrupts idle workers. A worker can retain that interrupt after its last task unlocks and `getTask()` observes SHUTDOWN with an empty queue. The worker then enters `terminated()` with its interrupt flag still set. Our termination hook performs interruptible FileChannel reads and syncs, so ordinary recording close could close the channel through interruption before final header verification/sync finished. A competing external-close explanation does not produce this worker's interrupt flag and would report a closed/asynchronously closed channel instead.

The fix is confined to the executor termination hook: save and clear an inherited interrupt before owned WAV finalization, then restore it before signaling completion. The executor serializes its shutdown/termination hook under its termination lock, so its own idle-worker shutdown interrupts cannot arrive inside that protected cleanup interval. The hook may also run on a shutdown caller, so restoring the flag preserves the existing caller-interruption contract. Real file/storage exceptions remain failures; no exception is suppressed to make the original test pass.

The original failing test is unchanged. A new deterministic regression, `inheritedWorkerInterruptCannotAbortFinalHeaderSyncAndCleanup`, adds a controlled final worker task through test-only reflection, starts close until the executor is SHUTDOWN, and then lets that task return with its interrupt flag set. This forces the failing termination ordering without relying on a race lottery. The test checks successful finalization, actual header and payload bytes, file-lock release, and exactly one channel close. The storage test file now contains 22 tests.

### Executed mechanism evidence and remaining gate

The local environment includes the JDK compiler module although no standalone javac executable was found. A small Java source-launcher reproducer at `/tmp/AlienFlowTerminationInterrupt.java` exercised the same ThreadPoolExecutor/FileChannel termination transition on Java 17.0.20. Its actual output was:

```text
ORIGINAL: inheritedInterrupt=true, failure=ClosedByInterruptException, channelClosed=true, interruptRestored=true
PATCHED: inheritedInterrupt=true, failure=none, channelClosed=true, interruptRestored=true
```

The reproducer exited successfully and removed its temporary data file. This establishes runtime evidence for the named mechanism and the save/clear/finalize/restore repair. It does not establish that the Kotlin unit suite or Android app passes. The updated Kotlin source/test files were frozen when the lead requested the next CI checkpoint; the next CI result remains the compile/test gate. No Git, push, signing, provider calls, or unrelated source changes were performed for this follow-up.

## Final CI checkpoint

Source `c76b3ccffd7b3072da1a179ebf5f19b39cc7238e` passed all 36 tests, including all 22 storage tests, in [Actions run 38058270344](https://github.com/IssisX/AlienFlow/actions/runs/38058270344). The original failing test and deterministic executor-interrupt regression passed. Build, lint, packaging and final APK signing evidence are recorded in IMPLEMENTATION.md and RELEASE_VERIFICATION.json.
