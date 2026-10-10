package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class PendingAudioTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test(timeout = 10000) fun appendReturnsWhileDiskIsBlockedAndCloseDrainsCopiedFrames() {
        val file = path("drain.wav")
        val opening = CountDownLatch(1)
        val release = CountDownLatch(1)
        val channel = AtomicReference<ProbeChannel>()
        val audio = PendingAudio(file, 4) {
            opening.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            ProbeChannel(openNew(it)).also(channel::set)
        }
        assertFalse(file.exists())
        val first = byteArrayOf(1, 2, 3, 4)
        val closeResult = AtomicReference<Throwable?>()
        val closeStarted = CountDownLatch(1)
        val closeDone = CountDownLatch(1)
        try {
            audio.append(first, 16000, 2)
            assertTrue("Append returned without waiting for the blocked file open", opening.await(2, TimeUnit.SECONDS))
            first.fill(99)
            audio.append(byteArrayOf(5, 6, 7, 8), 16000, 2)
            Thread {
                closeStarted.countDown()
                try { audio.close() } catch (e: Throwable) { closeResult.set(e) }
                finally { closeDone.countDown() }
            }.start()
            assertTrue(closeStarted.await(2, TimeUnit.SECONDS))
            assertFalse("Close must wait for accepted audio", closeDone.await(50, TimeUnit.MILLISECONDS))
            release.countDown()
            assertTrue(closeDone.await(5, TimeUnit.SECONDS))
            assertNull(closeResult.get())
            assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), payload(file))
            assertHeader(file, 16000, 2, 8)
            assertFalse(channel.get().isOpen)
            assertEquals(1, channel.get().closes.get())
            audio.close()
            audio.append(byteArrayOf(9), 0, 0) // No acceptance after close.
            assertEquals(52L, file.length())
        } finally {
            release.countDown()
            runCatching { audio.close() }
        }
    }

    @Test(timeout = 10000) fun validAuthoritativeHeaderExistsBeforeCloseAndActiveRepairIsRefused() {
        val file = path("active.wav")
        val checkpoint = CountDownLatch(1)
        val audio = PendingAudio(file, 4) {
            ProbeChannel(openNew(it), afterWrite = { position -> if (position == 40L) checkpoint.countDown() })
        }
        try {
            audio.append(byteArrayOf(1, 2, 3, 4), 44100, 1)
            assertTrue(checkpoint.await(5, TimeUnit.SECONDS))
            assertHeader(file, 44100, 1, 4)
            assertThrows(IOException::class.java) { PendingAudio.repair(file) }
            assertThrows(IOException::class.java) {
                PendingAudio.forEachUploadChunk(file) { fail("Active audio must not be uploaded") }
            }
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), payload(file))
        } finally { audio.close() }
        assertEquals(PendingAudio.WavInfo(44100, 1, 4), PendingAudio.repair(file))
    }

    @Test(timeout = 10000) fun queueOverflowIsVisibleAndAlreadyAcceptedFramesStillDrain() {
        val file = path("queue.wav")
        val opening = CountDownLatch(1)
        val release = CountDownLatch(1)
        val audio = PendingAudio(file, 2) {
            opening.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            openNew(it)
        }
        try {
            audio.append(byteArrayOf(1, 2), 16000, 1)
            assertTrue(opening.await(2, TimeUnit.SECONDS))
            audio.append(byteArrayOf(3, 4), 16000, 1)
            audio.append(byteArrayOf(5, 6), 16000, 1)
            assertThrows(IOException::class.java) { audio.append(byteArrayOf(7, 8), 16000, 1) }
            assertThrows(IOException::class.java) { audio.append(byteArrayOf(9, 10), 16000, 1) }
            release.countDown()
            assertThrows(IOException::class.java) { audio.close() }
            assertHeader(file, 16000, 1, 6)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), payload(file))
            assertEquals(6L, PendingAudio.repair(file).dataBytes)
        } finally {
            release.countDown()
            runCatching { audio.close() }
        }
    }

    @Test(timeout = 10000) fun queuedMemoryIsBoundedByBytesAsWellAsFrameCount() {
        val file = path("budget.wav")
        val opening = CountDownLatch(1)
        val release = CountDownLatch(1)
        val audio = PendingAudio(file, 64) {
            opening.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            openNew(it)
        }
        val frame = ByteArray(64 * 1024) { 1 }
        try {
            audio.append(frame, 48000, 1)
            assertTrue(opening.await(2, TimeUnit.SECONDS))
            repeat(15) { audio.append(frame, 48000, 1) }
            assertThrows(IOException::class.java) { audio.append(frame, 48000, 1) }
            release.countDown()
            assertThrows(IOException::class.java) { audio.close() }
            assertHeader(file, 48000, 1, 1024L * 1024)
        } finally {
            release.countDown()
            runCatching { audio.close() }
        }
    }

    @Test(timeout = 10000) fun sampleRateAndChannelChangesCannotRelabelPreviouslyCapturedAudio() {
        for ((index, metadata) in listOf(24000 to 2, 16000 to 1).withIndex()) {
            val file = path("format-$index.wav")
            val audio = PendingAudio(file)
            audio.append(byteArrayOf(1, 2, 3, 4), 16000, 2)
            assertThrows(IOException::class.java) { audio.append(byteArrayOf(5, 6, 7, 8), metadata.first, metadata.second) }
            assertThrows(IOException::class.java) { audio.close() }
            assertHeader(file, 16000, 2, 4)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), payload(file))
        }
    }

    @Test fun invalidMetadataIncompleteFramesAndOversizedCallbacksFailBeforeWriting() {
        val cases = listOf(
            Triple(byteArrayOf(1), 16000, 1),
            Triple(byteArrayOf(1, 2), 16000, 2),
            Triple(byteArrayOf(1, 2), 0, 1),
            Triple(byteArrayOf(1, 2), 16000, 0),
            Triple(ByteArray(64 * 1024 + 2), 48000, 1),
            Triple(ByteArray(2), Int.MAX_VALUE, 2),
        )
        for ((index, values) in cases.withIndex()) {
            val file = path("invalid-$index.wav")
            val audio = PendingAudio(file)
            assertThrows(IOException::class.java) { audio.append(values.first, values.second, values.third) }
            assertThrows(IOException::class.java) { audio.close() }
            assertFalse(file.exists())
        }
    }

    @Test fun emptyCaptureDoesNotInventASampleRateOrCreateAnUpload() {
        val file = path("no-frames.wav")
        val audio = PendingAudio(file)
        audio.append(ByteArray(0), 16000, 1)
        audio.close()
        audio.close()
        assertFalse(file.exists())
        assertThrows(IOException::class.java) { PendingAudio.repair(file) }
        assertFalse("Repair must not create a missing recording", file.exists())
        val empty = fixture("empty.wav", byteArrayOf())
        assertEquals(0L, PendingAudio.repair(empty).dataBytes)
        assertThrows(IOException::class.java) {
            PendingAudio.forEachUploadChunk(empty) { fail("Empty audio must not be uploaded") }
        }
        assertEquals(44L, empty.length())
    }

    @Test(timeout = 10000) fun existingRecordingIsNeverOverwritten() {
        val file = fixture("existing.wav", byteArrayOf(1, 2, 3, 4))
        val original = file.readBytes()
        val audio = PendingAudio(file)
        audio.append(byteArrayOf(9, 9), 48000, 1)
        assertThrows(IOException::class.java) { audio.close() }
        assertArrayEquals(original, file.readBytes())
    }

    @Test(timeout = 10000) fun syncFailureStillReleasesLockAndClosesExactlyOnce() {
        val file = path("sync-failure.wav")
        val checkpoint = CountDownLatch(1)
        val channel = AtomicReference<ProbeChannel>()
        val audio = PendingAudio(file, 4) {
            ProbeChannel(openNew(it), afterWrite = { position -> if (position == 40L) checkpoint.countDown() }).also(channel::set)
        }
        audio.append(byteArrayOf(1, 2), 16000, 1)
        assertTrue(checkpoint.await(5, TimeUnit.SECONDS))
        channel.get().forceFails.set(true)
        assertThrows(IOException::class.java) { audio.close() }
        assertFalse(channel.get().isOpen)
        assertEquals(1, channel.get().closes.get())
        assertEquals(2L, PendingAudio.repair(file).dataBytes)
        assertThrows(IOException::class.java) { audio.close() }
        assertEquals(1, channel.get().closes.get())
    }

    @Test(timeout = 10000) fun partialWriteClosesResourcesAndPreservesActualBytesWithoutFabrication() {
        for (written in listOf(2, 3)) {
            val file = path("partial-$written.wav")
            val channel = AtomicReference<ProbeChannel>()
            val audio = PendingAudio(file, 4) {
                ProbeChannel(openNew(it), failDataAfter = written).also(channel::set)
            }
            audio.append(byteArrayOf(1, 2, 3, 4), 16000, 1)
            assertThrows(IOException::class.java) { audio.close() }
            assertFalse(channel.get().isOpen)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4).copyOf(written), payload(file))
            if (written == 2) {
                assertHeader(file, 16000, 1, 2)
                assertEquals(2L, PendingAudio.repair(file).dataBytes)
            } else {
                val original = file.readBytes()
                assertThrows(IOException::class.java) { PendingAudio.repair(file) }
                assertArrayEquals(original, file.readBytes())
                assertEquals(47L, file.length())
            }
        }
    }

    @Test(timeout = 10000) fun interruptedCloseFinishesDrainAndRestoresInterruption() {
        val file = path("interrupted.wav")
        val opening = CountDownLatch(1)
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)
        val done = CountDownLatch(1)
        val result = AtomicReference<Throwable?>()
        val interrupted = AtomicBoolean()
        val audio = PendingAudio(file, 4) {
            opening.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            openNew(it)
        }
        try {
            audio.append(byteArrayOf(1, 2), 16000, 1)
            assertTrue(opening.await(2, TimeUnit.SECONDS))
            val closer = Thread {
                started.countDown()
                try { audio.close() } catch (e: Throwable) { result.set(e) }
                finally { interrupted.set(Thread.currentThread().isInterrupted); done.countDown() }
            }
            closer.start()
            assertTrue(started.await(2, TimeUnit.SECONDS))
            closer.interrupt()
            release.countDown()
            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertTrue(result.get() is InterruptedIOException)
            assertTrue(interrupted.get())
            audio.close()
            assertHeader(file, 16000, 1, 2)
            assertEquals(2L, PendingAudio.repair(file).dataBytes)
        } finally {
            release.countDown()
            runCatching { audio.close() }
        }
    }

    @Test fun staleHeaderRepairUsesActualFileLengthIncludingUnsignedOldSizes() {
        for ((index, declared) in listOf(0L, 2L, 0xffff_ff00L).withIndex()) {
            val audio = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
            val file = fixture("stale-$index.wav", audio, 22050, 2, declared)
            assertEquals(PendingAudio.WavInfo(22050, 2, 8), PendingAudio.repair(file))
            assertHeader(file, 22050, 2, 8)
            assertArrayEquals(audio, payload(file))
        }
    }

    @Test fun unknownHeadersAndTruncatedFramesAreRejectedUnchanged() {
        val valid = fixtureBytes(byteArrayOf(1, 2, 3, 4), 16000, 2)
        val cases = listOf(
            ByteArray(48),
            valid.copyOf(20),
            valid.copyOf(47),
            valid.copyOf().also { it[20] = 3 }, // IEEE float is not PCM16.
            valid.copyOf().also { it[34] = 8 },
            valid.copyOf().also { it[28] = 1 }, // Incorrect byte rate.
            valid.copyOf().also { it[32] = 2 }, // Incorrect block alignment.
        )
        for ((index, bytes) in cases.withIndex()) {
            val file = path("bad-header-$index.wav").apply { writeBytes(bytes) }
            assertThrows(IOException::class.java) { PendingAudio.repair(file) }
            assertArrayEquals(bytes, file.readBytes())
        }
    }

    @Test fun standardRiffOverflowIsExplicitAndDoesNotTruncateTheSource() {
        val file = fixture("riff-limit.wav", byteArrayOf())
        val tooLong = 44L + (0xffff_ffffL - 36L) + 1L
        RandomAccessFile(file, "rw").use { it.setLength(tooLong) } // Sparse; no multi-GB allocation.
        assertThrows(IOException::class.java) { PendingAudio.repair(file) }
        assertEquals(tooLong, file.length())
    }

    @Test fun streamedChunksAreOrderedFrameAlignedAndCleanedBeforeTheNextCallback() {
        val bytes = ByteArray(40) { (it + 1).toByte() }
        val file = fixture("chunks.wav", bytes, 16000, 2)
        val original = file.readBytes()
        val collected = ByteArrayOutputStream()
        val parts = mutableListOf<File>()
        val lengths = mutableListOf<Long>()
        PendingAudio.forEachUploadChunk(file, maxPayloadBytes = 13) { part ->
            assertTrue(parts.all { !it.exists() })
            assertTrue(file.exists())
            assertEquals("upload", part.extension)
            val info = PendingAudio.repair(part)
            assertEquals(0L, info.dataBytes % 4)
            assertTrue(info.dataBytes in 1L..13L)
            assertHeader(part, 16000, 2, info.dataBytes)
            lengths.add(info.dataBytes)
            parts.add(part)
            collected.write(payload(part))
        }
        assertEquals(listOf(12L, 12L, 12L, 4L), lengths)
        assertTrue(parts.all { !it.exists() })
        assertArrayEquals(bytes, collected.toByteArray())
        assertArrayEquals(original, file.readBytes())
    }

    @Test fun failureOnSecondUploadCleansTemporaryFileAndRetainsOriginalRecording() {
        val file = fixture("upload-failure.wav", ByteArray(20) { (it + 1).toByte() })
        val original = file.readBytes()
        val parts = mutableListOf<File>()
        val error = IOException("Provider upload failed")
        val thrown = assertThrows(IOException::class.java) {
            PendingAudio.forEachUploadChunk(file, 8) { part ->
                parts.add(part)
                if (parts.size == 2) throw error
            }
        }
        assertSame(error, thrown)
        assertEquals(2, parts.size)
        assertTrue(parts.all { !it.exists() })
        assertArrayEquals(original, file.readBytes())
        assertEquals(20L, PendingAudio.repair(file).dataBytes) // Source lock was released.
    }

    @Test fun uploadRepairsAProcessDeathHeaderBeforeStreaming() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5, 6)
        val file = fixture("crashed.wav", bytes, declared = 0)
        val collected = ByteArrayOutputStream()
        PendingAudio.forEachUploadChunk(file, 4) { part -> collected.write(payload(part)) }
        assertArrayEquals(bytes, collected.toByteArray())
        assertHeader(file, 16000, 1, 6)
        assertTrue(file.exists())
    }

    @Test fun sustainedSilenceNearTheLimitIsPreferredWithoutDroppingOrDuplicatingSamples() {
        val data = ByteBuffer.allocate(12000 * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(12000) { sample -> data.putShort(if (sample in 9000 until 9850) 0 else 1000) }
        val file = fixture("silence.wav", data.array(), rate = 8000)
        val collected = ByteArrayOutputStream()
        val lengths = mutableListOf<Long>()
        PendingAudio.forEachUploadChunk(file, 20000) { part ->
            lengths.add(part.length() - 44)
            collected.write(payload(part))
        }
        assertEquals(listOf(18900L, 5100L), lengths)
        assertArrayEquals(data.array(), collected.toByteArray())
    }

    @Test fun absentOrTooShortSilenceFallsBackToTheExactFrameBoundary() {
        val data = ByteBuffer.allocate(12000 * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(12000) { sample ->
            data.putShort(0) // Quiet in one channel is insufficient.
            data.putShort(if (sample in 9500 until 9600) 0 else 1000)
        }
        val file = fixture("speech.wav", data.array(), rate = 8000, channels = 2)
        val lengths = mutableListOf<Long>()
        val collected = ByteArrayOutputStream()
        PendingAudio.forEachUploadChunk(file, 40003) { part ->
            lengths.add(part.length() - 44)
            collected.write(payload(part))
        }
        assertEquals(listOf(40000L, 8000L), lengths)
        assertArrayEquals(data.array(), collected.toByteArray())
    }

    @Test(timeout = 15000) fun defaultChunkSizeKeepsALongWavBelowProviderLimitUsingBoundedStreaming() {
        val dataBytes = PendingAudio.DEFAULT_UPLOAD_PAYLOAD_BYTES + 4
        val file = fixture("long.wav", byteArrayOf(), rate = 48000)
        RandomAccessFile(file, "rw").use { it.setLength(44 + dataBytes) }
        PendingAudio.repair(file)
        val original = digest(file)
        var parts = 0
        var total = 0L
        PendingAudio.forEachUploadChunk(file) { part ->
            parts++
            assertTrue(part.length() < 25_000_000L)
            val info = PendingAudio.repair(part)
            assertHeader(part, 48000, 1, info.dataBytes)
            assertTrue(info.dataBytes <= 20L * 1024 * 1024)
            total += info.dataBytes
        }
        assertEquals(2, parts)
        assertEquals(dataBytes, total)
        assertArrayEquals(original, digest(file))
        assertEquals(listOf(file.name), temporary.root.listFiles()!!.map { it.name })
    }

    @Test fun invalidUploadLimitsAreRejectedWithoutChangingTheSource() {
        val file = fixture("limits.wav", byteArrayOf(1, 2, 3, 4), channels = 2)
        val original = file.readBytes()
        for (limit in listOf(0L, -1L, 3L, 25_000_000L - 44, Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                PendingAudio.forEachUploadChunk(file, limit) { fail("Invalid limit must not produce an upload") }
            }
        }
        assertArrayEquals(original, file.readBytes())
    }

    private fun path(name: String) = File(temporary.root, name)

    private fun fixture(
        name: String, data: ByteArray, rate: Int = 16000, channels: Int = 1,
        declared: Long = data.size.toLong(),
    ) = path(name).apply { writeBytes(fixtureBytes(data, rate, channels, declared)) }

    private fun fixtureBytes(data: ByteArray, rate: Int, channels: Int, declared: Long = data.size.toLong()): ByteArray {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt((36 + declared).toInt())
        header.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1)
        header.putShort(channels.toShort()).putInt(rate).putInt(rate * channels * 2)
        header.putShort((channels * 2).toShort()).putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII)).putInt(declared.toInt())
        return header.array() + data
    }

    private fun assertHeader(file: File, rate: Int, channels: Int, dataBytes: Long) {
        val header = ByteArray(44)
        RandomAccessFile(file, "r").use { it.readFully(header) }
        assertArrayEquals(fixtureBytes(ByteArray(0), rate, channels, dataBytes), header)
        assertEquals(44 + dataBytes, file.length())
    }

    private fun payload(file: File) = file.readBytes().copyOfRange(44, file.length().toInt())

    private fun digest(file: File): ByteArray {
        val result = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                result.update(buffer, 0, count)
            }
        }
        return result.digest()
    }

    private fun openNew(file: File): FileChannel = FileChannel.open(
        file.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE,
    )

    /** Real file I/O with controlled failure points, so tests observe actual on-disk headers/bytes. */
    private class ProbeChannel(
        private val delegate: FileChannel,
        private val afterWrite: (Long) -> Unit = {},
        private val failDataAfter: Int? = null,
    ) : FileChannel() {
        val closes = AtomicInteger()
        val forceFails = AtomicBoolean()
        private val dataFailed = AtomicBoolean()
        override fun write(src: ByteBuffer, position: Long): Int {
            if (position >= 44 && failDataAfter != null && dataFailed.compareAndSet(false, true)) {
                val prefix = src.slice()
                prefix.limit(failDataAfter)
                delegate.write(prefix, position)
                throw IOException("Simulated partial disk write")
            }
            return delegate.write(src, position).also { afterWrite(position) }
        }
        override fun force(metaData: Boolean) {
            if (forceFails.get()) throw IOException("Simulated sync failure")
            delegate.force(metaData)
        }
        override fun implCloseChannel() { closes.incrementAndGet(); delegate.close() }
        override fun read(dst: ByteBuffer) = delegate.read(dst)
        override fun read(dsts: Array<out ByteBuffer>, offset: Int, length: Int) = delegate.read(dsts, offset, length)
        override fun read(dst: ByteBuffer, position: Long) = delegate.read(dst, position)
        override fun write(src: ByteBuffer) = delegate.write(src)
        override fun write(srcs: Array<out ByteBuffer>, offset: Int, length: Int) = delegate.write(srcs, offset, length)
        override fun position() = delegate.position()
        override fun position(newPosition: Long): FileChannel { delegate.position(newPosition); return this }
        override fun size() = delegate.size()
        override fun truncate(size: Long): FileChannel { delegate.truncate(size); return this }
        override fun transferTo(position: Long, count: Long, target: WritableByteChannel) = delegate.transferTo(position, count, target)
        override fun transferFrom(src: ReadableByteChannel, position: Long, count: Long) = delegate.transferFrom(src, position, count)
        override fun map(mode: MapMode, position: Long, size: Long): MappedByteBuffer = delegate.map(mode, position, size)
        override fun lock(position: Long, size: Long, shared: Boolean): FileLock = delegate.lock(position, size, shared)
        override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock? = delegate.tryLock(position, size, shared)
    }
}
