package io.github.issisx.alienflow

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.*
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private val app get() = application as AlienApp
    private val green = Color.rgb(169, 242, 123)
    private val white = Color.rgb(229, 238, 224)
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var record: Button
    private lateinit var history: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16, 22, 16)
        val screen = column(); screen.setBackgroundColor(Color.rgb(16, 22, 16)); screen.setPadding(dp(22), dp(24), dp(22), dp(40))
        val scroll = ScrollView(this); scroll.addView(screen); setContentView(scroll)
        title(screen, "ALIENFLOW", 30)
        text(screen, "Your words. Your keyboard.", 18, green)
        text(screen, "Live dictation uses your personal OpenAI account. The alien keeps your existing keyboard selected.")
        status = text(screen, app.coordinator.status, 17, green)
        title(screen, "1 · Personal API key")
        text(screen, "Encrypted on this device using Android Keystore. Never enter it in a dictation field.")
        val key = EditText(this).apply { hint = "OpenAI API key"; setTextColor(white); setHintTextColor(Color.GRAY); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO }
        screen.addView(key)
        button(screen, "Save key") { try { app.store.saveKey(key.text.toString()); key.setText(""); status.text = "Key saved securely on this device" } catch (_: Exception) { status.text = "Could not encrypt/save the key" } }
        button(screen, "Check key & transcription model") { app.coordinator.testKey() }
        button(screen, "Remove key") { app.store.saveKey(""); status.text = "Key removed" }
        title(screen, "2 · Enable dictation")
        button(screen, "Allow microphone & notifications") { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1) }
        button(screen, "Enable AlienFlow accessibility") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        text(screen, "Accessibility lets the alien edit the focused input. Password fields are excluded. Live insertion stops if focus, selection or text changes unexpectedly.")
        title(screen, "3 · Try your first dictation")
        val test = EditText(this).apply { hint = "Tap here, then tap the alien or Start below"; setTextColor(white); setHintTextColor(Color.GRAY); minLines = 3; gravity = android.view.Gravity.TOP; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }
        screen.addView(test)
        record = button(screen, "Start dictation") { app.coordinator.toggle() }
        button(screen, "Cancel current dictation") { app.coordinator.cancel() }
        text(screen, "A real listening state appears after microphone frames arrive. Ordinary pauses do not stop recording. Starting from another app can be denied by Android; the error is shown here.")
        title(screen, "Your result")
        result = text(screen, "No dictation yet", 17)
        button(screen, "Copy current result") { copy(app.coordinator.result) }
        button(screen, "Settings") { settings() }
        title(screen, "Recent text & pending recovery")
        text(screen, "Completed recordings are deleted. Text is kept for 7 days by default. Interrupted audio stays private for 24 hours; recovery never turns on the mic or edits a new field.")
        button(screen, "Refresh history") { refreshHistory() }
        button(screen, "Clear text history") { app.store.deleteHistory(); refreshHistory() }
        history = column(); screen.addView(history); refreshHistory()
        text(screen, "First APK limits: connection loss continues capture locally by default; retries the realtime connection and recovers the complete file after stop. Live gap replay, audio-in-history, image imports and per-entry restore are not available. Composing keyboard words must be finished before dictation.", 13, Color.rgb(164, 176, 160))
    }
    override fun onResume() { super.onResume(); app.coordinator.listener = { renderStatus() }; renderStatus(); refreshHistory() }
    override fun onPause() { app.coordinator.listener = null; super.onPause() }
    private fun renderStatus() { status.text = app.coordinator.status; result.text = app.coordinator.result.ifBlank { "No dictation yet" }; record.text = if (app.coordinator.active) "Stop & finish" else if (app.coordinator.finishing) "Finishing…" else "Start dictation"; record.isEnabled = !app.coordinator.finishing }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun text(parent: LinearLayout, value: String, size: Int = 15, color: Int = white): TextView = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(color); setPadding(0, dp(7), 0, dp(7)); parent.addView(this) }
    private fun title(parent: LinearLayout, value: String, size: Int = 21) = text(parent, value, size, white)
    private fun button(parent: LinearLayout, label: String, action: () -> Unit): Button = Button(this).apply { text = label; isFocusable = false; minHeight = dp(48); setTextColor(Color.rgb(17, 30, 13)); backgroundTintList = android.content.res.ColorStateList.valueOf(green); setOnClickListener { action() }; parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) }) }
    private fun copy(value: String) { if (value.isNotBlank()) getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("AlienFlow dictation", value)) }
    private fun refreshHistory() {
        if (!::history.isInitialized) return
        history.removeAllViews()
        try {
            app.store.pending().filterNot { app.coordinator.ownsAudio(it) }.forEach { file ->
                text(history, "Pending recording · ${DateFormat.getDateTimeInstance().format(Date(file.lastModified()))} · ${file.length() / 1024} KB", 15, green)
                button(history, "Recover audio (uses OpenAI transcription)") { app.coordinator.recover(file) }
                button(history, "Delete this pending recording") { if (!app.coordinator.ownsAudio(file)) file.delete(); refreshHistory() }
            }
            app.store.history().forEachIndexed { index, item ->
                text(history, "${DateFormat.getDateTimeInstance().format(Date(item.getLong("time")))} · ${item.optString("status")}", 13, green)
                text(history, item.optString("result"), 17)
                if (item.optString("original") != item.optString("result")) text(history, "Recognized: ${item.optString("original")}", 14)
                button(history, "Copy result") { copy(item.optString("result")) }
                button(history, "Delete entry") { app.store.deleteHistory(item.getString("id")); refreshHistory() }
            }
        } catch (_: Exception) { text(history, "History could not be loaded; existing files were left intact") }
    }
    private fun settings() {
        val p = app.store.prefs
        val body = column(); body.setPadding(dp(18), dp(8), dp(18), dp(20))
        fun flag(label: String, key: String, default: Boolean) { body.addView(Switch(this).apply { text = label; setTextColor(Color.WHITE); isChecked = p.getBoolean(key, default); setOnCheckedChangeListener { _, value -> p.edit().putBoolean(key, value).apply(); app.coordinator.service?.refreshBubble() } }) }
        fun choices(label: String, key: String, values: List<String>, stored: List<String>, default: String) {
            text(body, label, 16, green)
            val spinner = Spinner(this); spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, values)
            spinner.setSelection(stored.indexOf(p.getString(key, default)).coerceAtLeast(0))
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent: AdapterView<*>?) {} ; override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { p.edit().putString(key, stored[position]).apply() } }
            body.addView(spinner)
        }
        fun slider(label: String, key: String, default: Int, low: Int, high: Int) {
            val caption = text(body, "$label: ${p.getInt(key, default)}", 16, green)
            body.addView(SeekBar(this).apply { max = high - low; progress = p.getInt(key, default) - low; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) {}; override fun onStopTrackingTouch(bar: SeekBar?) {}
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) { if (fromUser) { p.edit().putInt(key, value + low).apply(); caption.text = "$label: ${value + low}"; app.coordinator.service?.refreshBubble() } }
            }) })
        }
        text(body, "Choices apply to the next session. Bubble appearance applies now.")
        title(body, "Bubble")
        slider("Alien size (dp)", "size", 32, 24, 64); slider("Idle opacity (%)", "opacity", 25, 10, 100); slider("Active opacity (%)", "active_opacity", 100, 50, 100)
        flag("Always show alien (otherwise focused inputs)", "always", false)
        button(body, "Reset position / show hidden alien") { app.coordinator.service?.resetPosition() }
        title(body, "Dictation")
        flag("Hold to talk (long press starts; release stops)", "hold", false)
        text(body, "In hold mode, a short tap remains an accessible start/stop action. Cancel is available on the main screen.")
        flag("Live words in the field (off: insert after stop)", "live_display", true)
        slider("Optional silence stop (0 = off; seconds)", "silence_stop", 0, 0, 30)
        text(body, "Language code: en, es, fr… Blank requests automatic detection.")
        val language = EditText(this).apply { setText(p.getString("language", "en")); setTextColor(Color.WHITE); isSingleLine = true; hint = "en" }; body.addView(language)
        text(body, "Personal vocabulary (first 4000 characters sent as guidance)")
        val vocabulary = EditText(this).apply { setText(p.getString("vocabulary", "")); setTextColor(Color.WHITE); minLines = 2 }; body.addView(vocabulary)
        button(body, "Save language & vocabulary") { p.edit().putString("language", language.text.toString().trim()).putString("vocabulary", vocabulary.text.toString()).apply() }
        title(body, "Corrections")
        choices("Strength", "correction", listOf("A · Faithful: recognition only", "B · Very light: punctuation, case, explicit correction", "C · Rewrite: clearer phrasing"), listOf("A", "B", "C"), "B")
        text(body, "B keeps ‘I might not come’ and ‘that's fucking ridiculous’. C explicitly permits wording changes. Ambiguous B changes are rejected.")
        flag("Clean up live (off: after stop)", "live_cleanup", true)
        flag("Remove obvious fillers and exact repetitions", "fillers", false)
        title(body, "History & connection")
        val retention = Spinner(this); val days = listOf(-1, 1, 7, 30, 0)
        retention.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("History off", "Text for 1 day", "Text for 7 days", "Text for 30 days", "Text until deleted")); retention.setSelection(days.indexOf(p.getInt("retention", 7)).coerceAtLeast(0)); body.addView(retention)
        retention.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent: AdapterView<*>?) {}; override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { p.edit().putInt("retention", days[position]).apply() } }
        flag("Keep recording locally on connection loss (off: stop)", "continue_local", true)
        text(body, "Connection loss: keep audio locally until stop (or stop immediately when disabled). Manual recovery preserves the complete recording. Realtime reconnect retries automatically; gaps are recovered after stop, without typing into a changed field. Pending retention: 24 hours. History off does not disable recovery.")
        text(body, "Recordings stay private on this device until recovery. Recovery never restarts the microphone or types into a newly focused field. There is no app word allowance.")
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(21, 29, 21)); addView(body) }
        AlertDialog.Builder(this).setTitle("AlienFlow settings").setView(scroll).setPositiveButton("Done") { _, _ -> app.coordinator.service?.refreshBubble() }.show()
    }
}
