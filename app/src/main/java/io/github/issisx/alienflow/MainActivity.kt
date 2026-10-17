package io.github.issisx.alienflow

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Navigation owns presentation only. Dictation and editor ownership stay in their existing owners. */
class MainActivity : Activity() {
    companion object { const val OPEN_SETTINGS = "io.github.issisx.alienflow.OPEN_SETTINGS" }
    private enum class Page { HOME, HISTORY, SETTINGS, BUBBLE, DICTATION, LANGUAGE, PRIVACY, ACCOUNT, HELP, TRANSCRIPT, RESULT, RECOVERY }
    private val app get() = application as AlienApp
    private val prefs get() = app.store.prefs
    private lateinit var ui: AppUi
    private lateinit var host: FrameLayout
    private lateinit var navigation: LinearLayout
    private var page = Page.HOME
    private var scroll: ScrollView? = null
    private val scrollPositions = mutableMapOf<Page, Int>()
    private var status: TextView? = null
    private var resultPreview: TextView? = null
    private var record: Button? = null
    private var cancel: Button? = null
    private var test: EditText? = null
    private var testDraft = ""
    private var selectedId: String? = null
    private var query = ""
    private val backStack = ArrayDeque<Page>()
    private var listPosition = 0
    private var listOffset = 0
    private var historyList: ListView? = null
    private var historyAdapter: TranscriptAdapter? = null
    private var historySummary: TextView? = null
    private var historyNotice: TextView? = null
    private var recoveryRow: TextView? = null
    private var recoverySignature = ""
    private var accountKeyState: TextView? = null
    private var setupCard: LinearLayout? = null
    private var microphoneState: TextView? = null
    private var accessibilityState: TextView? = null
    private val back = OnBackInvokedCallback { goBack() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = AppUi(this)
        page = savedInstanceState?.getString("page")?.let { runCatching { Page.valueOf(it) }.getOrNull() } ?: Page.HOME
        if (savedInstanceState == null && intent.action == OPEN_SETTINGS) page = Page.SETTINGS
        selectedId = savedInstanceState?.getString("transcript")
        query = savedInstanceState?.getString("query").orEmpty()
        savedInstanceState?.getStringArrayList("backStack")?.forEach { name -> runCatching { Page.valueOf(name) }.getOrNull()?.let { backStack.addLast(it) } }
        listPosition = savedInstanceState?.getInt("listPosition") ?: 0
        listOffset = savedInstanceState?.getInt("listOffset") ?: 0
        testDraft = savedInstanceState?.getString("testDraft").orEmpty()
        savedInstanceState?.getBundle("scrolls")?.let { bundle -> Page.entries.forEach { scrollPositions[it] = bundle.getInt(it.name) } }
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.windowInsetsController?.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        val shell = ui.column().apply { setBackgroundColor(ui.background) }
        val frame = ui.readingFrame()
        shell.addView(frame, LinearLayout.LayoutParams(-1, -1).apply { gravity = Gravity.CENTER_HORIZONTAL })
        val content = ui.column()
        frame.addView(content)
        host = FrameLayout(this)
        content.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))
        navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8)) }
        content.addView(navigation)
        setContentView(shell)
        shell.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        shell.requestApplyInsets()
        renderPage()
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, back)
    }
    override fun onDestroy() { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(back); super.onDestroy() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (intent.action == OPEN_SETTINGS) { backStack.clear(); open(Page.SETTINGS, push = false) }
    }
    override fun onResume() {
        super.onResume()
        app.coordinator.listener = {
            renderStatus()
            if (!app.coordinator.active && !app.coordinator.finishing) refreshHistory()
            refreshRecovery()
        }
        renderStatus(); refreshHistory(); refreshRecovery(); refreshSetup()
    }
    override fun onPause() { app.coordinator.listener = null; app.coordinator.cancelAuxiliaryRequests(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        rememberPage()
        outState.putString("page", page.name); outState.putString("transcript", selectedId)
        outState.putString("query", query); outState.putString("testDraft", testDraft)
        outState.putStringArrayList("backStack", ArrayList(backStack.map { it.name }))
        outState.putInt("listPosition", listPosition); outState.putInt("listOffset", listOffset)
        outState.putBundle("scrolls", Bundle().apply { scrollPositions.forEach { (page, y) -> putInt(page.name, y) } })
        super.onSaveInstanceState(outState)
    }
    private fun rememberPage() {
        test?.let { testDraft = it.text.toString() }
        scroll?.let { scrollPositions[page] = it.scrollY }
        historyList?.let { listPosition = it.firstVisiblePosition; listOffset = it.getChildAt(0)?.top ?: 0 }
    }
    private fun open(next: Page, push: Boolean = true) {
        if (next != page && (page == Page.RECOVERY || page == Page.ACCOUNT)) app.coordinator.cancelAuxiliaryRequests()
        rememberPage()
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(host.windowToken, 0)
        if (push) backStack.addLast(page)
        page = next; renderPage()
    }
    private fun goBack() {
        if (backStack.isNotEmpty()) { open(backStack.removeLast(), push = false); return }
        when (page) {
            Page.HOME -> finish()
            Page.HISTORY, Page.SETTINGS, Page.RESULT -> open(Page.HOME)
            Page.TRANSCRIPT, Page.RECOVERY -> open(Page.HISTORY)
            else -> open(Page.SETTINGS)
        }
    }
    private fun renderPage() {
        host.removeAllViews()
        status = null; resultPreview = null; record = null; cancel = null; test = null
        historyList = null; historyAdapter = null; historySummary = null; historyNotice = null; recoveryRow = null; accountKeyState = null; scroll = null
        setupCard = null; microphoneState = null; accessibilityState = null
        val body = ui.column().apply { setPadding(ui.dp(22), ui.dp(16), ui.dp(22), ui.dp(24)); isFocusableInTouchMode = true }
        if (page == Page.HISTORY) {
            host.addView(body, FrameLayout.LayoutParams(-1, -1)); historyPage(body)
        } else if (page == Page.BUBBLE) {
            host.addView(body, FrameLayout.LayoutParams(-1, -1)); bubble(body)
        } else {
            scroll = ScrollView(this).apply { isFillViewport = true; addView(body); clipToPadding = false }
            host.addView(scroll, FrameLayout.LayoutParams(-1, -1))
            when (page) {
                Page.HOME -> home(body)
                Page.SETTINGS -> settings(body)
                Page.BUBBLE -> bubble(body)
                Page.DICTATION -> dictation(body)
                Page.LANGUAGE -> language(body)
                Page.PRIVACY -> privacy(body)
                Page.ACCOUNT -> account(body)
                Page.HELP -> help(body)
                Page.TRANSCRIPT -> transcript(body)
                Page.RESULT -> currentResult(body)
                Page.RECOVERY -> recovery(body)
                else -> Unit
            }
            val currentScroll = scroll!!
            currentScroll.post { if (scroll === currentScroll) currentScroll.scrollTo(0, scrollPositions[page] ?: 0) }
        }
        nav(); renderStatus(); refreshHistory(); refreshRecovery()
    }
    private fun nav() {
        navigation.removeAllViews()
        val selected = when (page) { Page.HOME, Page.RESULT -> Page.HOME; Page.HISTORY, Page.TRANSCRIPT, Page.RECOVERY -> Page.HISTORY; else -> Page.SETTINGS }
        listOf(Page.HOME to "Home", Page.HISTORY to "History", Page.SETTINGS to "Settings").forEach { (destination, label) ->
            navigation.addView(Button(this).apply {
                text = label; textSize = 14f; isAllCaps = false; minHeight = ui.dp(52)
                setPadding(ui.dp(4), ui.dp(8), ui.dp(4), ui.dp(8))
                setTextColor(if (selected == destination) ui.accent else ui.muted)
                background = ui.ripple(if (selected == destination) ui.surface else ui.background, 14)
                isSelected = selected == destination
                contentDescription = if (isSelected) "$label, selected" else label
                tag = "nav-${destination.name}"
                setOnClickListener { if (page != destination) { backStack.clear(); open(destination, push = false) } }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(3); marginEnd = ui.dp(3) })
        }
    }
    private fun heading(body: LinearLayout, title: String, subtitle: String, leaf: Boolean = true) {
        if (leaf) ui.button(body, "‹ Back") { goBack() }.apply { tag = "back"; gravity = Gravity.START or Gravity.CENTER_VERTICAL; background = ui.ripple(ui.background); setPadding(0, ui.dp(8), 0, ui.dp(8)) }
        ui.text(body, title, 30, bold = true).apply { isAccessibilityHeading = true; setPadding(0, ui.dp(6), 0, 0) }
        ui.text(body, subtitle, 15, ui.muted).apply { setPadding(0, 0, 0, ui.dp(14)) }
    }
    private fun ready() = app.coordinator.paidApiEnabled && prefs.contains("api_key") && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && app.coordinator.service != null
    private fun home(body: LinearLayout) {
        val brand = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        brand.addView(ImageView(this).apply { setImageResource(R.drawable.alien_approved); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(ui.dp(56), ui.dp(56)))
        brand.addView(ui.text("AlienFlow", 25, bold = true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = ui.dp(12) })
        body.addView(brand, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(24) })
        ui.text(body, "Make room for\nyour words.", 32, bold = true)
        ui.text(body, "Speak naturally. Keep your keyboard.", 16, ui.muted).apply { setPadding(0, 0, 0, ui.dp(14)) }
        run {
            val setup = ui.card(body).also { setupCard = it; it.visibility = if (ready()) View.GONE else View.VISIBLE }
            ui.text(setup, "A little setup. Then just speak.", 18, bold = true)
            ui.text(setup, "Connect your account and allow AlienFlow to hear you and place your words in a text field.", color = ui.muted)
            ui.button(setup, "Finish setup", primary = true) { open(Page.ACCOUNT) }
        }
        val practice = ui.card(body)
        ui.text(practice, "Try it here", 18, bold = true)
        ui.text(practice, "Tap the field, then start. The green dot on the alien means you can speak.", 14, ui.muted)
        test = edit(practice, "Your words will appear here…", testDraft, multi = true).apply { tag = "practice"; minLines = 3 }
        status = ui.text(practice, "", 14, ui.accent).apply { tag = "session-status"; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        record = ui.button(practice, "Start dictation", primary = true) { test?.requestFocus(); app.coordinator.toggle() }
        cancel = ui.button(practice, "Cancel dictation") { app.coordinator.cancel() }
        ui.button(practice, "Pause all API activity", destructive = true) { app.coordinator.pausePaidApi() }
        val latest = ui.card(body)
        ui.text(latest, "Latest result", 18, bold = true)
        resultPreview = ui.text(latest, "", 15, ui.muted).apply { maxLines = 3; ellipsize = TextUtils.TruncateAt.END; tag = "latest-preview" }
        ui.button(latest, "Open latest result") { open(Page.RESULT) }
        ui.text(body, "Your history has its own home. Find, copy or delete a transcript in History.", 14, ui.muted)
    }
    private fun renderStatus() {
        val coordinator = app.coordinator
        status?.text = if (coordinator.status == "Set up your key, microphone and accessibility") {
            if (ready()) "Ready when you are." else "Finish setup to start dictating."
        } else coordinator.status
        resultPreview?.text = coordinator.result.ifBlank { "Your most recent dictation will appear here." }
        record?.text = when { coordinator.active -> "Stop & finish"; coordinator.finishing -> "Finishing…"; else -> "Start dictation" }
        record?.isEnabled = !coordinator.finishing
        cancel?.visibility = if (coordinator.active || coordinator.finishing) View.VISIBLE else View.GONE
    }
    private fun refreshSetup() {
        setupCard?.visibility = if (ready()) View.GONE else View.VISIBLE
        microphoneState?.text = if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "Microphone allowed" else "Microphone access needed"
        accessibilityState?.text = if (app.coordinator.service != null) "Accessibility connected" else "Accessibility needs to be enabled"
    }
    private fun settings(body: LinearLayout) {
        heading(body, "Settings", "Make AlienFlow feel like you.", leaf = false)
        ui.row(body, "Floating alien", "Size, visibility and how you start dictation") { open(Page.BUBBLE) }
        ui.row(body, "Dictation & writing", "Live words, punctuation and writing style") { open(Page.DICTATION) }
        ui.row(body, "Language & vocabulary", "Your language, names and specialist words") { open(Page.LANGUAGE) }
        ui.row(body, "Privacy & saving", "How long transcripts stay and connection recovery") { open(Page.PRIVACY) }
        ui.row(body, "Account & setup", "OpenAI connection and phone permissions") { open(Page.ACCOUNT) }
        ui.row(body, "Help & about", "A quick guide, troubleshooting and app version") { open(Page.HELP) }
        ui.text(body, "Changes save automatically and apply to your next dictation. The alien’s appearance updates right away.", 13, ui.muted)
    }
    private fun bubble(body: LinearLayout) {
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(Button(this).apply {
            text = "‹ Back"; isAllCaps = false; textSize = 14f; minHeight = ui.dp(48)
            setTextColor(ui.muted); background = ui.ripple(ui.background)
            tag = "back"; setOnClickListener { goBack() }
        }, LinearLayout.LayoutParams(-2, -2))
        header.addView(ui.text("Floating alien", 22, bold = true).apply { isAccessibilityHeading = true }, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ui.dp(12) })
        val previewCard = ui.card(body)
        ui.text(previewCard, "Live preview", 18, bold = true)
        ui.text(previewCard, "Your icon on light and dark backgrounds.", 14, ui.muted)
        previewCard.addView(BubblePreview(this, app.store.prefs).apply { tag = "bubble-preview" }, LinearLayout.LayoutParams(-1, ui.dp(120)))
        val controls = ui.column()
        scroll = ScrollView(this).apply { addView(controls); isFillViewport = true }
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val appearance = ui.card(controls)
        ui.text(appearance, "Appearance", 18, bold = true)
        slider(appearance, "Size", "size", 32, 24, 64) { "${it * 100 / 32}%" }
        slider(appearance, "Visibility when idle", "opacity", BubbleOpacity.DEFAULT_IDLE_PERCENT, 10, 100) { "$it%" }
        slider(appearance, "Visibility while dictating", "active_opacity", 100, 50, 100) { "$it%" }
        ui.text(appearance, "Higher visibility makes the alien more solid. Lower visibility lets more of your screen show through.", 14, ui.muted)
        val behaviour = ui.card(controls)
        toggle(behaviour, "Always show the alien", "Leave off to show it only when a supported text field is selected.", "always", false)
        toggle(behaviour, "Hold to talk", "Press and hold to start; release to finish. A short tap still starts or stops dictation.", "hold", false)
        ui.button(controls, "Bring the alien back") {
            val service = app.coordinator.service
            if (service == null) notice("Enable accessibility in Account & setup first.")
            else { service.resetPosition(); notice("Position reset. Select a text field to see the alien.") }
        }
        ui.text(controls, "Moves the alien to its starting position and unhides it. Accessibility must be enabled in Account & setup.", 14, ui.muted)
        val currentScroll = scroll!!
        currentScroll.post { if (scroll === currentScroll) currentScroll.scrollTo(0, scrollPositions[Page.BUBBLE] ?: 0) }
    }
    private fun dictation(body: LinearLayout) {
        heading(body, "Dictation & writing", "Choose how your speech becomes text.")
        val delivery = ui.card(body)
        toggle(delivery, "Show words as I speak", "Turn off to place the finished text after you stop. Termux always receives text after you stop.", "live_display", true)
        slider(delivery, "Stop after a quiet pause", "silence_stop", 30, 1, 30) { "$it seconds" }
        ui.text(delivery, "Spending safeguard: recording stops after at most 30 seconds of quiet or five minutes per start. Tap again to begin another session. Switching apps keeps the current session active.", 14, ui.muted)
        ui.text(delivery, "Off keeps recording until you stop it yourself.", 14, ui.muted)
        val writing = ui.card(body)
        ui.text(writing, "Writing style", 18, bold = true)
        choose(writing, "correction", listOf(
            Choice("A", "As spoken", "Keep the recognized words, without an extra editing pass."),
            Choice("B", "Light polish", "Tidy sentence case, punctuation and clear spelling errors. Connect phrases while keeping your voice."),
            Choice("C", "Clearer phrasing", "Allow wording changes for a more readable result.")), prefs.getString("correction", "B").orEmpty()) { prefs.edit().putString("correction", it).apply() }
        toggle(writing, "Polish while I speak", "Turn off to apply your writing style only when you finish.", "live_cleanup", true)
        toggle(writing, "Remove fillers & repeated words", "Remove obvious fillers such as “um” and accidental exact repetitions.", "fillers", false)
    }
    private data class Choice(val value: String, val title: String, val explanation: String)
    private fun language(body: LinearLayout) {
        heading(body, "Language & vocabulary", "Help AlienFlow hear the words you use.")
        val languages = listOf("" to "Detect automatically", "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German", "it" to "Italian", "pt" to "Portuguese", "ja" to "Japanese", "ko" to "Korean", "zh" to "Chinese", "ar" to "Arabic", "hi" to "Hindi")
        val settings = ui.card(body)
        ui.text(settings, "Spoken language", 18, bold = true)
        val selected = languages.firstOrNull { it.first == app.store.language() }?.second ?: "Other: ${app.store.language()}"
        ui.button(settings, selected) {
            val labels = languages.map { it.second } + "Another language…"
            AlertDialog.Builder(this).setTitle("Spoken language").setSingleChoiceItems(labels.toTypedArray(), languages.indexOfFirst { it.first == app.store.language() }) { dialog, which ->
                dialog.dismiss()
                if (which < languages.size) { prefs.edit().putString("language", languages[which].first).apply(); rememberPage(); renderPage() }
                else customLanguage()
            }.setNegativeButton("Cancel", null).show()
        }.apply { tag = "spoken-language" }
        ui.text(settings, "Choosing a language helps recognition. Automatic detection is useful when you switch languages.", 14, ui.muted)
        val vocabulary = ui.card(body)
        ui.text(vocabulary, "Personal vocabulary", 18, bold = true)
        toggle(vocabulary, "Use my vocabulary", "Optional. Turn on only for names or terms AlienFlow often mishears. Off sends no vocabulary.", "use_vocabulary", false)
        ui.text(vocabulary, "Keep a short list of names or specialist terms, separated by commas or new lines. Duplicate entries are sent once. Your saved list stays here when switched off.", 14, ui.muted)
        val words = edit(vocabulary, "For example: Siobhan, Kubernetes, AlienFlow", prefs.getString("vocabulary", "").orEmpty(), multi = true).apply { tag = "vocabulary"; minLines = 4; filters = arrayOf(InputFilter.LengthFilter(4000)) }
        val count = ui.text(vocabulary, "${words.text.length} / 4,000 characters", 13, ui.muted)
        words.watch { prefs.edit().putString("vocabulary", it).apply(); count.text = "${it.length} / 4,000 characters" }
        ui.text(body, "Saved automatically. Changes apply to your next dictation.", 13, ui.muted)
    }
    private fun customLanguage() {
        val input = EditText(this).apply { hint = "Language code, for example: nl"; isSingleLine = true; setText(app.store.language()) }
        val dialog = AlertDialog.Builder(this).setTitle("Another language").setMessage("Enter a two-letter language code, such as nl for Dutch. Leave blank for automatic detection.").setView(input).setPositiveButton("Save", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val code = input.text.toString().trim().lowercase(Locale.ROOT)
            if (code.isNotEmpty() && !code.matches(Regex("[a-z]{2}"))) input.error = "Use a two-letter code, or leave blank."
            else { prefs.edit().putString("language", code).apply(); dialog.dismiss(); rememberPage(); renderPage() }
        } }; dialog.show()
    }
    private fun privacy(body: LinearLayout) {
        heading(body, "Privacy & saving", "You decide what stays on this phone.")
        val saving = ui.card(body)
        ui.text(saving, "Keep transcripts", 18, bold = true)
        ui.text(saving, "Find saved text in History. Choosing a shorter period removes older entries when history is refreshed. Off removes saved history on refresh.", 14, ui.muted)
        choose(saving, "retention", listOf(Choice("-1", "Off", "Do not keep completed transcripts."), Choice("1", "1 day", "For a little room to revisit your words."), Choice("7", "7 days", "A week of recent dictations."), Choice("30", "30 days", "Keep a month of transcripts."), Choice("0", "Until I delete them", "Keep transcripts until you remove them.")), prefs.getInt("retention", 7).toString()) { prefs.edit().putInt("retention", it.toInt()).apply() }
        val connection = ui.card(body)
        toggle(connection, "Keep recording if the connection drops", "Save audio privately on this phone without reconnecting or uploading it automatically. Turn off to stop when the connection is lost.", "continue_local", true)
        ui.text(connection, "Completed audio is deleted. Interrupted recordings can remain for up to 24 hours, even when transcript saving is off. Recovery is a separate paid upload that starts only when you choose it in History; it never types into a new field.", 14, ui.muted)
        val data = ui.card(body)
        ui.text(data, "What leaves this phone", 18, bold = true)
        ui.text(data, "Audio goes to OpenAI for transcription. Your dictated words and a short excerpt around the insertion point may be sent for your chosen writing style. Existing text is read-only context; password fields are excluded. Vocabulary hints are sent only when enabled. Your full history is not used as a prompt.", 14, ui.muted)
        ui.text(data, "Your API key is encrypted on this device. Keys, transcripts and recordings are excluded from Android backup.", 14, ui.muted)
        ui.button(body, "Delete all saved transcripts", destructive = true) { confirm("Delete all transcripts?", "This permanently removes saved text. Pending recordings and your account settings stay in place.") { app.store.deleteHistory(); notice("Saved transcripts deleted.") } }
    }
    private fun account(body: LinearLayout) {
        heading(body, "Account & setup", "Connect once. Dictate wherever you write.")
        val connection = ui.card(body)
        ui.text(connection, "Your OpenAI connection", 18, bold = true)
        ui.text(connection, "This version uses your own OpenAI API account. API usage is billed by OpenAI separately; a ChatGPT subscription does not include API usage.", 14, ui.muted)
        toggle(connection, "Allow paid API requests", "Off pauses all API activity. This safety update starts paused, including on existing installations. Turning it on permits requests only when you start dictation, check the connection, or request recovery.", "paid_api_enabled", false)
        ui.button(connection, "Pause all API activity", destructive = true) { app.coordinator.pausePaidApi(); rememberPage(); renderPage() }
        accountKeyState = ui.text(connection, if (prefs.contains("api_key")) "API key saved on this phone" else "No API key saved", 14, ui.accent)
        val key = edit(connection, "Paste your OpenAI API key", "", password = true).apply { tag = "api-key"; isSaveEnabled = false; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS }
        ui.button(connection, "Save API key", primary = true) {
            if (key.text.isNullOrBlank()) key.error = "Paste a key first."
            else try { app.store.saveKey(key.text.toString()); key.setText(""); accountKeyState?.text = "API key saved securely" } catch (_: Exception) { accountKeyState?.text = "Could not save the key. Try again." }
        }
        ui.button(connection, "Check connection") { app.coordinator.testKey() }
        status = ui.text(connection, "", 14, ui.muted).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        ui.button(connection, "Remove saved key", destructive = true) { confirm("Remove your API key?", "You will need to add a key again to dictate. Saved transcripts stay on this phone.") { app.coordinator.pausePaidApi(); app.store.saveKey(""); accountKeyState?.text = "No API key saved"; key.setText("") } }
        val permissions = ui.card(body)
        ui.text(permissions, "Phone permissions", 18, bold = true)
        microphoneState = ui.text(permissions, if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "Microphone allowed" else "Microphone access needed", 14, ui.accent)
        ui.button(permissions, "Allow microphone & notifications") { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS), 1) }
        accessibilityState = ui.text(permissions, if (app.coordinator.service != null) "Accessibility connected" else "Accessibility needs to be enabled", 14, ui.accent)
        ui.text(permissions, "Accessibility lets the alien place text while keeping your keyboard. Typing continues to work during dictation. Password fields are excluded. Switching to another field keeps the recording out of that new field; your result remains in AlienFlow.", 14, ui.muted)
        ui.text(permissions, "AlienFlow stays ready in the background with a notification while accessibility is connected. You do not need to keep its screen open. The microphone starts only when you tap the alien. Hide alien pauses background readiness; Reset position brings it back.", 14, ui.muted)
        ui.button(permissions, "Open accessibility settings") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshSetup(); renderStatus()
    }
    private fun help(body: LinearLayout) {
        heading(body, "Help & about", "A few things worth knowing.")
        val guide = ui.card(body)
        ui.text(guide, "Everyday dictation", 18, bold = true)
        ui.text(guide, "1. Select a text field in your app.\n2. Tap the alien to start. Speak when its green dot appears.\n3. Tap again to finish. In Termux, your words arrive after you stop. AlienFlow never presses Enter for you.")
        ui.text(guide, "Drag the alien to move it. Open its menu to hide it or reach Settings. The cyan rim shows a live connection; amber means local recording or finishing.", 14, ui.muted)
        val troubleshooting = ui.card(body)
        ui.text(troubleshooting, "Troubleshooting", 18, bold = true)
        ui.text(troubleshooting, "Alien missing? Select a supported text field, check accessibility in Account & setup, or use “Bring the alien back” in Floating alien.", 14, ui.muted)
        toggle(troubleshooting, "Show error details beside the alien", "Helpful while reporting a problem. Turn off for a quieter experience. Routine completion messages never appear there.", "diagnostic_bubble_errors", true)
        ui.button(troubleshooting, "Diagnostics / share report") { startActivity(Intent(this, DiagnosticsActivity::class.java)) }
        ui.text(troubleshooting, "View technical information before copying, saving or sharing it. Reports exclude API keys, audio and dictated text.", 13, ui.muted)
        ui.text(body, "AlienFlow", 20, bold = true)
        ui.text(body, "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", 14, ui.muted)
    }

    private fun historyPage(body: LinearLayout) {
        heading(body, "History", "Your words, easy to find.", leaf = false)
        historySummary = ui.text(body, "", 14, ui.muted)
        ui.row(body, "Interrupted recordings", "Recover or discard audio saved on this phone") { open(Page.RECOVERY) }
            .apply { tag = "recovery-link"; recoveryRow = getChildAt(0).let { (it as LinearLayout).getChildAt(1) as TextView } }
        val search = edit(body, "Search your transcripts", query).apply { tag = "history-search"; inputType = InputType.TYPE_CLASS_TEXT }
        historyNotice = ui.text(body, "", 15, ui.muted)
        historyList = ListView(this).apply {
            tag = "transcript-list"; divider = null; dividerHeight = ui.dp(8)
            isVerticalScrollBarEnabled = false
            adapter = TranscriptAdapter().also { historyAdapter = it }
            setOnItemClickListener { _, _, position, _ -> selectedId = historyAdapter!!.items[position].getString("id"); open(Page.TRANSCRIPT) }
        }
        body.addView(historyList, LinearLayout.LayoutParams(-1, 0, 1f))
        search.watch { query = it; listPosition = 0; listOffset = 0; filterHistory() }
    }
    private var entries = emptyList<JSONObject>()
    private fun refreshHistory() {
        if (page != Page.HISTORY) return
        try {
            entries = app.store.history()
            val period = when (val days = prefs.getInt("retention", 7)) { -1 -> "Saving is off"; 0 -> "Kept until you delete them"; 1 -> "Kept for 1 day"; else -> "Kept for $days days" }
            historySummary?.text = "${entries.size} ${if (entries.size == 1) "transcript" else "transcripts"} · $period"
            filterHistory()
            val pending = app.store.pending()
            recoveryRow?.text = if (pending.isEmpty()) "No recordings waiting" else "${pending.size} waiting · kept for up to 24 hours"
        } catch (_: Exception) {
            entries = emptyList(); historyAdapter?.items = emptyList(); historyAdapter?.notifyDataSetChanged()
            historyNotice?.apply { text = "History couldn’t be opened. Your files have been left in place. Try opening History again."; visibility = View.VISIBLE }
        }
    }
    private fun filterHistory() {
        val filtered = if (query.isBlank()) entries else entries.filter { it.optString("result").contains(query, ignoreCase = true) || it.optString("original").contains(query, ignoreCase = true) }
        val adapter = historyAdapter ?: return
        adapter.items = filtered; adapter.notifyDataSetChanged()
        historyList?.setSelectionFromTop(listPosition.coerceAtMost((filtered.size - 1).coerceAtLeast(0)), listOffset)
        historyNotice?.apply { visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE; text = if (query.isNotBlank()) "No matching transcripts. Try a different word." else "Nothing saved yet. Your completed dictations will appear here when saving is on." }
    }
    private inner class TranscriptAdapter : BaseAdapter() {
        var items = emptyList<JSONObject>()
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? LinearLayout) ?: ui.column().apply {
                background = ui.ripple(ui.surface, 16, ui.border); setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
                addView(ui.text("", 12, ui.accent))
                addView(ui.text("", 16).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(0, ui.dp(5), 0, 0) })
                minimumHeight = ui.dp(88)
            }
            val item = items[position]
            (row.getChildAt(0) as TextView).text = date(item.getLong("time"))
            (row.getChildAt(1) as TextView).text = item.optString("result").ifBlank { item.optString("original") }
            return row
        }
    }
    private fun transcript(body: LinearLayout) {
        val item = try { app.store.history().firstOrNull { it.optString("id") == selectedId } } catch (_: Exception) { null }
        heading(body, "Transcript", item?.let { date(it.getLong("time")) } ?: "This transcript is no longer available.")
        if (item == null) return
        val words = item.optString("result").ifBlank { item.optString("original") }
        val content = ui.card(body)
        ui.text(content, words, 18).apply { setTextIsSelectable(true); tag = "transcript-text" }
        ui.button(body, "Copy transcript", primary = true) { copy(words) }
        if (item.optString("original") != words && item.optString("original").isNotBlank()) {
            val original = ui.text(body, item.optString("original"), 16, ui.muted).apply { visibility = View.GONE; setTextIsSelectable(true) }
            ui.button(body, "Show original words") { original.visibility = if (original.visibility == View.GONE) View.VISIBLE else View.GONE }
        }
        val details = ui.text(body, item.optString("status"), 14, ui.muted).apply { visibility = View.GONE }
        ui.button(body, "View dictation details") { details.visibility = if (details.visibility == View.GONE) View.VISIBLE else View.GONE }
        ui.button(body, "Delete transcript", destructive = true) { confirm("Delete this transcript?", "This cannot be undone.") { app.store.deleteHistory(item.getString("id")); backStack.clear(); open(Page.HISTORY, push = false) } }
    }
    private fun currentResult(body: LinearLayout) {
        heading(body, "Latest result", "The result of your most recent dictation.")
        val card = ui.card(body)
        resultPreview = ui.text(card, "", 18).apply { setTextIsSelectable(true) }
        status = ui.text(body, "", 14, ui.muted)
        ui.button(body, "Copy latest result", primary = true) { copy(app.coordinator.result) }
    }
    private fun recovery(body: LinearLayout) {
        heading(body, "Interrupted recordings", "Recover your words without restarting the microphone.")
        ui.text(body, "These recordings stay privately on this phone for up to 24 hours. Recovery is a paid upload using your API account. It never starts automatically. Leaving AlienFlow or tapping Cancel upload stops the request; already processed audio may still be billed. Each attempt stops after two minutes.", 14, ui.muted)
        status = ui.text(body, "", 14, ui.accent)
        ui.button(body, "Cancel upload") { app.coordinator.cancelAuxiliaryRequests() }
        val pending = app.store.pending()
        recoverySignature = pending.joinToString { it.name }
        if (pending.isEmpty()) ui.text(body, "All caught up. No recordings are waiting.", 18)
        pending.forEach { file ->
            val card = ui.card(body)
            ui.text(card, date(file.lastModified()), 17, bold = true)
            ui.text(card, "${file.length() / 1024} KB · saved on this phone", 14, ui.muted)
            ui.button(card, "Recover transcript (uses API credit)") { app.coordinator.recover(file) }
            ui.button(card, "Discard recording", destructive = true) { confirm("Discard this recording?", "The audio will be permanently deleted.") { if (!app.coordinator.ownsAudio(file)) file.delete(); rememberPage(); renderPage() } }
        }
        ui.button(body, "Open latest result") { open(Page.RESULT) }
    }
    private fun refreshRecovery() {
        if (page == Page.RECOVERY && app.store.pending().joinToString { it.name } != recoverySignature) { rememberPage(); renderPage() }
    }
    private fun date(time: Long) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
    private fun copy(value: String) { if (value.isNotBlank()) getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("AlienFlow dictation", value)) }
    private fun notice(value: String) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show() }
    private fun confirm(title: String, message: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Delete") { _, _ -> action() }.setNegativeButton("Cancel", null).show()
    }
    private fun edit(parent: LinearLayout, hintText: String, value: String, multi: Boolean = false, password: Boolean = false): EditText {
        return EditText(this).apply {
            hint = hintText; setTextColor(ui.ink); setHintTextColor(ui.muted); textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT or when { password -> InputType.TYPE_TEXT_VARIATION_PASSWORD; multi -> InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; else -> InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }
            gravity = Gravity.TOP or Gravity.START
            if (!multi) isSingleLine = true
            setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14)); minimumHeight = ui.dp(54)
            background = ui.shape(ui.background, 12, ui.border)
            setText(value)
            parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(8); bottomMargin = ui.dp(12) })
        }
    }
    private fun EditText.watch(action: (String) -> Unit) { addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) { action(s.toString()) }
    }) }
    private fun toggle(parent: LinearLayout, label: String, explanation: String, key: String, default: Boolean) {
        parent.addView(Switch(this).apply {
            text = label; textSize = 16f; setTextColor(ui.ink); minHeight = ui.dp(52)
            switchMinWidth = ui.dp(48); switchPadding = ui.dp(12)
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(ui.accent, ui.muted))
            isChecked = prefs.getBoolean(key, default); tag = key
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(key, checked).apply()
                if (key == "paid_api_enabled" && !checked) app.coordinator.pausePaidApi()
                app.coordinator.service?.refreshBubble()
            }
        }, LinearLayout.LayoutParams(-1, -2))
        ui.text(parent, explanation, 14, ui.muted).apply { setPadding(0, 0, 0, ui.dp(8)) }
    }
    private fun slider(parent: LinearLayout, label: String, key: String, default: Int, low: Int, high: Int, format: (Int) -> String) {
        val current = prefs.getInt(key, default).coerceIn(low, high)
        val caption = ui.text(parent, "$label · ${format(current)}", 16, bold = true)
        parent.addView(SeekBar(this).apply {
            tag = key; contentDescription = label; max = high - low; progress = current - low
            progressTintList = ColorStateList.valueOf(ui.accent); thumbTintList = ColorStateList.valueOf(ui.accent)
            minimumHeight = ui.dp(48)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) { val value = progress + low; prefs.edit().putInt(key, value).apply(); caption.text = "$label · ${format(value)}"; app.coordinator.service?.refreshBubble(); host.findViewWithTag<View>("bubble-preview")?.invalidate() } }
            })
        }, LinearLayout.LayoutParams(-1, ui.dp(48)))
    }
    private fun choose(parent: LinearLayout, tag: String, choices: List<Choice>, selected: String, save: (String) -> Unit) {
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL; this.tag = tag }
        choices.forEach { choice ->
            group.addView(RadioButton(this).apply {
                id = View.generateViewId(); this.tag = choice.value
                text = "${choice.title}\n${choice.explanation}"; textSize = 15f; setTextColor(ui.ink)
                minHeight = ui.dp(64); setPadding(ui.dp(8), ui.dp(12), ui.dp(8), ui.dp(12))
                buttonTintList = ColorStateList.valueOf(ui.accent)
                isChecked = choice.value == selected
            }, RadioGroup.LayoutParams(-1, -2))
        }
        group.setOnCheckedChangeListener { radio, id -> radio.findViewById<RadioButton>(id)?.let { save(it.tag as String) } }
        parent.addView(group)
    }
}
