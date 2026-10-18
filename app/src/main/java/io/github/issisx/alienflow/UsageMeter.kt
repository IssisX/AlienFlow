package io.github.issisx.alienflow

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * OpenAI usage counted on this phone per local calendar day: realtime audio actually sent,
 * audio uploaded by recovery, polish requests and connection checks. Metadata only; it never
 * sees or stores text, audio or keys. Local counts are an estimate, not the provider's bill.
 *
 * Counts accumulate in memory. They are written when at least [PERSIST_EVERY_BYTES] of new
 * audio has been counted and whenever the owner calls [flush] (session end, recovery end,
 * connection check). A crash can therefore lose up to about 5 s of audio, and the polish
 * requests made since the last write, from the count; that is acceptable for a spending guard
 * and keeps the 20 ms capture path free of disk work. Every method is thread-safe: the
 * capture, network and UI threads all report here.
 */
class UsageMeter(
    private val read: () -> String?,
    private val write: (String) -> Unit,
    private val currentDate: () -> LocalDate,
) {
    data class Day(
        val date: LocalDate,
        val realtimeBytes: Long = 0,
        val recoveryBytes: Long = 0,
        val polishRequests: Int = 0,
        val connectionChecks: Int = 0,
    ) {
        /** What the daily limit counts: realtime plus recovery audio. */
        val audioBytes: Long get() = realtimeBytes + recoveryBytes
    }

    private val days = sortedMapOf<LocalDate, Day>()
    private var loaded = false
    private var dirty = false
    private var unsavedAudioBytes = 0L

    /** Returns today's realtime plus recovery audio bytes after adding [bytes]. */
    @Synchronized fun addRealtimeAudio(bytes: Int): Long = addAudio(bytes.toLong(), recovery = false)
    @Synchronized fun addRecoveryAudio(bytes: Long): Long = addAudio(bytes, recovery = true)
    @Synchronized fun addPolishRequest() { change { it.copy(polishRequests = it.polishRequests + 1) } }
    @Synchronized fun addConnectionCheck() { change { it.copy(connectionChecks = it.connectionChecks + 1) } }

    @Synchronized fun today(): Day {
        val date = current()
        return days[date] ?: Day(date)
    }
    /** Stored days, newest first, within the retention window. */
    @Synchronized fun days(): List<Day> { current(); return days.values.sortedByDescending { it.date } }
    @Synchronized fun flush() { if (dirty) persist() }

    fun limitReached(minutes: Int): Boolean = today().audioBytes >= limitBytes(minutes)
    fun remainingBytes(minutes: Int): Long = (limitBytes(minutes) - today().audioBytes).coerceAtLeast(0L)

    fun summary(): String {
        val day = today()
        return "Used today: ${duration(day.audioBytes)} of audio · ${day.polishRequests} polish " +
            if (day.polishRequests == 1) "request" else "requests"
    }

    fun diagnostic(limitMinutes: Int): String = buildString {
        appendLine("USAGE ON THIS PHONE (metadata only)")
        appendLine("Daily dictation limit: ${if (limitMinutes <= 0) "Off" else "$limitMinutes minutes"} of realtime and recovery audio. Local counts, not an OpenAI bill.")
        val stored = days()
        if (stored.isEmpty()) appendLine("No usage recorded in the last $RETENTION_DAYS days.")
        for (day in stored) appendLine("${day.date} realtime_audio_seconds=${seconds(day.realtimeBytes)}; " +
            "recovery_audio_seconds=${seconds(day.recoveryBytes)}; polish_requests=${day.polishRequests}; " +
            "connection_checks=${day.connectionChecks}")
    }

    private fun addAudio(bytes: Long, recovery: Boolean): Long {
        if (bytes <= 0L) return today().audioBytes
        val day = change { if (recovery) it.copy(recoveryBytes = it.recoveryBytes + bytes) else it.copy(realtimeBytes = it.realtimeBytes + bytes) }
        unsavedAudioBytes += bytes
        if (unsavedAudioBytes >= PERSIST_EVERY_BYTES) persist()
        return day.audioBytes
    }

    private inline fun change(update: (Day) -> Day): Day {
        val date = current()
        val next = update(days[date] ?: Day(date))
        days[date] = next
        dirty = true
        return next
    }

    /** Loads once, then drops days outside the window. A day moved into the future by a clock
     * change is dropped too, so a stale entry cannot occupy the window indefinitely. */
    private fun current(): LocalDate {
        if (!loaded) {
            loaded = true
            try { read()?.let(::decode)?.forEach { days[it.date] = it } } catch (_: Exception) { /* Start counting afresh. */ }
        }
        val date = currentDate()
        val oldest = date.minusDays(RETENTION_DAYS - 1L)
        if (days.keys.removeAll { it.isBefore(oldest) || it.isAfter(date) }) dirty = true
        return date
    }

    private fun persist() {
        try {
            write(encode(days.values))
            dirty = false
            unsavedAudioBytes = 0L
        } catch (_: Exception) { /* Counting continues in memory; the next write retries. */ }
    }

    companion object {
        const val RETENTION_DAYS = 7
        const val PERSIST_EVERY_BYTES = 5L * RealtimeProtocol.BYTES_PER_SECOND
        /** 0 minutes means Off: no daily limit. */
        fun limitBytes(minutes: Int): Long = if (minutes <= 0) Long.MAX_VALUE else minutes * 60L * RealtimeProtocol.BYTES_PER_SECOND
        fun seconds(bytes: Long): Long = bytes / RealtimeProtocol.BYTES_PER_SECOND
        fun duration(bytes: Long): String {
            val total = seconds(bytes)
            return if (total >= 60) "${total / 60} min ${total % 60} s" else "$total s"
        }

        /** "date,realtimeBytes,recoveryBytes,polishRequests,connectionChecks" records, ";"-separated. */
        internal fun encode(days: Collection<Day>): String = days.joinToString(";") {
            "${it.date},${it.realtimeBytes},${it.recoveryBytes},${it.polishRequests},${it.connectionChecks}"
        }
        internal fun decode(value: String): List<Day> = value.split(';').mapNotNull { record ->
            val fields = record.split(',')
            if (fields.size != 5) return@mapNotNull null
            val date = try { LocalDate.parse(fields[0]) } catch (_: DateTimeParseException) { return@mapNotNull null }
            val realtime = fields[1].toLongOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            val recovery = fields[2].toLongOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            val polish = fields[3].toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            val checks = fields[4].toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            Day(date, realtime, recovery, polish, checks)
        }
    }
}
