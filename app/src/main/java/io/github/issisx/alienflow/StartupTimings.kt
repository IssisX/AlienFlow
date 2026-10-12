package io.github.issisx.alienflow

/** Monotonic, first-observation timings. Contains no audio, transcript, key, or editor data. */
class StartupTimings {
    private var tap: Long? = null
    private var microphone: Long? = null
    private var server: Long? = null
    private var transcript: Long? = null
    fun begin(nowMs: Long) { tap = nowMs; microphone = null; server = null; transcript = null }
    fun captured(nowMs: Long) { if (tap != null && microphone == null) microphone = nowMs - tap!! }
    fun connected(nowMs: Long) { if (tap != null && server == null) server = nowMs - tap!! }
    fun text(nowMs: Long): Boolean {
        if (tap == null || transcript != null) return false
        transcript = nowMs - tap!!
        return true
    }
    fun metadata(): String = "tap_to_microphone_ms=${microphone ?: "pending"}; " +
        "tap_to_server_ms=${server ?: "pending"}; tap_to_first_text_ms=${transcript ?: "pending"}"
}
