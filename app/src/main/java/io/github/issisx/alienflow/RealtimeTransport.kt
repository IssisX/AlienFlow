package io.github.issisx.alienflow

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.*
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException

/**
 * Direct 24 kHz PCM capture plus the OpenAI Realtime WebSocket protocol.
 *
 * The previous native WebRTC library is deliberately not part of this owner path:
 * the Fold6 supplied an Android tombstone proving SIGABRT inside that exact ELF,
 * while the app's direct AudioRecord probe captured 70,560 samples successfully.
 */
class RealtimeTransport internal constructor(
    private val context: Context,
    private val audio: PendingAudio,
    private val listener: Listener,
    private val microphoneFactory: () -> MicrophoneInput = { AndroidMicrophoneInput(context) },
    captureDispatcher: CoroutineDispatcher = Dispatchers.IO,
    controlDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val elapsedMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    interface Listener {
        fun event(value: JSONObject)
        fun captured()
        fun connected() {}
        fun turnCommitted()
        fun interrupted()
        fun reconnected()
        fun failure(message: String)
    }

    private val app = context.applicationContext as AlienApp
    private val main = Handler(Looper.getMainLooper())
    private val state = Any()
    private val controlScope = CoroutineScope(SupervisorJob() + controlDispatcher)
    private val captureScope = CoroutineScope(SupervisorJob() + captureDispatcher)
    private val vad = ClientSideVad()
    private val coverage = AudioCoverage()

    @Volatile private var closed = false
    @Volatile private var capturing = true
    @Volatile private var localOnly = false
    @Volatile private var microphone: MicrophoneInput? = null
    @Volatile private var terminalFailure = false
    private val startup = StartupAudioBuffer()
    private val firstConnection = CompletableDeferred<Boolean>()

    private var api: OpenAiClient? = null
    private var key = ""
    private var language = ""
    private var vocabulary = ""
    private var socket: WebSocket? = null
    private var socketGeneration = 0
    private var sessionReady = false
    private var reconnecting = false
    private var interruptionReported = false
    private var remoteBufferedBytes = 0L
    private var commitSequence = 0L
    private var editorEpoch = 0L
    private var reconnectJob: Job? = null
    private var captureJob: Job? = null
    private var firstSample = true

    val hasPendingCoverage: Boolean get() = synchronized(state) { coverage.uncertain }
    val hasUnacknowledgedCommit: Boolean get() = synchronized(state) { coverage.hasUnacknowledgedCommit }
    // Old installs may have 0 (off). Never allow indefinite quiet capture.
    var silenceStopSeconds = 30
        set(value) { field = if (value <= 0) 30 else value.coerceAtMost(30) }
    var onSilenceStop: (() -> Unit)? = null

    fun connect(api: OpenAiClient, key: String, language: String, vocabulary: String) {
        this.api = api
        this.key = key
        this.language = language
        this.vocabulary = vocabulary
        startCapture()
        openSocket(reconnect = false)
    }

    private fun openSocket(reconnect: Boolean) {
        if (closed || !capturing) return
        val generation = synchronized(state) {
            socketGeneration += 1
            sessionReady = false
            remoteBufferedBytes = 0L
            reconnecting = reconnect
            socketGeneration
        }
        app.crashJournal.mark("websocket.connecting")
        try {
            val pendingSocket = api!!.realtimeWebSocket(key, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val accepted = synchronized(state) {
                        if (closed || terminalFailure || generation != socketGeneration) false
                        else {
                            socket = webSocket
                            true
                        }
                    }
                    if (!accepted) {
                        webSocket.cancel()
                        return
                    }
                    app.crashJournal.mark("websocket.open")
                    if (!webSocket.send(RealtimeProtocol.sessionUpdate(language, vocabulary))) {
                        transportLost(generation, webSocket, null, "Realtime session setup could not be sent")
                        return
                    }
                    app.crashJournal.mark("websocket.session-update")
                    main.postDelayed({
                        val waiting = synchronized(state) {
                            !closed && !terminalFailure && generation == socketGeneration && !sessionReady
                        }
                        if (waiting) transportLost(
                            generation,
                            webSocket,
                            null,
                            "Realtime session setup timed out",
                        )
                    }, 15_000L)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (synchronized(state) { closed || generation != socketGeneration || socket !== webSocket }) return
                    val value = try { JSONObject(text) } catch (_: Exception) {
                        postFailure("Invalid realtime event received", generation)
                        return
                    }
                    val type = value.optString("type")
                    if (RealtimeProtocol.isSessionReadyEvent(type)) {
                        val problem = RealtimeHandshake.acknowledgementProblem(value)
                        if (problem != null) {
                            postFailure(problem.message(), generation, problem)
                        } else sessionBecameReady(generation, webSocket, reconnect)
                        return
                    }
                    main.post {
                        if (!closed && generation == synchronized(state) { socketGeneration }) {
                            if (type == "input_audio_buffer.committed") {
                                synchronized(state) { coverage.acknowledge(value.optString("item_id")) }
                            }
                            val item = value.optString("item_id", value.optJSONObject("item")?.optString("id").orEmpty())
                            if (item.isNotBlank()) value.put("_editor_epoch", synchronized(state) {
                                coverage.editorEpochFor(item) ?: -1L
                            })
                            if (type == "error") app.crashJournal.liveConnectionFailure(LiveConnectionProblem.event(value))
                            listener.event(value.put("_connection_generation", generation))
                        }
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    transportLost(generation, webSocket, null, "Realtime connection closed")
                }

                override fun onFailure(webSocket: WebSocket, problem: Throwable, response: Response?) {
                    val failure = LiveConnectionProblem.http(response)
                    transportLost(generation, webSocket, response, failure.message(), failure)
                }
            })
            val retain = synchronized(state) {
                if (closed || terminalFailure || generation != socketGeneration) false
                else { socket = pendingSocket; true }
            }
            if (!retain) pendingSocket.cancel()
        } catch (problem: Exception) {
            app.crashJournal.recordFailure("websocket.failure", problem)
            postFailure("Realtime connection could not start")
        }
    }

    private fun sessionBecameReady(generation: Int, webSocket: WebSocket, reconnect: Boolean) {
        var failed: WebSocket? = null
        synchronized(state) {
            if (closed || terminalFailure || generation != socketGeneration || socket !== webSocket || sessionReady) return
            sessionReady = true
            localOnly = false
            interruptionReported = false
            reconnecting = false
            // The same lock orders startup replay, turn commits, and newly captured frames.
            var replayEpoch = editorEpoch
            val flushed = startup.drain { entry ->
                when (entry) {
                    is StartupAudioBuffer.Entry.Audio -> { replayEpoch = entry.editorEpoch; appendLocked(webSocket, entry.pcm) }
                    StartupAudioBuffer.Entry.Commit -> commitLocked(webSocket, replayEpoch)
                }
            }
            if (!flushed) failed = breakStreamLocked()
            else firstConnection.complete(true)
        }
        if (failed != null) { reportSendFailure(failed); return }
        app.crashJournal.mark("websocket.session-ready")
        main.post {
            if (!closed && synchronized(state) { generation == socketGeneration && sessionReady }) {
                listener.connected()
                if (reconnect && capturing) listener.reconnected()
            }
        }
    }

    private fun startCapture() {
        synchronized(state) {
            if (closed || !capturing || captureJob != null) return
            captureJob = captureScope.launch { captureLoop() }
        }
    }

    private suspend fun captureLoop() {
        var owned: MicrophoneInput? = null
        try {
            if (!capturing || closed) return
            app.crashJournal.mark("audiorecord.creating")
            val current = microphoneFactory()
            owned = current
            microphone = current
            if (!capturing || closed) return
            current.start()
            if (!capturing || closed) return
            app.crashJournal.mark("audiorecord.started")
            val captureStartedAt = elapsedMillis()
            val samples = ShortArray(RealtimeProtocol.CHUNK_SAMPLES)
            while (capturing && currentCoroutineContext().isActive) {
                // The network cutoff must not depend solely on a responsive UI looper.
                if (elapsedMillis() - captureStartedAt >= 300_000L) {
                    postFailure("Five-minute safety limit reached; recording stopped and audio saved")
                    break
                }
                val count = current.read(samples)
                if (count > 0) {
                    val pcm = RealtimeProtocol.pcm16LittleEndian(samples, count)
                    audio.append(pcm, RealtimeProtocol.SAMPLE_RATE, RealtimeProtocol.CHANNELS)
                    if (firstSample) {
                        firstSample = false
                        main.post { if (!closed && capturing) listener.captured() }
                    }
                    sendAudio(pcm)
                    val decision = vad.inspect(pcm, elapsedMillis(), silenceStopSeconds)
                    if (decision.commit) sendCommit()
                    if (decision.silenceStop) {
                        capturing = false
                        stopRecorder()
                        main.post { if (!closed) onSilenceStop?.invoke() }
                    }
                } else if (count < 0) {
                    if (!capturing || closed) break
                    throw IOException("AudioRecord read failed ($count)")
                } else delay(5)
            }
        } catch (_: CancellationException) {
            // Explicit stop/close owns cancellation.
        } catch (problem: Throwable) {
            if (!closed && capturing) {
                app.crashJournal.recordFailure("audiorecord.failure", problem)
                postFailure("Android microphone capture failed")
            }
        } finally {
            synchronized(state) { if (microphone === owned) microphone = null }
            try { owned?.stop() } catch (_: Exception) { }
            try { owned?.release() } catch (_: Exception) { }
            app.crashJournal.mark("audiorecord.released")
        }
    }

    private fun sendAudio(pcm: ByteArray) {
        var failed: WebSocket? = null
        var overflow = false
        synchronized(state) {
            if (closed || terminalFailure || localOnly) return
            val current = socket
            if (!sessionReady || current == null) {
                if (!startup.append(pcm, editorEpoch)) {
                    overflow = true
                    failed = breakStreamLocked()
                }
            } else if (!appendLocked(current, pcm)) failed = breakStreamLocked()
        }
        if (failed != null || overflow) reportSendFailure(failed)
    }

    private fun appendLocked(current: WebSocket, pcm: ByteArray): Boolean {
        // OkHttp counts encoded messages, not the original PCM bytes.
        if (current.queueSize() + (pcm.size * 4L / 3L) + 128 > RealtimeProtocol.MAX_WEBSOCKET_QUEUE_BYTES ||
            !current.send(RealtimeProtocol.appendEvent(pcm))) return false
        remoteBufferedBytes += pcm.size
        return true
    }

    fun commit(): Boolean = sendCommit()

    /** The same lock serializes capture frames, old-field commit, and new-field audio.
     * Capture/connection stay alive; delayed old-item results retain the old epoch. */
    fun switchEditor(nextEpoch: Long) {
        var failed: WebSocket? = null
        synchronized(state) {
            if (nextEpoch == editorEpoch) return
            val current = socket
            if (!closed && !terminalFailure && !localOnly) {
                if (sessionReady && current != null) {
                    if (!commitLocked(current)) failed = breakStreamLocked()
                } else startup.commit()
            }
            editorEpoch = nextEpoch
        }
        if (failed != null) reportSendFailure(failed)
    }

    private fun sendCommit(): Boolean {
        var failed: WebSocket? = null
        var committed = false
        synchronized(state) {
            if (closed || terminalFailure || localOnly) return false
            val current = socket
            if (!sessionReady || current == null) {
                startup.commit() // Announce the turn only when its actual commit is sent.
                return false
            }
            if (remoteBufferedBytes <= 0L) return false
            committed = commitLocked(current)
            if (!committed) failed = breakStreamLocked()
        }
        if (failed != null) reportSendFailure(failed)
        return committed
    }

    private fun commitLocked(current: WebSocket, targetEpoch: Long = editorEpoch): Boolean {
        if (remoteBufferedBytes <= 0L) return true
        val capturedBytes = remoteBufferedBytes
        // Preserve a short final frame: silence extends only the network envelope to 100ms.
        // The authoritative recovery WAV and coverage counts contain captured audio only.
        val padding = (RealtimeProtocol.MIN_COMMIT_BYTES - capturedBytes).coerceAtLeast(0L).toInt()
        if (padding > 0 && !appendLocked(current, ByteArray(padding))) return false
        val id = "commit-$socketGeneration-${++commitSequence}"
        if (!current.send(RealtimeProtocol.commitEvent(id))) return false
        coverage.sent(id, capturedBytes, targetEpoch)
        remoteBufferedBytes = 0L
        main.post { if (!closed) listener.turnCommitted() }
        return true
    }

    /** Caller holds state. The WAV becomes the sole authority after any discontinuity. */
    private fun breakStreamLocked(): WebSocket? {
        coverage.markUncertain()
        localOnly = true
        sessionReady = false
        startup.clear()
        firstConnection.complete(false)
        socketGeneration += 1
        return socket.also { socket = null }
    }

    private fun reportSendFailure(failed: WebSocket?) {
        failed?.cancel()
        val notify = synchronized(state) {
            if (interruptionReported) false else { interruptionReported = true; true }
        }
        if (notify) main.post { if (!closed && capturing) listener.interrupted() }
    }

    fun rejectEmptyCommit(clientEventId: String): Boolean =
        synchronized(state) {
            app.crashJournal.rejectedAudioCommit(coverage.bytesFor(clientEventId) ?: -1L, capturing, socketGeneration)
            coverage.rejectEmpty(clientEventId)
        }

    fun finishCapture(complete: (Boolean) -> Unit) {
        capturing = false
        reconnectJob?.cancel()
        reconnecting = false
        stopRecorder()
        controlScope.launch {
            captureJob?.join()
            if (closed) return@launch
            // A quick Stop during startup still sends the captured words once setup completes.
            val waiting = synchronized(state) { !localOnly && !sessionReady && startup.bytes > 0 }
            if (waiting && withTimeoutOrNull(15_000L) { firstConnection.await() } != true) {
                val old = synchronized(state) { breakStreamLocked() }
                old?.cancel()
            }
            if (closed) return@launch
            synchronized(state) { if (localOnly || !sessionReady) coverage.markUncertain() }
            sendCommit()
            if (!closed) complete(synchronized(state) { !coverage.uncertain })
        }
    }

    private fun stopRecorder() {
        try { microphone?.stop() }
        catch (problem: Exception) { app.crashJournal.recordFailure("audiorecord.failure", problem) }
    }

    fun continueLocally() {
        if (closed || !capturing) return
        val old = synchronized(state) { breakStreamLocked() }
        old?.cancel()
        reconnectJob?.cancel()
    }

    private fun transportLost(
        generation: Int,
        webSocket: WebSocket,
        response: Response?,
        message: String,
        detail: LiveConnectionProblem? = null,
    ) {
        var terminal = false
        var notifyInterrupted = false
        synchronized(state) {
            if (closed || generation != socketGeneration || (socket != null && socket !== webSocket)) return
            // A failed HTTP upgrade may arrive before onOpen or even before newWebSocket returns.
            // Invalidate that attempt so the returned handle cannot revive it.
            socketGeneration += 1
            socket = null
            sessionReady = false
            val startupBytes = startup.bytes
            startup.clear()
            firstConnection.complete(false)
            if (response?.code in setOf(401, 403, 429)) {
                terminal = true
            } else if (capturing && captureJob != null) {
                localOnly = true
                coverage.markUncertain()
                if (!interruptionReported) {
                    interruptionReported = true
                    notifyInterrupted = true
                }
            } else if (capturing) {
                terminal = true
            } else if (remoteBufferedBytes > 0L || startupBytes > 0 || coverage.uncertain) {
                coverage.markUncertain()
                terminal = true
            }
        }
        detail?.let { app.crashJournal.liveConnectionFailure(it) }
        webSocket.cancel()
        if (terminal) postFailure(message)
        else if (notifyInterrupted) main.post { if (!closed && capturing) listener.interrupted() }
    }

    private fun postFailure(message: String, generation: Int? = null, detail: LiveConnectionProblem? = null) {
        val old = synchronized(state) {
            if (closed || terminalFailure || (generation != null && generation != socketGeneration)) return
            terminalFailure = true
            capturing = false
            breakStreamLocked()
        }
        detail?.let { app.crashJournal.liveConnectionFailure(it) }
        stopRecorder()
        old?.cancel()
        main.post { if (!closed) listener.failure(message) }
    }

    fun close() {
        if (closed) return
        closed = true
        capturing = false
        reconnecting = false
        reconnectJob?.cancel()
        stopRecorder()
        captureJob?.cancel()
        controlScope.cancel()
        captureScope.cancel()
        val current = synchronized(state) {
            socketGeneration += 1
            sessionReady = false
            localOnly = true
            startup.clear()
            firstConnection.complete(false)
            socket.also { socket = null }
        }
        current?.cancel()
        key = ""
    }
}
