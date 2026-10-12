package io.github.issisx.alienflow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
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
class RealtimeTransport(
    private val context: Context,
    private val audio: PendingAudio,
    private val listener: Listener,
) {
    interface Listener {
        fun event(value: JSONObject)
        fun captured()
        fun turnCommitted()
        fun interrupted()
        fun reconnected()
        fun failure(message: String)
    }

    private val app = context.applicationContext as AlienApp
    private val main = Handler(Looper.getMainLooper())
    private val state = Any()
    private val controlScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vad = ClientSideVad()
    private val coverage = AudioCoverage()

    @Volatile private var closed = false
    @Volatile private var capturing = true
    @Volatile private var localOnly = false
    @Volatile private var recorder: AudioRecord? = null

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
    private var reconnectJob: Job? = null
    private var captureJob: Job? = null
    private var firstSample = true

    val hasPendingCoverage: Boolean get() = synchronized(state) { coverage.uncertain }
    var silenceStopSeconds = 0
    var onSilenceStop: (() -> Unit)? = null

    fun connect(api: OpenAiClient, key: String, language: String, vocabulary: String) {
        this.api = api
        this.key = key
        this.language = language
        this.vocabulary = vocabulary
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
                        if (closed || !capturing || generation != socketGeneration) false
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
                            !closed && capturing && generation == socketGeneration && !sessionReady
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
                        postFailure("Invalid realtime event received")
                        return
                    }
                    val type = value.optString("type")
                    if (RealtimeProtocol.isSessionReadyEvent(type)) {
                        val problem = RealtimeHandshake.acknowledgementProblem(value)
                        if (problem != null) {
                            app.crashJournal.liveConnectionFailure(problem)
                            postFailure(problem.message())
                        } else sessionBecameReady(generation, webSocket, reconnect)
                        return
                    }
                    main.post {
                        if (!closed && generation == synchronized(state) { socketGeneration }) {
                            if (type == "input_audio_buffer.committed") {
                                synchronized(state) { coverage.acknowledge(value.optString("item_id")) }
                            }
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
                if (closed || !capturing || generation != socketGeneration) false
                else { socket = pendingSocket; true }
            }
            if (!retain) pendingSocket.cancel()
        } catch (problem: Exception) {
            app.crashJournal.recordFailure("websocket.failure", problem)
            postFailure("Realtime connection could not start")
        }
    }

    private fun sessionBecameReady(generation: Int, webSocket: WebSocket, reconnect: Boolean) {
        var startCapture = false
        var reportReconnect = false
        synchronized(state) {
            if (closed || !capturing || generation != socketGeneration || socket !== webSocket) return
            sessionReady = true
            localOnly = false
            interruptionReported = false
            reconnecting = false
            startCapture = captureJob == null
            reportReconnect = reconnect && !startCapture
        }
        app.crashJournal.mark("websocket.session-ready")
        if (startCapture) startCapture()
        if (reportReconnect) main.post { if (!closed && capturing) listener.reconnected() }
    }

    private fun startCapture() {
        synchronized(state) {
            if (closed || !capturing || captureJob != null) return
            captureJob = captureScope.launch { captureLoop() }
        }
    }

    private suspend fun captureLoop() {
        var owned: AudioRecord? = null
        try {
            app.crashJournal.mark("audiorecord.creating")
            val minimum = AudioRecord.getMinBufferSize(
                RealtimeProtocol.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            check(minimum > 0) { "Unsupported microphone format" }
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("Microphone permission was revoked before capture started")
            }
            val current = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RealtimeProtocol.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minimum * 2, RealtimeProtocol.CHUNK_BYTES * 4))
                .build()
            owned = current
            check(current.state == AudioRecord.STATE_INITIALIZED) { "Microphone did not initialize" }
            recorder = current
            current.startRecording()
            check(current.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start" }
            app.crashJournal.mark("audiorecord.started")

            val samples = ShortArray(RealtimeProtocol.CHUNK_SAMPLES)
            while (capturing && currentCoroutineContext().isActive) {
                val count = current.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                if (count > 0) {
                    val pcm = RealtimeProtocol.pcm16LittleEndian(samples, count)
                    audio.append(pcm, RealtimeProtocol.SAMPLE_RATE, RealtimeProtocol.CHANNELS)
                    if (firstSample) {
                        firstSample = false
                        main.post { if (!closed && capturing) listener.captured() }
                    }
                    sendAudio(pcm)
                    val decision = vad.inspect(pcm, System.currentTimeMillis(), silenceStopSeconds)
                    if (decision.commit) sendCommit()
                    if (decision.silenceStop) main.post { if (!closed && capturing) onSilenceStop?.invoke() }
                } else if (count < 0) {
                    if (!capturing || closed) break
                    throw IOException("AudioRecord read failed ($count)")
                } else {
                    delay(5)
                }
            }
        } catch (_: CancellationException) {
            // Explicit stop/close owns cancellation.
        } catch (problem: Throwable) {
            if (!closed && capturing) {
                app.crashJournal.recordFailure("audiorecord.failure", problem)
                postFailure("Android microphone capture failed")
            }
        } finally {
            synchronized(state) { if (recorder === owned) recorder = null }
            try {
                if (owned?.recordingState == AudioRecord.RECORDSTATE_RECORDING) owned.stop()
            } catch (_: Exception) { }
            try { owned?.release() } catch (_: Exception) { }
            app.crashJournal.mark("audiorecord.released")
        }
    }

    private fun sendAudio(pcm: ByteArray) {
        var failedSocket: WebSocket? = null
        var notify = false
        synchronized(state) {
            val current = socket
            if (closed || localOnly || !sessionReady || current == null) return
            if (current.queueSize() + pcm.size > RealtimeProtocol.MAX_WEBSOCKET_QUEUE_BYTES ||
                !current.send(RealtimeProtocol.appendEvent(pcm))) {
                coverage.markUncertain()
                localOnly = true
                sessionReady = false
                socket = null
                failedSocket = current
                if (!interruptionReported) {
                    interruptionReported = true
                    notify = true
                }
            } else {
                remoteBufferedBytes += pcm.size
            }
        }
        failedSocket?.cancel()
        if (notify) main.post { if (!closed && capturing) listener.interrupted() }
    }

    fun commit(): Boolean = sendCommit()

    private fun sendCommit(): Boolean {
        var failedSocket: WebSocket? = null
        var notify = false
        var committed = false
        synchronized(state) {
            if (remoteBufferedBytes <= 0L) return false
            val current = socket
            if (closed || localOnly || !sessionReady || current == null) {
                coverage.markUncertain()
                return false
            }
            val id = "commit-$socketGeneration-${++commitSequence}"
            if (current.send(RealtimeProtocol.commitEvent(id))) {
                coverage.sent(id)
                remoteBufferedBytes = 0L
                committed = true
            } else {
                coverage.markUncertain()
                localOnly = true
                sessionReady = false
                socket = null
                failedSocket = current
                if (!interruptionReported) {
                    interruptionReported = true
                    notify = true
                }
            }
        }
        failedSocket?.cancel()
        if (committed) main.post { if (!closed) listener.turnCommitted() }
        if (notify) main.post { if (!closed && capturing) listener.interrupted() }
        return committed
    }

    fun rejectEmptyCommit(clientEventId: String): Boolean =
        synchronized(state) { coverage.rejectEmpty(clientEventId) }

    fun finishCapture(): Boolean {
        capturing = false
        reconnectJob?.cancel()
        reconnecting = false
        stopRecorder()
        synchronized(state) {
            if (localOnly || !sessionReady) coverage.markUncertain()
        }
        // A read already returned when Stop was tapped may still be serialized to disk and
        // the socket. Commit after that bounded in-flight callback, preserving message order.
        main.postDelayed({ if (!closed) sendCommit() }, 250L)
        return synchronized(state) { !coverage.uncertain }
    }

    private fun stopRecorder() {
        val current = recorder ?: return
        try {
            if (current.recordingState == AudioRecord.RECORDSTATE_RECORDING) current.stop()
        } catch (problem: Exception) {
            app.crashJournal.recordFailure("audiorecord.failure", problem)
        }
    }

    fun continueLocally() {
        if (closed || !capturing) return
        synchronized(state) {
            coverage.markUncertain()
            localOnly = true
            sessionReady = false
        }
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (closed || !capturing) return
        reconnectJob?.cancel()
        reconnectJob = controlScope.launch {
            delay(3_000)
            if (!closed && capturing) openSocket(reconnect = true)
        }
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
            } else if (remoteBufferedBytes > 0L) {
                coverage.markUncertain()
                terminal = true
            }
        }
        detail?.let { app.crashJournal.liveConnectionFailure(it) }
        webSocket.cancel()
        if (terminal) postFailure(message)
        else if (notifyInterrupted) main.post { if (!closed && capturing) listener.interrupted() }
    }

    private fun postFailure(message: String) {
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
            socket.also { socket = null }
        }
        current?.cancel()
        key = ""
    }
}
