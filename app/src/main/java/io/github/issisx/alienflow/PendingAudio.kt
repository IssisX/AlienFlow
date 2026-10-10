package io.github.issisx.alienflow

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * Bounded copy of the SAME microphone's little-endian PCM16 frames, never a second recorder.
 * The caller must verify the capture callback's audio format before calling [append].
 * Construction and append do no file I/O; [close] drains and syncs, so call it on an I/O worker
 * after stopping capture. A recording without any nonempty frames does not create a file.
 */
class PendingAudio internal constructor(
    val file: File,
    queueCapacity: Int,
    private val openOutput: (File) -> FileChannel,
) : Closeable {
    constructor(file: File) : this(file, QUEUE_CAPACITY, ::openNewRecording)

    data class WavInfo(val sampleRate: Int, val channels: Int, val dataBytes: Long) {
        val bytesPerFrame: Int get() = channels * 2
    }

    private val format = AtomicReference<WavInfo?>()
    private val storageFailure = AtomicReference<IOException?>()
    private val closing = AtomicBoolean()
    private val admissions = AtomicInteger()
    private val queuedBytes = AtomicLong()
    private val finished = CountDownLatch(1)

    // Only the serialized writer touches these resources, including its termination hook.
    private var output: FileChannel? = null
    private var outputLock: FileLock? = null
    private var writtenBytes = 0L
    private var writeFailed = false
    private var lastSyncNanos = 0L
    private val writer = object : ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(queueCapacity),
        ThreadFactory { task -> Thread(task, "AlienFlow-audio-writer").apply { isDaemon = true } },
    ) {
        override fun terminated() {
            try { finishOutput() } finally { finished.countDown() }
        }
    }

    /**
     * Copies and offers one complete PCM16 buffer without waiting for disk or queue capacity.
     * Metadata is fixed by the first nonempty frame. Overflow or a format/storage failure is
     * explicit; no later frame can turn a recording with missing audio into a successful one.
     * Calls which begin after close has started are ignored. Accepted work is drained by close.
     */
    fun append(data: ByteArray, rate: Int, channelCount: Int) {
        if (closing.get()) return
        admissions.incrementAndGet()
        try {
            if (closing.get()) return
            storageFailure.get()?.let { throw it }
            val incoming = try { pcmInfo(rate, channelCount, 0) }
                catch (e: IOException) { throw remember(e) }
            if (data.size > MAX_CALLBACK_BYTES || data.size % incoming.bytesPerFrame != 0) {
                throw remember(IOException("Invalid PCM16 callback size; recording retained"))
            }
            if (data.isEmpty()) return
            format.compareAndSet(null, incoming)
            if (format.get() != incoming) {
                throw remember(IOException("Microphone format changed; recording retained"))
            }
            reserveBytes(data.size)
            var enqueued = false
            try {
                val frame = data.copyOf()
                writer.execute {
                    try { writeFrame(frame, incoming) }
                    finally { queuedBytes.addAndGet(-frame.size.toLong()) }
                }
                enqueued = true
            } catch (e: RejectedExecutionException) {
                throw remember(IOException("Audio storage queue is full; recording retained", e))
            } finally {
                if (!enqueued) queuedBytes.addAndGet(-data.size.toLong())
            }
        } finally {
            // Shutdown cannot overtake an append that passed the second closing check.
            if (admissions.decrementAndGet() == 0 && closing.get()) writer.shutdown()
        }
    }

    private fun reserveBytes(count: Int) {
        while (true) {
            val previous = queuedBytes.get()
            if (previous + count > MAX_QUEUED_BYTES) {
                throw remember(IOException("Audio storage cannot keep up; recording retained"))
            }
            if (queuedBytes.compareAndSet(previous, previous + count)) return
        }
    }

    private fun remember(error: IOException): IOException {
        storageFailure.compareAndSet(null, error)
        return storageFailure.get()!!
    }

    private fun writeFrame(data: ByteArray, info: WavInfo) {
        if (writeFailed) return
        try {
            val target = output ?: openOutput(file).also {
                output = it // Own it before any operation that could fail.
                outputLock = exclusiveLock(it)
                writeHeader(it, info)
                // Preserve authoritative format before putting any audio on disk.
                it.force(true)
                lastSyncNanos = System.nanoTime()
            }
            if (writtenBytes + data.size > MAX_DATA_BYTES) {
                throw IOException("Recording reached the standard WAV size limit; recording retained")
            }
            writeFully(target, ByteBuffer.wrap(data), HEADER_BYTES + writtenBytes)
            writtenBytes += data.size
            writeSizes(target, writtenBytes)
            val now = System.nanoTime()
            if (now - lastSyncNanos >= SYNC_INTERVAL_NANOS) {
                target.force(false)
                lastSyncNanos = now
            }
        } catch (e: Exception) {
            writeFailed = true
            remember(IOException("Could not store microphone audio; recording retained", e))
        }
    }

    private fun finishOutput() {
        val target = output ?: return
        try {
            // If a write failed partway through, inspect actual bytes, never claim queued bytes.
            repairChannel(target)
        } catch (e: Exception) {
            remember(IOException("Could not finalize WAV header; recording retained", e))
        } finally {
            try { target.force(true) }
            catch (e: Exception) { remember(IOException("Could not sync recording", e)) }
            try { outputLock?.release() }
            catch (e: Exception) { remember(IOException("Could not release recording lock", e)) }
            try { target.close() }
            catch (e: Exception) { remember(IOException("Could not close recording", e)) }
            outputLock = null
            output = null
        }
    }

    /**
     * Idempotent: waits for accepted work and resource cleanup, and reports any stored failure.
     * Interruption does not abandon a live writer; cleanup finishes before interruption is restored.
     * This intentionally waits for file I/O rather than timing out and leaking an open recording.
     */
    override fun close() {
        closing.set(true)
        if (admissions.get() == 0) writer.shutdown()
        var interrupted = false
        while (true) {
            try { finished.await(); break }
            catch (_: InterruptedException) { interrupted = true }
        }
        if (interrupted) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Interrupted while finalizing recording").also {
                storageFailure.get()?.let(it::addSuppressed)
            }
        }
        storageFailure.get()?.let { throw it }
    }

    companion object {
        const val DEFAULT_UPLOAD_PAYLOAD_BYTES = 20L * 1024 * 1024
        private const val HEADER_BYTES = 44L
        private const val MAX_DATA_BYTES = 0xffff_ffffL - 36L
        private const val PROVIDER_FILE_LIMIT_BYTES = 25_000_000L
        private const val QUEUE_CAPACITY = 256
        private const val MAX_CALLBACK_BYTES = 64 * 1024
        private const val MAX_QUEUED_BYTES = 1024L * 1024
        private const val COPY_BUFFER_BYTES = 64 * 1024
        private const val SYNC_INTERVAL_NANOS = 1_000_000_000L
        private const val SILENCE_AMPLITUDE = 256

        private fun openNewRecording(file: File): FileChannel = FileChannel.open(
            file.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.READ,
            StandardOpenOption.WRITE,
        )

        private fun exclusiveLock(channel: FileChannel): FileLock = try {
            channel.tryLock() ?: throw IOException("Recording is already in use")
        } catch (e: OverlappingFileLockException) {
            throw IOException("Recording is already in use", e)
        }

        private fun <T> withRecording(file: File, block: (FileChannel) -> T): T =
            // No CREATE: a missing source must never silently become an empty recording.
            FileChannel.open(file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE).use { input ->
                exclusiveLock(input).use { block(input) }
            }

        /**
         * Validates this app's canonical PCM16 WAV and durably repairs stale RIFF/data sizes from
         * actual file length. Requires an inactive recording; an active writer holds its lock.
         * Unknown/legacy zero headers and incomplete terminal frames are retained and rejected,
         * never guessed, padded, or truncated. Valid empty WAVs return dataBytes = 0.
         */
        fun repair(file: File): WavInfo = withRecording(file, ::repairChannel)

        /**
         * Streams ordered, frame-aligned WAV parts, each under the provider's 25 MB file limit.
         * [block] must upload/read each file synchronously before returning; it must not retain
         * the temporary path. Temporary files have a .upload suffix to avoid pending-WAV UI;
         * send them with multipart filename "recording.wav" and MIME type "audio/wav".
         * Every part is deleted in finally, including upload failure/cancellation. This method
         * never deletes the source; its owner must first durably save the complete result.
         */
        fun forEachUploadChunk(
            file: File,
            maxPayloadBytes: Long = DEFAULT_UPLOAD_PAYLOAD_BYTES,
            block: (File) -> Unit,
        ) {
            require(maxPayloadBytes > 0 && maxPayloadBytes < PROVIDER_FILE_LIMIT_BYTES - HEADER_BYTES) {
                "Upload payload plus WAV header must be smaller than 25 MB"
            }
            withRecording(file) { input ->
                val info = repairChannel(input)
                if (info.dataBytes == 0L) throw IOException("Recording contains no PCM audio")
                val limit = maxPayloadBytes / info.bytesPerFrame * info.bytesPerFrame
                require(limit > 0) { "Upload payload must fit one PCM16 frame" }
                val buffer = ByteBuffer.allocate(COPY_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
                var offset = 0L
                while (offset < info.dataBytes) {
                    val remaining = info.dataBytes - offset
                    val count = if (remaining <= limit) remaining
                        else silenceBoundary(input, info, offset, limit, buffer)
                    val part = File.createTempFile("alienflow-upload-", ".upload", file.absoluteFile.parentFile)
                    var failure: Throwable? = null
                    try {
                        FileChannel.open(part.toPath(), StandardOpenOption.WRITE).use { output ->
                            writeHeader(output, info.copy(dataBytes = count))
                            var copied = 0L
                            while (copied < count) {
                                val amount = min(buffer.capacity().toLong(), count - copied).toInt()
                                buffer.clear(); buffer.limit(amount)
                                readFully(input, buffer, HEADER_BYTES + offset + copied)
                                buffer.flip()
                                writeFully(output, buffer, HEADER_BYTES + copied)
                                copied += amount
                            }
                        }
                        block(part)
                    } catch (e: Throwable) {
                        failure = e
                        throw e
                    } finally {
                        if (!part.delete() && part.exists()) {
                            val cleanup = IOException("Could not remove temporary audio upload")
                            if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
                        }
                    }
                    offset += count
                }
            }
        }

        private fun pcmInfo(rate: Int, channels: Int, bytes: Long): WavInfo {
            if (rate <= 0 || channels !in 1..32767 || rate.toLong() * channels * 2 > 0xffff_ffffL) {
                throw IOException("Unsupported PCM16 metadata")
            }
            return WavInfo(rate, channels, bytes)
        }

        private fun repairChannel(channel: FileChannel): WavInfo {
            val size = channel.size()
            if (size < HEADER_BYTES) throw IOException("Recording has no complete WAV header")
            val header = ByteBuffer.allocate(HEADER_BYTES.toInt()).order(ByteOrder.LITTLE_ENDIAN)
            readFully(channel, header, 0)
            val bytes = header.array()
            fun tag(offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)
            fun uint(offset: Int) = header.getInt(offset).toLong() and 0xffff_ffffL
            fun ushort(offset: Int) = header.getShort(offset).toInt() and 0xffff
            if (tag(0) != "RIFF" || tag(8) != "WAVE" || tag(12) != "fmt " || tag(36) != "data" ||
                uint(16) != 16L || ushort(20) != 1 || ushort(34) != 16) {
                throw IOException("Recording is not a supported PCM16 WAV")
            }
            val rate = uint(24)
            if (rate > Int.MAX_VALUE) throw IOException("Unsupported WAV sample rate")
            val info = pcmInfo(rate.toInt(), ushort(22), size - HEADER_BYTES)
            if (ushort(32) != info.bytesPerFrame || uint(28) != rate * info.bytesPerFrame) {
                throw IOException("WAV metadata is inconsistent")
            }
            if (info.dataBytes > MAX_DATA_BYTES) throw IOException("Recording exceeds the standard WAV size limit")
            if (info.dataBytes % info.bytesPerFrame != 0L) {
                throw IOException("Recording ends with an incomplete PCM16 frame; original retained")
            }
            if (uint(4) != info.dataBytes + 36 || uint(40) != info.dataBytes) {
                writeSizes(channel, info.dataBytes)
                channel.force(true)
            }
            return info
        }

        private fun writeHeader(channel: FileChannel, info: WavInfo) {
            val header = ByteBuffer.allocate(HEADER_BYTES.toInt()).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt((info.dataBytes + 36).toInt())
            header.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1)
            header.putShort(info.channels.toShort()).putInt(info.sampleRate)
            header.putInt((info.sampleRate.toLong() * info.bytesPerFrame).toInt())
            header.putShort(info.bytesPerFrame.toShort()).putShort(16)
            header.put("data".toByteArray(Charsets.US_ASCII)).putInt(info.dataBytes.toInt())
            header.flip()
            writeFully(channel, header, 0)
        }

        private fun writeSizes(channel: FileChannel, dataBytes: Long) {
            val size = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            size.putInt((dataBytes + 36).toInt()).flip()
            writeFully(channel, size, 4)
            size.clear(); size.putInt(dataBytes.toInt()).flip()
            writeFully(channel, size, 40)
        }

        private fun readFully(channel: FileChannel, buffer: ByteBuffer, start: Long) {
            var position = start
            while (buffer.hasRemaining()) {
                val count = channel.read(buffer, position)
                if (count <= 0) throw IOException("Recording was truncated while reading")
                position += count
            }
        }

        private fun writeFully(channel: FileChannel, buffer: ByteBuffer, start: Long) {
            var position = start
            while (buffer.hasRemaining()) {
                val count = channel.write(buffer, position)
                if (count <= 0) throw IOException("Could not write recording bytes")
                position += count
            }
        }

        private fun silenceBoundary(
            input: FileChannel, info: WavInfo, offset: Long, limit: Long, buffer: ByteBuffer,
        ): Long {
            // Search at most the last two seconds / 10% of this part for 100 ms of quiet.
            // Without sufficient quiet, split exactly at the frame-aligned size boundary.
            val frame = info.bytesPerFrame
            val search = min(limit / 10, info.sampleRate.toLong() * frame * 2) / frame * frame
            val minimumQuietFrames = (info.sampleRate / 10).coerceAtLeast(1).toLong()
            if (search < minimumQuietFrames * frame) return limit
            val start = limit - search
            val capacity = buffer.capacity() / frame * frame
            var scanned = 0L
            var quietFrames = 0L
            var boundary = limit
            fun rememberQuietEnd(end: Long) {
                if (quietFrames >= minimumQuietFrames) {
                    boundary = end - (minimumQuietFrames / 2) * frame
                }
            }
            while (scanned < search) {
                val amount = min(capacity.toLong(), search - scanned).toInt()
                buffer.clear(); buffer.limit(amount)
                readFully(input, buffer, HEADER_BYTES + offset + start + scanned)
                buffer.flip()
                var position = 0
                while (position < amount) {
                    var quiet = true
                    for (sample in position until position + frame step 2) {
                        val value = buffer.getShort(sample).toInt()
                        if (value < -SILENCE_AMPLITUDE || value > SILENCE_AMPLITUDE) { quiet = false; break }
                    }
                    if (quiet) quietFrames++
                    else { rememberQuietEnd(start + scanned + position); quietFrames = 0 }
                    position += frame
                }
                scanned += amount
            }
            rememberQuietEnd(limit)
            return boundary
        }
    }
}
