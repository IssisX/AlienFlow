package io.github.issisx.alienflow

/** Source revisions and keyboard text have separate owners. After an interaction,
 * previously dispatched speech is immutable in the field; only its new tail is editable.
 * Token boundaries keep case/punctuation revisions and partial-word growth out of a
 * user's edits. No timers, capture changes, or app names participate in this rule.
 */
internal class SpeechContinuation {
    private data class Word(val key: String, val start: Int)
    private var source = ""
    private var protectedWords = 0
    private var hasProtectedSpeech = false

    private fun words(text: String) = Regex("\\S+").findAll(text).map {
        Word(it.value.lowercase().trim { c -> !c.isLetterOrDigit() }, it.range.first)
    }.toList()

    fun reset() { source = ""; protectedWords = 0; hasProtectedSpeech = false }
    fun protectDispatched() { protectedWords = words(source).size; hasProtectedSpeech = true }

    /** Preview only: an undelivered hypothesis never moves the source frontier. */
    fun tail(next: String): String {
        if (!hasProtectedSpeech) return next
        val old = words(source); val fresh = words(next)
        val boundary = mappedBoundary(old, fresh)
        if (boundary >= fresh.size) return ""
        // Include the original separator before the next word, not a fabricated prefix.
        var start = fresh[boundary].start
        while (start > 0 && next[start - 1].isWhitespace()) start--
        return next.substring(start)
    }

    fun dispatched(next: String) {
        if (hasProtectedSpeech) protectedWords = mappedBoundary(words(source), words(next))
        source = next
    }

    private fun mappedBoundary(old: List<Word>, fresh: List<Word>): Int {
        var prefix = 0
        while (prefix < minOf(old.size, fresh.size) && old[prefix].key == fresh[prefix].key) prefix++
        var suffix = 0
        while (suffix < minOf(old.size, fresh.size) - prefix &&
            old[old.lastIndex - suffix].key == fresh[fresh.lastIndex - suffix].key) suffix++
        return when {
            protectedWords <= prefix -> protectedWords
            protectedWords > old.size - suffix -> fresh.size - (old.size - protectedWords)
            else -> prefix + minOf(protectedWords - prefix, fresh.size - prefix - suffix)
        }.coerceIn(0, fresh.size)
    }
}
