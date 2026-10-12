package io.github.issisx.alienflow

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

enum class EditorWriteResult { APPLIED, USER_EDIT, RETRY, FAILED }

/** Wait for independently observed node/IME state; never resend a mutation. */
object EditorSynchronization {
    data class Reading(val node: EditorSnapshot?, val keyboard: EditorEvidence.KeyboardText?, val composing: Boolean = false)
    fun inspect(before: EditorSnapshot, expected: EditorSnapshot, reading: Reading, allowComposition: Boolean = false): EditorWriteResult? {
        val observed = reading.node ?: return null
        val node = if (allowComposition) observed.copy(composing = false) else observed
        val keyboard = reading.keyboard ?: return null
        if (!allowComposition && (reading.composing || node.composing)) return EditorWriteResult.USER_EDIT
        if (EditorEvidence.corroborate(node, keyboard).snapshot == null) return null
        return when {
            node == expected -> EditorWriteResult.APPLIED
            node != before -> EditorWriteResult.USER_EDIT
            else -> null // Both may still expose the pre-commit state.
        }
    }
    suspend fun await(before: EditorSnapshot, expected: EditorSnapshot,
                      current: () -> Boolean, read: suspend () -> Reading,
                      attempts: Int = 16, intervalMs: Long = 50, allowComposition: Boolean = false): EditorWriteResult {
        // The platform's surrounding-text IPC may take a couple of seconds. This
        // is a ceiling, never a startup delay; the first matching observation wins.
        return withTimeoutOrNull(3_000L) {
            repeat(attempts) { index ->
                if (!current()) return@withTimeoutOrNull EditorWriteResult.USER_EDIT
                val reading = read()
                if (!current()) return@withTimeoutOrNull EditorWriteResult.USER_EDIT
                inspect(before, expected, reading, allowComposition)?.let { return@withTimeoutOrNull it }
                if (index + 1 < attempts) delay(intervalMs)
            }
            EditorWriteResult.FAILED
        } ?: EditorWriteResult.FAILED
    }
}

/** The final commit and WAV close follow the last capture callback, not a timer. */
object CaptureDrain {
    suspend fun finish(capture: Job?, commit: () -> Unit, complete: () -> Unit) {
        capture?.join()
        commit()
        complete()
    }
}
