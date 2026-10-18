package io.github.issisx.alienflow

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class DictationCoordinator(private val app: AlienApp, private val store: LocalStore,
                           private val api: OpenAiClient = OpenAiClient(),
                           ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
                           private val keyProvider: () -> String? = store::apiKey) : RealtimeTransport.Listener {
    companion object {
        /** Users who never chose a quiet-pause stop get one, so a forgotten session stops billing. Off stays selectable. */
        const val DEFAULT_SILENCE_STOP_SECONDS = 15
        const val MAX_SILENCE_STOP_SECONDS = 60
        /** Hard bound on one session's realtime audio, independent of the quiet-pause setting. Never Off. */
        const val DEFAULT_SESSION_MINUTES = 15
        const val MIN_SESSION_MINUTES = 1
        const val MAX_SESSION_MINUTES = 30
        const val SESSION_CAP_MS = DEFAULT_SESSION_MINUTES * 60 * 1_000L
        const val SESSION_CAP_STATUS = "Stopped after $DEFAULT_SESSION_MINUTES minutes to limit cost"
        fun silenceStopSeconds(prefs: SharedPreferences): Int =
            prefs.getInt("silence_stop", DEFAULT_SILENCE_STOP_SECONDS).coerceIn(0, MAX_SILENCE_STOP_SECONDS)
        fun sessionMinutes(prefs: SharedPreferences): Int =
            prefs.getInt("session_minutes", DEFAULT_SESSION_MINUTES).coerceIn(MIN_SESSION_MINUTES, MAX_SESSION_MINUTES)
        fun sessionCapStatus(minutes: Int) = "Stopped after $minutes ${if (minutes == 1) "minute" else "minutes"} to limit cost"
        /** A session whose field was released stops unless an eligible field is attached within this time. */
        const val NO_FIELD_STOP_MS = 10_000L
        const val NO_FIELD_STOP_STATUS = "Stopped • no text field selected"
        /** Realtime plus recovery audio sent per local calendar day. 0 is Off; on by default. */
        const val DEFAULT_DAILY_MINUTES = 30
        const val MIN_DAILY_MINUTES = 0
        const val MAX_DAILY_MINUTES = 120
        /** Live polish plus the final pass after Stop. Past it, recognized words stay unpolished. */
        const val MAX_POLISH_REQUESTS_PER_SESSION = 120
        fun dailyMinutes(prefs: SharedPreferences): Int =
            prefs.getInt("daily_minutes", DEFAULT_DAILY_MINUTES).coerceIn(MIN_DAILY_MINUTES, MAX_DAILY_MINUTES)
        fun dailyLimitStatus(minutes: Int) = "Daily limit of $minutes minutes reached • change it in Settings"
        fun dailyLimitStopStatus(minutes: Int) = "Stopped • daily limit of $minutes minutes reached"
        fun recoveryLimitStatus(remainingBytes: Long, minutes: Int) =
            "Recording kept • it is longer than today's remaining allowance (${UsageMeter.duration(remainingBytes)} of $minutes minutes). Change the daily limit in Settings"
    }
    /** Thrown instead of making a polish request past the session's cap; never a provider error. */
    private class PolishCapReached : Exception()
    private val main = Handler(Looper.getMainLooper())
    private val io = CoroutineScope(SupervisorJob() + ioDispatcher)
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
    private var writeRetryScheduled = false
    private var desired = ""
    private var lastWritten = ""
    private var cleanup: Job? = null
    private var cleanupMemo = CleanupMemo()
    private var endCleanupDone = false
    private val corrections = mutableMapOf<String, String>()
    private val cleaned = mutableMapOf<Long, Pair<String, WritingContext>>()
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
    private var serverConnected = false
    private val startupTimings = StartupTimings()
    val readiness: DictationReadiness get() = DictationReadiness.from(active, finishing,
        microphoneConfirmed, serverConnected, interrupted)
    private var commits = 0
    private val completeItems = mutableSetOf<String>()
    private val committedItems = mutableSetOf<String>()
    private val seenErrors = mutableSetOf<String>()
    private var stoppingAt = 0L
    private var saved = false
    private var liveFailed = false
    private var userEdited = false
    /** Captured before finish so a real rejection is never masked by the recovery banner. */
    private var terminalReason: String? = null
    private var terminalTarget = false
    private var editorEpoch = 0L
    private var waitingForTarget = false
    private var waitingAfterInputEpoch = -1L
    private var switchedTargets = false
    /** Why this session stopped itself; kept visible through the finishing and final statuses. */
    private var stopNotice: String? = null
    /** Monotonic time this session's field was released; null while a field is attached. */
    private var fieldReleasedAt: Long? = null
    private var fieldReleases = 0L
    /** Read once per session; the capture thread compares today's audio against it. */
    @Volatile private var dailyLimitMinutes = DEFAULT_DAILY_MINUTES
    @Volatile private var dailyLimitBytes = UsageMeter.limitBytes(DEFAULT_DAILY_MINUTES)
    @Volatile private var dailyLimitStopPosted = -1L
    /** Polish requests made by this session; replaced at each start, like the cleanup memo. */
    private var polishBudget = AtomicInteger()
    private fun update(value: String? = null) {
        val changed = value != null && value != status
        if (value != null) status = value
        if (changed) app.crashJournal.statusChanged(status)
        listener?.invoke()
        service?.refreshBubble()
        if (changed) service?.presentStatus(status)
        CaptureService.instance?.refresh(status)
    }
    fun toggle() { if (active || finishing) stop() else start() }
    fun start() {
        if (active || finishing) return
        if (keyTestJob?.isActive == true) { update("Wait for the live connection check to finish"); return }
        startupTimings.begin(SystemClock.elapsedRealtime())
        app.crashJournal.mark("tap.received")
        if (!app.screenReady()) { update("Unlock the screen before starting dictation"); return }
        if (recoveryJob?.isActive == true) { update("Wait for audio recovery to finish before starting a new dictation"); return }
        val dailyMinutes = dailyMinutes(store.prefs)
        if (store.usage.limitReached(dailyMinutes)) { update(dailyLimitStatus(dailyMinutes)); return }
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { update("Microphone permission is needed; open AlienFlow setup"); return }
        val key = try { keyProvider() } catch (_: Exception) { update("Encrypted key could not be opened; save it again"); return }
        if (key.isNullOrBlank()) { update("Save an OpenAI API key first"); return }
        val bridge = service?.bridge ?: run { update("Enable AlienFlow accessibility first"); return }
        app.crashJournal.mark("editor.begin")
        if (!bridge.begin()) { update(bridge.lastError); return }
        app.crashJournal.mark("editor.ready")
        terminalTarget = bridge.isTerminal
        displayLive = store.prefs.getBoolean("live_display", true) && !terminalTarget; cleanLive = store.prefs.getBoolean("live_cleanup", true); correctionMode = store.mode(); fillersEnabled = store.prefs.getBoolean("fillers", false); continueLocal = store.prefs.getBoolean("continue_local", true)
        session++; ledger = TranscriptLedger(); cleanupMemo = CleanupMemo(); endCleanupDone = false; original = ""; result = ""; desired = ""; lastWritten = ""
        editorEpoch = 0; waitingForTarget = false; waitingAfterInputEpoch = -1; switchedTargets = false; stopNotice = null
        fieldReleasedAt = null; polishBudget = AtomicInteger(); dailyLimitMinutes = dailyMinutes; dailyLimitBytes = UsageMeter.limitBytes(dailyMinutes)
        saved = false; liveFailed = false; userEdited = false; terminalReason = null; microphoneConfirmed = false; serverConnected = false; interrupted = false; finalizing = false; audioClosing = false; corrections.clear(); cleaned.clear(); writing = false; writeRetryScheduled = false; commits = 0; completeItems.clear(); committedItems.clear(); seenErrors.clear(); editor = bridge; captureTarget = bridge
        active = true; finishing = false; update("Starting microphone and realtime connection…")
        app.crashJournal.mark("service.start-requested")
        try { app.startForegroundService(Intent(app, CaptureService::class.java).setAction(CaptureService.START)); app.crashJournal.mark("service.start-returned") }
        catch (e: Exception) { app.crashJournal.recordFailure("service.failure", e); active = false; editor = null; update("Android denied background microphone start. Open AlienFlow and start a test; bubble start is not available in this permission state") }
    }
    fun microphoneServiceReady() {
        if (!active || transport != null) return
        app.crashJournal.mark("microphone.setup")
        try {
            val key = keyProvider() ?: error("Save an API key first")
            audio = PendingAudio(File(store.audioDir, "${System.currentTimeMillis()}.wav"))
            app.crashJournal.mark("transport.start")
            val token = session
            transport = RealtimeTransport(app, audio!!, this).also {
                it.silenceStopSeconds = silenceStopSeconds(store.prefs)
                it.onSilenceStop = { stop() }
                it.onAudioSent = { bytes -> realtimeAudioSent(token, bytes) }
                it.connect(api, key, store.language(), store.vocabularyForSession())
            }
            main.postDelayed({ if (session == token && active && !microphoneConfirmed) failure("Microphone/realtime startup timed out") }, 30000)
            scheduleSessionCap(token)
            monitorTarget(token)
        } catch (e: Exception) { failure(e.message ?: "Microphone setup failed") }
    }
    /** A normal Stop at the cap: captured speech is still finished, delivered and saved. */
    private fun scheduleSessionCap(token: Long) {
        val minutes = sessionMinutes(store.prefs)
        main.postDelayed({
            if (session != token || !active) return@postDelayed
            stopNotice = sessionCapStatus(minutes)
            stop()
        }, minutes * 60_000L)
    }
    /** Capture/network thread, from the transport. Counting never blocks capture; the stop
     * itself is a normal Stop on the main thread, so captured speech is still finished and saved. */
    private fun realtimeAudioSent(token: Long, bytes: Int) {
        if (store.usage.addRealtimeAudio(bytes) < dailyLimitBytes || dailyLimitStopPosted == token) return
        dailyLimitStopPosted = token
        main.post {
            if (session != token || !active) return@post
            app.crashJournal.mark("stop.daily-limit")
            stopNotice = dailyLimitStopStatus(dailyLimitMinutes)
            stop()
        }
    }
    /** With no eligible field, room or video audio would keep streaming (and billing) with nowhere
     * to write. Stop unless a field is attached within [NO_FIELD_STOP_MS]; captured words are kept. */
    private fun fieldReleased() {
        if (!active || fieldReleasedAt != null) return
        // Live polish has no field to fit; an in-flight request's result is for the released field.
        cleanup?.cancel(); cleanup = null
        fieldReleasedAt = SystemClock.elapsedRealtime()
        scheduleNoFieldStop(session, ++fieldReleases, NO_FIELD_STOP_MS)
    }
    private fun fieldAttached() { fieldReleasedAt = null; fieldReleases++ }
    private fun scheduleNoFieldStop(token: Long, release: Long, delayMs: Long) {
        main.postDelayed({
            val since = fieldReleasedAt
            // Same session, still active, and no field attached at any point since this release.
            if (session != token || !active || fieldReleases != release || since == null) return@postDelayed
            val remaining = NO_FIELD_STOP_MS - (SystemClock.elapsedRealtime() - since)
            if (remaining > 0) { scheduleNoFieldStop(token, release, remaining); return@postDelayed }
            app.crashJournal.mark("stop.no-field")
            stopNotice = NO_FIELD_STOP_STATUS
            stop()
        }, delayMs)
    }
    /**
     * A field losing focus is NOT a reason to shut off the microphone.
     * Revoke only the right to edit that field; keep the ongoing capture and
     * leave all other field contents untouched. Never adopt a new field mid-turn.
     */
    private fun detachInlineEditor() {
        if (editor == null && captureTarget == null) return
        editor?.invalidate()
        editor = null
        captureTarget = null
        userEdited = true
        app.crashJournal.mark("editor.detached")
        update("Text field changed; dictation continues. Stop when finished, then copy the result")
        fieldReleased()
    }
    /** Switching releases only the editor, never capture. Speech recorded with no
     * eligible field has its own epoch and stays in history; it is not backfilled. */
    private fun followFocusedTarget() {
        val previous = editor ?: captureTarget
        if (previous != null && !previous.sameTarget()) releaseTarget(previous)
        if (!waitingForTarget || !active || finishing) return
        val bridge = service?.bridge ?: return
        val currentService = service ?: return
        // A new accessibility focus can precede onStartInput. The old connection
        // must never be bound to a second field in the same package.
        if (currentService.keyboardAttached &&
            (!currentService.keyboardInputStarted || currentService.inputEpoch <= waitingAfterInputEpoch)) return
        if (bridge.focused() == null || !bridge.begin()) return
        editorEpoch++; transport?.switchEditor(editorEpoch)
        editor = bridge; captureTarget = bridge; waitingForTarget = false; fieldAttached()
        terminalTarget = bridge.isTerminal
        displayLive = store.prefs.getBoolean("live_display", true) && !terminalTarget
        desired = ""; lastWritten = ""
        app.crashJournal.mark("editor.ready")
        update(if (terminalTarget) "Listening • Termux text inserts when you stop" else "Listening • tap the alien to stop")
    }
    /** The field is gone; capture continues and the next eligible field gets a new segment. */
    private fun releaseTarget(previous: EditorBridge) {
        waitingAfterInputEpoch = previous.bindingInputEpoch
        previous.invalidate()
        editor = null; captureTarget = null
        writing = false; writeRetryScheduled = false
        waitingForTarget = true; switchedTargets = true
        editorEpoch++; transport?.switchEditor(editorEpoch)
        desired = ""; lastWritten = ""
        app.crashJournal.mark("editor.detached")
        update("Listening • select a text field for new speech")
        fieldReleased()
    }
    private fun monitorTarget(token: Long) {
        main.postDelayed({
            if (session != token || (!active && !finishing)) return@postDelayed
            if (!app.screenReady()) { screenUnavailable(); return@postDelayed }
            if (interrupted) {
                if (captureTarget != null && captureTarget?.detachedTargetUnchanged() != true) detachInlineEditor()
            } else if (active) {
                followFocusedTarget()
            } else if (InlineEditorContinuity.action(editor != null, writing,
                    editor?.sameTarget() == true) == InlineEditorContinuity.Action.DETACH) {
                detachInlineEditor()
            }
            monitorTarget(token)
        }, 500)
    }
    fun targetEvent() {
        if (!active && !finishing) return
        if (interrupted) {
            if (captureTarget != null && captureTarget?.detachedTargetUnchanged() != true) detachInlineEditor()
        } else if (active) {
            followFocusedTarget()
        } else if (InlineEditorContinuity.action(editor != null, writing,
                editor?.sameTarget() == true) == InlineEditorContinuity.Action.DETACH) {
            detachInlineEditor()
        }
        if (!interrupted && !writing) pumpWrite()
    }
    override fun captured() {
        if (!active) return
        app.crashJournal.mark("microphone.frames")
        microphoneConfirmed = true
        startupTimings.captured(SystemClock.elapsedRealtime())
        app.crashJournal.startupTiming(startupTimings.metadata())
        update(if (terminalTarget) "Listening • Termux text inserts when you stop" else "Listening • tap the alien to stop")
    }
    override fun connected() {
        serverConnected = true
        startupTimings.connected(SystemClock.elapsedRealtime())
        app.crashJournal.startupTiming(startupTimings.metadata())
        update()
    }
    override fun turnCommitted() { commits++ }
    override fun event(value: JSONObject) {
        if (!active && !finishing) return
        val type = value.optString("type")
        if ((type == "conversation.item.input_audio_transcription.delta" && value.optString("delta").isNotBlank()) ||
            (type == "conversation.item.input_audio_transcription.completed" && value.optString("transcript").isNotBlank())) {
            if (startupTimings.text(SystemClock.elapsedRealtime())) app.crashJournal.startupTiming(startupTimings.metadata())
        }
        val rawId = value.optString("item_id", value.optJSONObject("item")?.optString("id").orEmpty())
        val id = "${value.optInt("_connection_generation")}:$rawId"
        val previous = value.optString("previous_item_id").takeIf { it.isNotEmpty() }?.let { "${value.optInt("_connection_generation")}:$it" }
        // Production attaches the epoch of the ACKNOWLEDGED audio commit. Missing
        // fixture metadata stays at the original epoch, never the current screen.
        val targetEpoch = if (value.has("_editor_epoch")) value.optLong("_editor_epoch", -1L) else 0L
        val acknowledged = value.optBoolean("_editor_epoch_acknowledged")
        when (type) {
            "conversation.item.created", "input_audio_buffer.committed" -> if (rawId.isNotBlank()) { if (type == "input_audio_buffer.committed") committedItems.add(id); ledger.event(value.optString("event_id"), id, previous, editorEpoch = targetEpoch, epochAcknowledged = acknowledged); render(); tryFinish() }
            "conversation.item.input_audio_transcription.delta" -> if (rawId.isNotBlank()) {
                ledger.event(value.optString("event_id"), id, previous, delta = value.optString("delta"), editorEpoch = targetEpoch, epochAcknowledged = acknowledged); render()
            }
            "conversation.item.input_audio_transcription.completed" -> if (rawId.isNotBlank()) {
                ledger.event(value.optString("event_id"), id, previous, final = value.optString("transcript"), editorEpoch = targetEpoch, epochAcknowledged = acknowledged); completeItems.add(id); render(); scheduleCleanup(); tryFinish()
            }
            "conversation.item.input_audio_transcription.failed" -> failure("Recognition failed; pending audio preserved")
            "error" -> {
                val serverId = value.optString("event_id")
                if (serverId.isNotBlank() && !seenErrors.add(serverId)) return
                val code = value.optJSONObject("error")?.optString("code").orEmpty()
                if (code == "input_audio_buffer_commit_empty") {
                    val requestId = value.optJSONObject("error")?.optString("event_id").orEmpty()
                    if (transport?.rejectEmptyCommit(requestId) == true) commits = (commits - 1).coerceAtLeast(completeItems.size)
                    update("[AF-N02] Audio commit rejected; recording retained. See Diagnostics")
                    tryFinish()
                }
                else failure(LiveConnectionProblem.event(value).message())
            }
        }
    }
    private fun render() {
        original = ledger.stable(); result = ledger.displayed(corrections); desired = ledger.displayedFor(corrections, editorEpoch)
        if (displayLive) pumpWrite()
        update()
    }
    private fun pumpWrite(finalDelivery: Boolean = false) {
        // Accessibility/selection events may arrive during recording or final
        // recognition. End-only targets must wait for tryFinish's ready gate.
        if ((terminalTarget || !displayLive) && !finalDelivery) return
        if (writing || desired == lastWritten || editor == null) return
        writing = true; val proposed = desired; val token = session; val targetToken = editorEpoch
        editor!!.write(proposed) { outcome ->
            if (token != session || targetToken != editorEpoch) return@write
            writing = false
            userEdited = userEdited || editor?.hasUserInteraction == true
            when (outcome) {
                // A write that lost its field (the user switched apps mid-confirmation) is a field
                // switch while recording, not the end of field following for this session.
                EditorWriteResult.USER_EDIT -> {
                    val lost = editor
                    if (active && !finishing && !interrupted && lost != null) { releaseTarget(lost); followFocusedTarget() }
                    else detachInlineEditor()
                }
                EditorWriteResult.RETRY -> {
                    userEdited = true
                    if (!writeRetryScheduled) {
                        writeRetryScheduled = true
                        main.postDelayed({
                            if (token == session && targetToken == editorEpoch) {
                                writeRetryScheduled = false
                                if (finishing && System.currentTimeMillis() - stoppingAt >= 15_000) {
                                    liveFailed = true; editor = null; tryFinish(); return@postDelayed
                                }
                                if (active || finishing) { pumpWrite(); if (finishing) tryFinish() }
                            }
                        }, 30L)
                    }
                }
                EditorWriteResult.FAILED -> { liveFailed = true; update(editor?.lastError ?: "[AF-E14] Insertion unconfirmed; see Diagnostics"); editor = null }
                EditorWriteResult.APPLIED -> { lastWritten = proposed; pumpWrite(); scheduleCleanup() }
            }
            if (finishing && outcome != EditorWriteResult.RETRY) tryFinish()
        }
    }
    private fun scheduleCleanup() {
        if (!active || finalizing || correctionMode == "A" || !cleanLive || interrupted) return
        // No attached field: nothing to fit, and a forgotten session must not keep paying for polish.
        if (fieldReleasedAt != null || polishBudget.get() >= MAX_POLISH_REQUESTS_PER_SESSION) return
        if (cleanup?.isActive == true || writing) return
        val targetToken = editorEpoch
        val source = ledger.stableFor(targetToken)
        val context = if (editor?.hasUserInteraction == true) WritingContext() else editor?.writingContext ?: WritingContext()
        if (source.isBlank() || cleaned[targetToken] == (source to context)) return
        cleaned[targetToken] = source to context
        val token = session; val mode = correctionMode; val fillers = fillersEnabled
        val memo = cleanupMemo; val budget = polishBudget
        val job = io.launch(start = CoroutineStart.LAZY) {
            val ownJob = currentCoroutineContext().job
            try {
                val key = keyProvider() ?: return@launch
                val output = withTimeoutOrNull(4_000L) {
                    memo.cleanWithContext(source, mode, fillers, context) { chunk, local -> spendPolishRequest(budget); api.cleanup(key, chunk, mode, fillers, local) }
                } ?: return@launch
                withContext(Dispatchers.Main) {
                    // A later turn may arrive; the exact completed item, session and ownership
                    // still must match. A completion never doubles an earlier provisional span.
                    if (session == token && editorEpoch == targetToken && active && !finalizing &&
                        context == (if (editor?.hasUserInteraction == true) WritingContext() else editor?.writingContext ?: WritingContext()) &&
                        ledger.polish(targetToken, source, output)) { result = ledger.displayed(corrections); desired = ledger.displayedFor(corrections, editorEpoch)
                        if (displayLive || (finishing && !terminalTarget)) pumpWrite()
                        update()
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: PolishCapReached) { /* Session budget spent: the recognized words stay. */ }
            catch (_: Exception) { withContext(Dispatchers.Main) { if (token == session && active) update("Cleanup unavailable; recognized words preserved") } }
            finally { withContext(NonCancellable + Dispatchers.Main) {
                if (session == token && cleanup === ownJob) { cleanup = null; scheduleCleanup() }
            } }
        }
        cleanup = job; job.start()
    }
    /** Every polish (Responses) request passes here: the per-session cap first, then the daily meter. */
    private fun spendPolishRequest(budget: AtomicInteger) {
        if (budget.incrementAndGet() > MAX_POLISH_REQUESTS_PER_SESSION) throw PolishCapReached()
        store.usage.addPolishRequest()
    }
    fun stop() {
        if (!active) return
        app.crashJournal.mark("dictation.stopping")
        // Completed recognition is the deliverable. Optional live polish cannot
        // hold Stop behind a queue of per-turn requests.
        cleanup?.cancel(); cleanup = null
        active = false; finishing = true; stoppingAt = System.currentTimeMillis()
        val token = session
        val capturedAudio = audio; audioClosing = true
        fun closeAudio() {
            io.launch {
                var error = false
                try { capturedAudio?.close() } catch (_: Exception) { error = true }
                withContext(Dispatchers.Main) { if (session != token) return@withContext; audioClosing = false; if (error) failure("Audio finalization failed; recording retained") else tryFinish() }
            }
        }
        try { val current = transport; if (current != null) current.finishCapture { closeAudio() } else closeAudio() }
        catch (_: Exception) { failure("Could not finalize capture; pending audio preserved"); return }
        // Release hardware capture before refresh can remove the microphone FGS type.
        update(stopNotice?.let { "$it • finishing captured speech…" } ?: "Finishing captured speech…")
        main.postDelayed({ if (session == token && finishing) { if (transport?.hasUnacknowledgedCommit == true || commits > completeItems.size || commits > committedItems.size || ledger.pending()) failure("Final transcription timed out; pending audio preserved") else tryFinish() } }, 15000)
    }
    private fun tryFinish() {
        if (audioClosing || finalizing) return
        if (interrupted && finishing) { finish(true); return }
        if (!finishing || active || transport?.hasUnacknowledgedCommit == true || commits > completeItems.size || commits > committedItems.size || ledger.pending()) return
        if (!endCleanupDone) {
            endCleanupDone = true
            app.crashJournal.mark("dictation.transcript-ready")
            if ((!cleanLive || !ledger.fullyPolished(editorEpoch)) && correctionMode != "A" && (if (switchedTargets) desired else original).isNotBlank() && !interrupted &&
                polishBudget.get() < MAX_POLISH_REQUESTS_PER_SESSION) {
                polishAfterStop(); return
            }
        }
        if (cleanup?.isActive == true || writing || writeRetryScheduled) return
        if (desired != lastWritten && editor != null) {
            // Composition cannot hold microphone/finalization resources forever.
            // After Stop, retain any still-unwritten result in History.
            if (System.currentTimeMillis() - stoppingAt >= 15_000) {
                liveFailed = true; editor = null
            } else { pumpWrite(finalDelivery = true); return }
        }
        finish(transport?.hasPendingCoverage == true)
    }
    /** One bounded final pass covers the last phrase even when live cleanup was enabled. */
    private fun polishAfterStop() {
        val token = session; val targetToken = editorEpoch; val sessionOriginal = original
        val source = if (switchedTargets) ledger.stableFor(editorEpoch) else original; val mode = correctionMode; val fillers = fillersEnabled
        val context = if (editor?.hasUserInteraction == true) WritingContext() else editor?.writingContext ?: WritingContext()
        val memo = cleanupMemo; val budget = polishBudget
        val job = io.launch(start = CoroutineStart.LAZY) {
            val ownJob = currentCoroutineContext().job
            try {
                app.crashJournal.mark("cleanup.after-stop")
                val key = keyProvider() ?: return@launch
                val output = withTimeoutOrNull(2_500L) {
                    memo.cleanWithContext(source, mode, fillers, context) { chunk, local -> spendPolishRequest(budget); api.cleanup(key, chunk, mode, fillers, local) }
                }
                withContext(Dispatchers.Main) {
                    if (session == token && editorEpoch == targetToken && finishing && !finalizing && original == sessionOriginal && output != null) {
                        result = if (switchedTargets) ledger.displayedReplacingSegment(corrections, editorEpoch, output) else output
                        desired = output
                        if (displayLive && !terminalTarget) pumpWrite()
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Recognized words and already accepted polish remain. */ }
            finally { withContext(NonCancellable + Dispatchers.Main) {
                if (session == token && cleanup === ownJob) { cleanup = null; tryFinish() }
            } }
        }
        cleanup = job; job.start()
    }
    private fun finish(keepAudio: Boolean) {
        if (finalizing) return
        finalizing = true; active = false; finishing = true
        app.crashJournal.mark("dictation.saving")
        transport?.close(); transport = null
        val captured = audio; val token = session; val source = original.ifBlank { ledger.text() }; val finalText = result
        val reason = terminalReason
        val delivered = CompletionFeedback.inlineDelivered(desired, lastWritten)
        val insertionFailed = liveFailed && !delivered
        val retainAudio = keepAudio || (insertionFailed && store.prefs.getInt("retention", 7) == -1)
        val outcome = if (retainAudio) "Incomplete • audio pending" else if (terminalTarget && delivered) "Sent to Termux" else if (userEdited) "Preserved • user edited the field" else if (insertionFailed) "Preserved • live insertion failed" else "Completed"
        finalizationJob = io.launch {
            store.usage.flush()
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
                    audio = null; cleanupMemo = CleanupMemo(); saved = storageOk; finalizing = false; finishing = false
                    val hasSpeechFile = captured?.file?.let { it.exists() && it.length() > 44 } == true
                    val recoveryLabel = if (hasSpeechFile) "Audio saved for recovery" else "No microphone audio captured"
                    val notice = stopNotice
                    update(when {
                        !storageOk -> "Storage failed; audio retained if available. Copy the visible result"
                        reason != null -> "$reason • $recoveryLabel"
                        audioDeleteFailed -> "Result saved, but audio deletion failed; discard it in pending recovery"
                        retainAudio -> if (hasSpeechFile) "Stopped • audio saved for recovery" else "Stopped • no microphone audio captured"
                        userEdited -> "Text saved in AlienFlow • your field edits were preserved"
                        insertionFailed -> "Result saved • inline editing unavailable; copy the transcript"
                        terminalTarget && delivered -> "Sent to Termux • press Enter yourself"
                        switchedTargets -> "Complete • text saved across apps"
                        else -> "Complete • ready for another dictation"
                    }.let { if (notice != null && storageOk && reason == null) "$notice • $it" else it })
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
        serverConnected = false
        interrupted = true; cleanup?.cancel(); editor?.detachForCapture(); editor = null; liveFailed = true
        if (continueLocal) {
            transport?.continueLocally()
            update("Connection lost • recording continues locally while realtime reconnects. Tap stop to recover the complete recording")
        } else failure("Connection lost; recording stopped and audio saved")
    }
    override fun reconnected() { if (active && interrupted) update("Realtime reconnected • recording continues. Full recording will recover after stop") }
    override fun failure(message: String) {
        if (!active && !finishing) { update(message); return }
        terminalReason = message
        app.crashJournal.mark("failure.terminal")
        cleanup?.cancel(); editor?.invalidate(); editor = null; captureTarget = null; liveFailed = true
        finish(true); update(message)
    }
    fun cancel() {
        if (!active && !finishing) return
        terminalReason = null
        finalizationJob?.cancel(); cleanup?.cancel(); transport?.close(); transport = null
        val bridge = editor; editor = null; active = false; finishing = false
        val discarded = audio; audio = null
        session++
        cleanupMemo = CleanupMemo()
        val canceledToken = session
        io.launch {
            store.usage.flush()
            try { discarded?.close() } catch (_: Exception) { }
            finally {
                val deleted = discarded?.file?.let { !it.exists() || it.delete() } != false
                if (!deleted) withContext(Dispatchers.Main) { if (session == canceledToken) update("Canceled, but saved audio could not be deleted; remove it in history") }
            }
        }
        if (!writing && bridge != null) bridge.cancel { ok -> if (session == canceledToken && !active && !finishing) update(if (ok) "Canceled • owned text restored" else "Canceled • ownership uncertain, field left unchanged") }
        else update("Canceled • in-flight edit cannot be undone safely")
    }
    fun serviceLost() {
        app.crashJournal.mark("accessibility.disconnected")
        service = null
        CaptureService.instance?.stopSelf()
        if (active || finishing) failure("Accessibility disconnected; recording stopped and audio preserved")
    }
    fun screenUnavailable() { if (active || finishing) failure("Screen locked or turned off; microphone stopped and unfinished work preserved") }
    fun captureServiceLost() { if (active) failure("Microphone service stopped unexpectedly; audio retained") }
    fun ownsAudio(file: File) = audio?.file == file || recoveryFile == file
    fun recover(file: File) {
        if (active || finishing || recoveryJob?.isActive == true || ownsAudio(file)) { update("Stop current work before recovering another recording"); return }
        val minutes = dailyMinutes(store.prefs)
        val remaining = store.usage.remainingBytes(minutes)
        if ((file.length() - PendingAudio.HEADER_BYTES).coerceAtLeast(0L) > remaining) { update(recoveryLimitStatus(remaining, minutes)); return }
        val token = session; recoveryFile = file
        update("Recovering saved audio; no field will be edited")
        recoveryJob = io.launch {
            try {
                val key = keyProvider() ?: error("Save an API key first")
                val complete = StringBuilder()
                PendingAudio.forEachUploadChunk(file) { chunk ->
                    ensureActive()
                    store.usage.addRecoveryAudio((chunk.length() - PendingAudio.HEADER_BYTES).coerceAtLeast(0L))
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
            finally { store.usage.flush(); withContext(NonCancellable + Dispatchers.Main) { recoveryFile = null; recoveryJob = null } }
        }
    }
    fun testKey() {
        if (keyTestJob?.isActive == true || active || finishing) return
        val token = session
        app.crashJournal.beginConnectionCheck()
        update("Checking the live connection and transcription session…")
        keyTestJob = io.launch {
            try {
                val key = keyProvider() ?: error("Save an API key first")
                store.usage.addConnectionCheck()
                RealtimeHandshake.check(api, key, store.language(), store.vocabularyForSession())
                withContext(Dispatchers.Main) {
                    if (session == token && !active && !finishing)
                        update("Live connection accepted • gpt-live-transcribe • microphone not tested")
                }
            } catch (e: LiveConnectionException) {
                app.crashJournal.liveConnectionFailure(e.problem)
                withContext(Dispatchers.Main) { if (session == token && !active && !finishing) update(e.problem.message()) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                withContext(Dispatchers.Main) { if (session == token && !active && !finishing) update("Live connection check failed; check key and network") }
            }
            finally { store.usage.flush(); withContext(NonCancellable + Dispatchers.Main) { keyTestJob = null } }
        }
    }
}
