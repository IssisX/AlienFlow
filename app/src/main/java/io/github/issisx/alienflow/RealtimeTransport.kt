package io.github.issisx.alienflow

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.sqrt

/** All native lifecycle operations run on main. HTTP runs on IO, never on the audio thread. */
class RealtimeTransport(private val context: Context, private val audio: PendingAudio, private val listener: Listener) {
    interface Listener { fun event(value: JSONObject); fun captured(); fun turnCommitted(); fun interrupted(); fun reconnected(); fun failure(message: String) }
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var factory: PeerConnectionFactory? = null
    private var device: JavaAudioDeviceModule? = null
    private var connection: PeerConnection? = null
    private var channel: DataChannel? = null
    private var source: AudioSource? = null
    private var track: AudioTrack? = null
    @Volatile private var closed = false
    @Volatile private var capturing = true
    @Volatile private var localOnly = false
    private var firstSample = true
    private var voiced = false
    private var speechMs = 0L
    private var silenceMs = 0L
    private var lastVoice = System.currentTimeMillis()
    private var connectionGeneration = 0
    private var commitSequence = 0L
    private val coverage = AudioCoverage()
    private var reconnectJob: Job? = null
    private var connectJob: Job? = null
    private var api: OpenAiClient? = null
    private var key = ""
    private var language = ""
    private var vocabulary = ""
    val hasPendingCoverage get() = coverage.uncertain
    var silenceStopSeconds = 0
    var onSilenceStop: (() -> Unit)? = null
    fun connect(api: OpenAiClient, key: String, language: String, vocabulary: String) {
        this.api = api; this.key = key; this.language = language; this.vocabulary = vocabulary
        openPeer(false)
    }
    private fun openPeer(reconnect: Boolean) {
        connectJob = scope.launch {
            val generation = ++connectionGeneration
            try {
                val secret = withContext(Dispatchers.IO) { api!!.clientSecret(key, language, vocabulary) }
                if (closed || !capturing || generation != connectionGeneration) return@launch
                (context.applicationContext as AlienApp).crashJournal.mark("session.secret")
                if (factory == null) {
                    (context.applicationContext as AlienApp).crashJournal.mark("webrtc.initialize")
                    PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
                    device = JavaAudioDeviceModule.builder(context).setUseStereoInput(false).setAudioFormat(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setUseHardwareAcousticEchoCanceler(true).setUseHardwareNoiseSuppressor(true)
                        .setAudioRecordStateCallback(object : JavaAudioDeviceModule.AudioRecordStateCallback {
                            override fun onWebRtcAudioRecordStart() { }
                            override fun onWebRtcAudioRecordStop() { main.post { if (!closed && capturing) listener.failure("Microphone capture stopped unexpectedly; recording preserved") } }
                        })
                        .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                            override fun onWebRtcAudioRecordInitError(message: String) { microphoneError("Microphone initialization failed") }
                            override fun onWebRtcAudioRecordStartError(code: JavaAudioDeviceModule.AudioRecordStartErrorCode, message: String) { microphoneError("Microphone start failed") }
                            override fun onWebRtcAudioRecordError(message: String) { microphoneError("Microphone capture failed") }
                        })
                        .setSamplesReadyCallback { samples ->
                            if (!capturing || closed) return@setSamplesReadyCallback
                            try {
                                check(samples.audioFormat == android.media.AudioFormat.ENCODING_PCM_16BIT) { "Unsupported microphone PCM format" }
                                audio.append(samples.data, samples.sampleRate, samples.channelCount)
                                if (firstSample) { firstSample = false; lastVoice = System.currentTimeMillis(); main.post { if (!closed && capturing) listener.captured() } }
                                inspect(samples.data, samples.sampleRate, samples.channelCount)
                            } catch (_: Exception) { main.post { if (!closed && capturing) listener.failure("Local audio storage failed; capture stopped") } }
                        }.createAudioDeviceModule()
                    (context.applicationContext as AlienApp).crashJournal.mark("webrtc.factory")
                    factory = PeerConnectionFactory.builder().setAudioDeviceModule(device).createPeerConnectionFactory()
                    source = factory!!.createAudioSource(MediaConstraints())
                    track = factory!!.createAudioTrack("alienflow-microphone", source)
                }
                (context.applicationContext as AlienApp).crashJournal.mark("webrtc.peer")
                val config = PeerConnection.RTCConfiguration(emptyList()).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
                connection = factory!!.createPeerConnection(config, object : PeerConnection.Observer {
                    override fun onSignalingChange(s: PeerConnection.SignalingState) {}
                    override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {
                        if (s == PeerConnection.IceConnectionState.FAILED || s == PeerConnection.IceConnectionState.DISCONNECTED) main.postDelayed({
                            if (!closed && capturing && generation == connectionGeneration && connection?.iceConnectionState() in setOf(PeerConnection.IceConnectionState.FAILED, PeerConnection.IceConnectionState.DISCONNECTED)) listener.interrupted()
                        }, 1500)
                    }
                    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                    override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
                    override fun onIceCandidate(candidate: IceCandidate) {}
                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
                    override fun onAddStream(stream: MediaStream) {}
                    override fun onRemoveStream(stream: MediaStream) {}
                    override fun onDataChannel(dataChannel: DataChannel) {}
                    override fun onRenegotiationNeeded() {}
                    override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
                }) ?: error("WebRTC connection creation failed")
                // Do not begin microphone capture before the realtime path is ready.
                // During reconnect, the ADM's independent client demand keeps capture alive.
                connection!!.setAudioRecording(false)
                connection!!.addTrack(track, listOf("alienflow"))
                (context.applicationContext as AlienApp).crashJournal.mark("webrtc.data-channel")
                channel = connection!!.createDataChannel("oai-events", DataChannel.Init()).also { ch -> ch.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(previousAmount: Long) {}
                    override fun onStateChange() { main.post {
                        if (!closed && capturing && generation == connectionGeneration && ch.state() == DataChannel.State.OPEN) {
                            (context.applicationContext as AlienApp).crashJournal.mark("microphone.request")
                            connection?.setAudioRecording(true); device?.requestStartRecording(); localOnly = false
                            if (reconnect) listener.reconnected()
                        }
                        else if (!closed && capturing && generation == connectionGeneration && ch.state() == DataChannel.State.CLOSED) listener.interrupted()
                    } }
                    override fun onMessage(buffer: DataChannel.Buffer) {
                        if (buffer.binary) return
                        val bytes = ByteArray(buffer.data.remaining()); buffer.data.get(bytes)
                        try { val value = JSONObject(String(bytes, Charsets.UTF_8)).put("_connection_generation", generation); main.post {
                            if (!closed && generation == connectionGeneration) {
                                if (value.optString("type") == "input_audio_buffer.committed") coverage.acknowledge(value.optString("item_id"))
                                listener.event(value)
                            }
                        } }
                        catch (_: Exception) { main.post { if (!closed && generation == connectionGeneration) listener.failure("Invalid realtime event received") } }
                    }
                }) }
                (context.applicationContext as AlienApp).crashJournal.mark("webrtc.offer")
                val offer = suspendCancellableCoroutine<SessionDescription> { cont -> connection!!.createOffer(object : SdpObserver {
                    override fun onCreateSuccess(s: SessionDescription) { if (cont.isActive) cont.resume(s) }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(message: String) { if (cont.isActive) cont.resumeWithException(IllegalStateException("WebRTC offer creation failed")) }
                    override fun onSetFailure(message: String) {}
                }, MediaConstraints()) }
                setDescription(offer, true)
                (context.applicationContext as AlienApp).crashJournal.mark("webrtc.answer")
                val answer = withContext(Dispatchers.IO) { api!!.answer(secret, offer.description) }
                if (!closed && capturing && generation == connectionGeneration) setDescription(SessionDescription(SessionDescription.Type.ANSWER, answer), false)
            } catch (_: CancellationException) { }
            catch (e: Exception) {
                if (!closed && capturing) { if (reconnect && !(e is SafeApiException && e.httpCode in setOf(401, 403, 429))) scheduleReconnect() else listener.failure(safeNetworkError(e)) }
            }
        }
    }
    private fun microphoneError(message: String) { main.post { if (!closed && capturing) listener.failure(message) } }
    private fun safeNetworkError(e: Exception) = if (e is SafeApiException) e.message!! else "Realtime connection failed; check network, key and model access"
    private suspend fun setDescription(description: SessionDescription, local: Boolean) {
        suspendCancellableCoroutine<Unit> { cont ->
            val observer = object : SdpObserver {
                override fun onCreateSuccess(s: SessionDescription) {}
                override fun onSetSuccess() { if (cont.isActive) cont.resume(Unit) }
                override fun onCreateFailure(message: String) {}
                override fun onSetFailure(message: String) { if (cont.isActive) cont.resumeWithException(IllegalStateException("WebRTC SDP setup failed")) }
            }
            if (local) connection!!.setLocalDescription(observer, description) else connection!!.setRemoteDescription(observer, description)
        }
    }
    @Synchronized private fun inspect(data: ByteArray, rate: Int, channels: Int) {
        var sum = 0.0; var count = 0
        for (i in 0 until data.size - 1 step 2) { val value = ((data[i].toInt() and 255) or (data[i + 1].toInt() shl 8)).toShort().toDouble(); sum += value * value; count++ }
        val ms = data.size * 1000L / (rate * channels * 2)
        if (count > 0 && sqrt(sum / count) > 450) { voiced = true; speechMs += ms; silenceMs = 0; lastVoice = System.currentTimeMillis() } else silenceMs += ms
        if (voiced && speechMs >= 120 && silenceMs >= 850) { voiced = false; speechMs = 0; silenceMs = 0; main.post { if (capturing && !closed) commit() } }
        if (silenceStopSeconds > 0 && System.currentTimeMillis() - lastVoice > silenceStopSeconds * 1000L) { lastVoice = System.currentTimeMillis(); main.post { if (capturing && !closed) onSilenceStop?.invoke() } }
    }
    fun commit(): Boolean {
        if (closed || localOnly || channel?.state() != DataChannel.State.OPEN) { coverage.markUncertain(); return false }
        val id = "commit-$connectionGeneration-${++commitSequence}"
        val event = JSONObject().put("type", "input_audio_buffer.commit").put("event_id", id)
        val ok = channel!!.send(DataChannel.Buffer(ByteBuffer.wrap(event.toString().toByteArray()), false))
        if (ok) { coverage.sent(id); listener.turnCommitted() } else coverage.markUncertain()
        return ok
    }
    fun rejectEmptyCommit(clientEventId: String): Boolean {
        // Empty server buffer does not prove an empty local recording or media coverage.
        return coverage.rejectEmpty(clientEventId)
    }
    fun finishCapture(): Boolean {
        capturing = false; reconnectJob?.cancel(); connectJob?.cancel()
        connection?.setAudioRecording(false); device?.requestStopRecording()
        if (channel?.state() != DataChannel.State.OPEN || localOnly) coverage.markUncertain()
        main.postDelayed({ if (!closed && !localOnly) commit() }, 350)
        return !hasPendingCoverage
    }
    fun continueLocally() {
        if (closed || !capturing) return
        coverage.markUncertain(); localOnly = true
        device?.requestStartRecording()
        detachPeer(); scheduleReconnect()
    }
    private fun scheduleReconnect() {
        if (closed || !capturing) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch { delay(3000); if (!closed && capturing) { detachPeer(); openPeer(true) } }
    }
    private fun detachPeer() { connectionGeneration++; channel?.dispose(); channel = null; connection?.close(); connection?.dispose(); connection = null }
    fun close() {
        if (closed) return; closed = true; capturing = false; scope.cancel()
        try { connection?.setAudioRecording(false); device?.requestStopRecording() }
        finally {
            detachPeer(); track?.dispose(); track = null; source?.dispose(); source = null; factory?.dispose(); factory = null; device?.release(); device = null; key = ""
        }
    }
}
