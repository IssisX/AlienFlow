package io.github.issisx.alienflow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File

class DictationCoordinator(private val app: AlienApp, private val store: LocalStore) : RealtimeTransport.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = OpenAiClient()
    var service: AlienAccessibilityService? = null
    var status = "Set up your key, microphone and accessibility"; private set
    var active = false; private set
    var finishing = false; private set
    var result = ""; private set
    var original = ""; private set
    var listener: (() -> Unit)? = null
    @Volatile private var session = 0L
    private var ledger = TranscriptLedger()
    private var transport: RealtimeTransport? = null
    private var audio: PendingAudio? = null
    private var editor: EditorBridge? = null
    private var captureTarget: EditorBridge? = null
    private var finalizationJob: Job? = null
    private var writing = false
    private var desired = ""
    private var lastWritten = ""
    private var cleanup: Job? = null
    private val corrections = mutableMapOf<String, String>()
    private val cleaned = mutableSetOf<String>()
    private var interrupted = false
    private var finalizing = false
    private var audioClosing = false
    private var recoveryJob: Job? = null
    private var keyTestJob: Job? = null
    private var recoveryFile: File? = null
    private var displayLive = true
    private var cleanLive = true
    private var correctionMode = "B"
    private var fillersEnabled = false
    private var continueLocal = true
    private var microphoneConfirmed = false
    private var commits = 0
    private val completeItems = mutableSetOf<String>()
    private val committedItems = mutableSetOf<String>()
    private val seenErrors = mutableSetOf<String>()
    private var stoppingAt = 0L
    private var saved = false
    private var liveFailed = false
    private fun update(value: String? = null) {
        val changed = value != null && value != status
        if (value != null) status = value
        listener?.invoke()
        service?.refreshBubble()
        if (changed) service?.presentStatus(status)
        CaptureService.instance?.refresh(status)
    }
    fun toggle() { if (active || finishing) stop() else start() }
    fun start() {
        if (active || finishing) return
        app.crashJournal.mark("tap.received")
        if (!app.screenReady()) { update("Unlock the screen before starting dictation"); return }
        if (recoveryJob?.isActive == true) { update("Wait for audio recovery to finish before starting a new dictation"); return }
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { update("Microphone permission is needed; open AlienFlow setup"); return }
        val key = try { store.apiKey() } catch (_: Exception) { update("Encrypted key could not be opened; save it again"); return }
        if (key.isNullOrBlank()) { update("Save an OpenAI API key first"); return }
        val bridge = service?.bridge ?: run { update("Enable AlienFlow accessibility first"); return }
        app.crashJournal.mark("editor.begin")
        if (!bridge.begin()) { update(bridge.lastError); return }
        app.crashJournal.mark("editor.ready")
        displayLive = store.prefs.getBoolean("live_display", true); cleanLive = store.prefs.getBoolean("live_cleanup", true); correctionMode = store.mode(); fillersEnabled = store.prefs.getBoolean("fillers", false); continueLocal = store.prefs.getBoolean("continue_local", true)
        session++; ledger = TranscriptLedger(); original = ""; result = ""; desired = ""; lastWritten = ""
        saved = false; liveFailed = false; microphoneConfirmed = false; interrupted = false; finalizing = false; audioClosing = false; corrections.clear(); cleaned.clear(); writing = false; commits = 0; completeItems.clear(); committedItems.clear(); seenErrors.clear(); editor = bridge; captureTarget = bridge
        active = true; finishing = false; update("Starting microphone and realtime connection…")
        app.crashJournal.mark("service.start-requested")
        try { app.startForegroundService(Intent(app, CaptureService::class.java).setAction(CaptureService.START)); app.crashJournal.mark("service.start-returned") }
        catch (_: Exception) { active = false; editor = null; update("Android denied background microphone start. Open AlienFlow and start a test; bubble start is not available in this permission state") }
    }
    fun microphoneServiceReady() {
        if (!active || transport != null) return
        app.crashJournal.mark("microphone.setup")
        try {
            val key = store.apiKey() ?: error("Save an API key first")
            audio = PendingAudio(File(store.audioDir, "${System.currentTimeMillis()}.wav"))
            app.crashJournal.mark("transport.start")
            transport = RealtimeTransport(app, audio!!, this).also {
                it.silenceStopSeconds = store.prefs.getInt("silence_stop", 0)
                it.onSilenceStop = { stop() }
                it.connect(api, key, store.language(), store.prefs.getString("vocabulary", "")!!)
            }
            val token = session
            main.postDelayed({ if (session == token && active && !microphoneConfirmed) failure("Microphone/realtime startup timed out") }, 30000)
            monitorTarget(token)
        } catch (e: Exception) { failure(e.message ?: "Microphone setup failed") }
    }
    private fun monitorTarget(token: Long) {
        main.postDelayed({
            if (session != token || (!active && !finishing)) return@postDelayed
            if (!app.screenReady()) { screenUnavailable(); return@postDelayed }
            if (interrupted) { if (captureTarget?.detachedTargetUnchanged() != true) stop() else monitorTarget(token); return@postDelayed }
            if (!writing && editor?.valid() != true) { liveFailed = true; editor?.invalidate(); editor = null; update("Target changed; recording stopped and result preserved"); stop() }
            else monitorTarget(token)
        }, 500)
    }
    fun targetEvent() { if (interrupted && active) { if (captureTarget?.detachedTargetUnchanged() != true) stop(); return }; if (!interrupted && (active || finishing) && !writing && editor?.valid() != true) { liveFailed = true; editor?.invalidate(); editor = null; stop(); update("Focus or manual edit changed; result preserved") } }
    override fun captured() { app.crashJournal.mark("microphone.frames"); microphoneConfirmed = true; if (active) update("Listening • tap the alien to stop") }
    override fun turnCommitted() { commits++ }
    override fun event(value: JSONObject) {
        if (!active && !finishing) return
        val type = value.optString("type")
        val rawId = value.optString("item_id", value.optJSONObject("item")?.optString("id").orEmpty())
        val id = "${value.optInt("_connection_generation")}:$rawId"
        val previous = value.optString("previous_item_id").takeIf { it.isNotEmpty() }?.let { "${value.optInt("_connection_generation")}:$it" }
        when (type) {
            "conversation.item.created", "input_audio_buffer.committed" -> if (rawId.isNotBlank()) { if (type == "input_audio_buffer.committed") committedItems.add(id); ledger.event(value.optString("event_id"), id, previous); render(); tryFinish() }
            "conversation.item.input_audio_transcription.delta" -> if (rawId.isNotBlank()) {
                ledger.event(value.optString("event_id"), id, previous, delta = value.optString("delta")); render()
            }
            "conversation.item.input_audio_transcription.completed" -> if (rawId.isNotBlank()) {
                ledger.event(value.optString("event_id"), id, previous, final = value.optString("transcript")); completeItems.add(id); render(); scheduleCleanup(); tryFinish()
            }
            "conversation.item.input_audio_transcription.failed" -> failure("Recognition failed; pending audio preserved")
            "error" -> {
                val serverId = value.optString("event_id")
                if (serverId.isNotBlank() && !seenErrors.add(serverId)) return
                val code = value.optJSONObject("error")?.optString("code").orEmpty()
                if (code == "input_audio_buffer_commit_empty") {
                    val requestId = value.optJSONObject("error")?.optString("event_id").orEmpty()
                    if (transport?.rejectEmptyCommit(requestId) == true) commits = (commits - 1).coerceAtLeast(completeItems.size)
                    update("Provider could not confirm the final audio buffer; local audio retained")
                    tryFinish()
                }
                else failure("Realtime service error${if (code.matches(Regex("[a-zA-Z0-9_]{1,80}"))) ": $code" else ""}; audio preserved")
            }
        }
    }
    private fun render() {
        original = ledger.stable(); result = ledger.displayed(corrections); desired = result
        if (displayLive) pumpWrite()
        update()
    }
    private fun pumpWrite() {
        if (writing || desired == lastWritten || editor == null) return
        writing = true; val proposed = desired; val token = session
        editor!!.write(proposed) { ok ->
            if (token != session) return@write
            writing = false
            if (!ok) { liveFailed = true; update(editor?.lastError ?: "Insertion unconfirmed; result preserved"); editor = null }
            else { lastWritten = proposed; pumpWrite() }
            if (finishing) tryFinish()
        }
    }
    private fun scheduleCleanup() {
        if ((!active && !finishing) || finalizing || correctionMode == "A" || (active && !cleanLive) || interrupted) return
        if (cleanup?.isActive == true) return
        val next = ledger.finals().firstOrNull { it.first !in cleaned } ?: return
        val (item, source) = next; cleaned.add(item)
        val token = session; val mode = correctionMode; val fillers = fillersEnabled
        cleanup = io.launch {
            try {
                val key = store.apiKey() ?: return@launch
                val output = cleanChunks(source, mode, fillers) { chunk -> api.cleanup(key, chunk, mode, fillers) }
                withContext(Dispatchers.Main) {
                    // A later turn may arrive; the exact completed item, session and ownership
                    // still must match. A completion never doubles an earlier provisional span.
                    if (session == token && ledger.finals().any { it.first == item && it.second == source }) {
                        corrections[item] = output; result = ledger.displayed(corrections); desired = result
                        if (displayLive || finishing) pumpWrite()
                        update()
                    }
                }
            } catch (_: Exception) { withContext(Dispatchers.Main) { if (token == session) update("Cleanup unavailable; recognized words preserved") } }
            finally { withContext(NonCancellable + Dispatchers.Main) { if (session == token) { cleanup = null; scheduleCleanup(); if (finishing) tryFinish() } } }
        }
    }
    fun stop() {
        if (!active) return
        app.crashJournal.mark("dictation.stopping")
        active = false; finishing = true; stoppingAt = System.currentTimeMillis(); update("Finishing captured speech…")
        try { transport?.finishCapture() } catch (_: Exception) { failure("Could not finalize capture; pending audio preserved"); return }
        CaptureService.instance?.stopSelf()
        val token = session
        val capturedAudio = audio; audioClosing = true
        io.launch {
            var error = false
            try { capturedAudio?.close() } catch (_: Exception) { error = true }
            withContext(Dispatchers.Main) { if (session != token) return@withContext; audioClosing = false; if (error) failure("Audio finalization failed; recording retained") else tryFinish() }
        }
        main.postDelayed({ if (session == token && finishing) { if (commits > completeItems.size || commits > committedItems.size || ledger.pending()) failure("Final transcription timed out; pending audio preserved") else tryFinish() } }, 15000)
        main.postDelayed({ if (session == token) { scheduleCleanup(); tryFinish() } }, 1200)
    }
    private fun tryFinish() {
        if (audioClosing || finalizing) return
        if (interrupted && finishing) { finish(true); return }
        if (!finishing || active || System.currentTimeMillis() - stoppingAt < 1000 || commits > completeItems.size || commits > committedItems.size || ledger.pending()) return
        if (cleanup?.isActive == true || writing) return
        if (!displayLive && desired != lastWritten && editor != null) { pumpWrite(); return }
        finish(transport?.hasPendingCoverage == true)
    }
    private fun finish(keepAudio: Boolean) {
        if (finalizing) return
        finalizing = true; active = false; finishing = true
        transport?.close(); transport = null; CaptureService.instance?.stopSelf()
        val captured = audio; val token = session; val source = original.ifBlank { ledger.text() }; val finalText = result
        val retainAudio = keepAudio || (liveFailed && store.prefs.getInt("retention", 7) == -1)
        val outcome = if (retainAudio) "Incomplete • audio pending" else if (liveFailed) "Preserved • live insertion failed" else "Completed"
        finalizationJob = io.launch {
            var storageOk = false
            var audioDeleteFailed = false
            var checkpointId: String? = null
            var accepted = false
            try {
                try {
                    captured?.close()
                    ensureActive()
                    if (!saved) checkpointId = store.saveResult(source, finalText, outcome)
                    storageOk = true
                    if (!retainAudio) audioDeleteFailed = captured?.file?.let { it.exists() && !it.delete() } == true
                } catch (_: CancellationException) { throw CancellationException() }
                catch (_: Exception) { }
                withContext(Dispatchers.Main) {
                    if (session != token) return@withContext
                    // Acceptance is on the serialized UI owner, so cancel between the
                    // disk checkpoint and this handoff is still covered by finally.
                    accepted = true
                    app.crashJournal.mark("dictation.finished")
                    audio = null; saved = storageOk; finalizing = false; finishing = false
                    update(if (!storageOk) "Storage failed; audio retained. Copy the visible result" else if (audioDeleteFailed) "Result saved, but audio deletion failed; discard it in pending recovery" else if (retainAudio) "Stopped • audio saved for recovery" else if (liveFailed) "Result saved • live insertion failed" else "Complete • ready for another dictation")
                    if (storageOk && keepAudio && interrupted && captured != null && continueLocal) recover(captured.file)
                }
            } finally {
                val rollbackId = checkpointId
                if (!accepted && rollbackId != null) withContext(NonCancellable + Dispatchers.IO) {
                    try { store.deleteHistory(rollbackId) }
                    catch (_: Exception) { withContext(Dispatchers.Main) { if (!active && !finishing) update("Canceled recording's history entry could not be removed; clear it in history") } }
                }
            }
        }
    }

    override fun interrupted() {
        if (!active) return
        interrupted = true; cleanup?.cancel(); editor?.detachForCapture(); editor = null; liveFailed = true
        if (continueLocal) {
            transport?.continueLocally()
            update("Connection lost • recording continues locally while realtime reconnects. Tap stop to recover the complete recording")
        } else failure("Connection lost; recording stopped and audio saved")
    }
    override fun reconnected() { if (active && interrupted) update("Realtime reconnected • recording continues. Full recording will recover after stop") }
    override fun failure(message: String) {
        if (!active && !finishing) { update(message); return }
        cleanup?.cancel(); editor?.invalidate(); editor = null; liveFailed = true
        finish(true); update(message)
    }
    fun cancel() {
        if (!active && !finishing) return
        finalizationJob?.cancel(); cleanup?.cancel(); transport?.close(); transport = null
        val bridge = editor; editor = null; active = false; finishing = false
        CaptureService.instance?.stopSelf(); val discarded = audio; audio = null
        session++
        val canceledToken = session
        io.launch {
            try { discarded?.close() } catch (_: Exception) { }
            finally {
                val deleted = discarded?.file?.let { !it.exists() || it.delete() } != false
                if (!deleted) withContext(Dispatchers.Main) { if (session == canceledToken) update("Canceled, but saved audio could not be deleted; remove it in history") }
            }
        }
        if (!writing && bridge != null) bridge.cancel { ok -> if (session == canceledToken && !active && !finishing) update(if (ok) "Canceled • owned text restored" else "Canceled • ownership uncertain, field left unchanged") }
        else update("Canceled • in-flight edit cannot be undone safely")
    }
    fun serviceLost() { if (active || finishing) failure("Accessibility disconnected; recording stopped and audio preserved"); service = null }
    fun screenUnavailable() { if (active || finishing) failure("Screen locked or turned off; microphone stopped and unfinished work preserved") }
    fun captureServiceLost() { if (active) failure("Microphone service stopped unexpectedly; audio retained") }
    fun ownsAudio(file: File) = audio?.file == file || recoveryFile == file
    fun recover(file: File) {
        if (active || finishing || recoveryJob?.isActive == true || ownsAudio(file)) { update("Stop current work before recovering another recording"); return }
        val token = session; recoveryFile = file
        update("Recovering saved audio; no field will be edited")
        recoveryJob = io.launch {
            try {
                val key = store.apiKey() ?: error("Save an API key first")
                val complete = StringBuilder()
                PendingAudio.forEachUploadChunk(file) { chunk ->
                    ensureActive()
                    val part = api.recover(key, chunk, store.language())
                    ensureActive()
                    if (complete.isNotEmpty()) complete.append(' ')
                    complete.append(part)
                }
                val text = complete.toString()
                check(text.isNotBlank()) { "Empty recovery response" }
                val savedId = store.saveResult(text, text, "Recovered • copy manually")
                val deleted = savedId == null || !file.exists() || file.delete()
                withContext(Dispatchers.Main) { if (session == token && !active && !finishing) { result = text; original = text; update(if (savedId != null && !deleted) "Recovered to history, but audio deletion failed; discard it in pending recovery" else if (savedId != null) "Recovered to history • no field was edited" else "Recovered • history off. Copy the result; audio retained until you discard it") } }
            } catch (e: Exception) { withContext(Dispatchers.Main) { if (session == token && !active) update(if (e is SafeApiException) "${e.message} · recording retained" else "Audio recovery failed; saved recording retained") } }
            finally { withContext(NonCancellable + Dispatchers.Main) { recoveryFile = null; recoveryJob = null } }
        }
    }
    fun testKey() {
        if (keyTestJob?.isActive == true || active || finishing) return
        val token = session
        update("Checking transcription model access…")
        keyTestJob = io.launch {
            try { api.clientSecret(store.apiKey() ?: error("Save an API key first"), store.language(), ""); withContext(Dispatchers.Main) { if (session == token && !active && !finishing) update("Key accepted • gpt-live-transcribe session available") } }
            catch (e: Exception) { withContext(Dispatchers.Main) { if (session == token && !active && !finishing) update(if (e is SafeApiException) e.message else "Connection check failed; check key and network") } }
            finally { withContext(NonCancellable + Dispatchers.Main) { keyTestJob = null } }
        }
    }
}
