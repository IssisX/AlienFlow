package io.github.issisx.alienflow

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets
import android.widget.*
import kotlinx.coroutines.*

/** Explicitly reachable, user-controlled diagnostics; never uploads automatically. */
class DiagnosticsActivity : Activity() {
    private val app get() = application as AlienApp
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var probe: Job? = null
    private var loading = false
    private var report = ""
    private var saving = ""
    private lateinit var output: TextView
    private lateinit var notice: TextView
    private val actions = mutableListOf<Button>()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 36, 48, 36)
            setBackgroundColor(Color.rgb(15, 20, 26))
        }
        val scroll = ScrollView(this).apply { addView(column) }
        setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            column.setPadding(32 + bars.left, 24 + bars.top, 32 + bars.right, 24 + bars.bottom)
            insets
        }
        fun label(value: String, size: Float = 16f) = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.WHITE)
            setPadding(0, 10, 0, 10); column.addView(this)
        }
        fun button(value: String, requiresReport: Boolean = true, action: () -> Unit): Button = Button(this).apply {
            text = value; isFocusable = false; minHeight = (48 * resources.displayMetrics.density).toInt()
            setOnClickListener { action() }; column.addView(this)
            if (requiresReport) { actions.add(this); isEnabled = false }
        }
        label("AlienFlow diagnostics", 24f)
        label("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        notice = label("Gathering report…")
        button("Share diagnostics") {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "AlienFlow ${BuildConfig.VERSION_NAME} diagnostics")
                putExtra(Intent.EXTRA_TEXT, report)
            }, "Share AlienFlow diagnostic report"))
        }
        button("Copy diagnostic report") {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("AlienFlow diagnostics", report))
            notice.text = "Copied. Paste the report into the development chat."
        }
        button("Save report as a file") {
            saving = report
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "AlienFlow-${BuildConfig.VERSION_NAME}-diagnostics.txt")
            }, 1515)
        }
        button("Refresh report", false) { refreshReport() }
        label("No API key, dictated words, recordings, memory dumps, or automatic uploads. Android exit records distinguish crashes from normal session stops.", 14f)
        button("Test microphone for 3 seconds (no upload)", false) { microphoneProbe() }
        label("This separate check tests Android audio capture only, not the realtime WebSocket or OpenAI. It saves sample counts, not audio.", 14f)
        output = label("", 13f).apply { setTextIsSelectable(true) }
        refreshReport()
    }

    private fun refreshReport() {
        if (loading) return
        loading = true; actions.forEach { it.isEnabled = false }
        scope.launch {
            report = withContext(Dispatchers.IO) { DiagnosticReport.collect(app) }
            output.text = report; notice.text = "Report ready. Share or copy it above."
            actions.forEach { it.isEnabled = true }; loading = false
        }
    }

    private fun microphoneProbe() {
        if (probe?.isActive == true) return
        if (app.coordinator.active || app.coordinator.finishing) {
            notice.text = "Stop dictation before running the separate microphone check."
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1516)
            notice.text = "Grant microphone permission, then tap the microphone test again."
            return
        }
        notice.text = "Testing Android microphone for 3 seconds. Speak normally; nothing is uploaded or saved."
        probe = scope.launch {
            try {
                val samples = withContext(Dispatchers.IO) {
                    app.crashJournal.mark("probe.start")
                    val rate = 24000
                    val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    check(minimum > 0)
                    val recorder = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                        .setBufferSizeInBytes(maxOf(minimum, 9600)).build()
                    var count = 0
                    try {
                        check(recorder.state == AudioRecord.STATE_INITIALIZED)
                        recorder.startRecording()
                        val end = SystemClock.elapsedRealtime() + 3000
                        val buffer = ShortArray(480)
                        while (SystemClock.elapsedRealtime() < end) {
                            ensureActive()
                            val n = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                            check(n >= 0)
                            count += n
                            delay(10)
                        }
                        count
                    } finally {
                        try { if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() }
                        finally { recorder.release() }
                    }
                }
                app.crashJournal.probeResult(samples)
                refreshReport()
            } catch (_: CancellationException) { app.crashJournal.mark("probe.cancelled") }
            catch (e: Exception) {
                app.crashJournal.recordFailure("probe.failure", e)
                refreshReport()
            }
        }
    }

    override fun onPause() { probe?.cancel(); super.onPause() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        val uri = data?.data
        if (request == 1515 && result == RESULT_OK && uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.use { it.write(saving.toByteArray(Charsets.UTF_8)) } ?: error("No output") }
                notice.text = "Diagnostic file saved."
            } catch (_: Exception) { notice.text = "Could not save the file. Copy or share the report instead." }
        }
    }
}
