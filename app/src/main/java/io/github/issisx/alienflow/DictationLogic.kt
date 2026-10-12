package io.github.issisx.alienflow

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object BubbleOpacity {
    const val DEFAULT_IDLE_PERCENT = 75
    fun alpha(opaquePercent: Int): Float = opaquePercent.coerceIn(0, 100) / 100f
}

class AudioCoverage {
    private val outstanding = linkedMapOf<String, Long>()
    private val acknowledged = mutableSetOf<String>()
    var uncertain = false; private set
    fun markUncertain() { uncertain = true }
    fun sent(id: String, bytes: Long = 0) { outstanding[id] = bytes }
    fun acknowledge(item: String) {
        if (item.isNotBlank() && acknowledged.add(item)) outstanding.keys.firstOrNull()?.let { outstanding.remove(it) }
    }
    fun rejectEmpty(clientEventId: String): Boolean {
        // A server-side empty buffer cannot establish that local audio was empty.
        uncertain = true
        return outstanding.remove(clientEventId) != null
    }
    fun bytesFor(id: String): Long? = outstanding[id]
}

/** Transcripts are keyed by item identity; completion replaces provisional words. */
class TranscriptLedger {
    data class Turn(val id: String, var previous: String?, var provisional: String = "", var final: String? = null)
    private val turns = linkedMapOf<String, Turn>()
    private val seenEvents = mutableSetOf<String>()
    var revision = 0L; private set
    fun event(eventId: String?, id: String, previous: String? = null, delta: String? = null, final: String? = null): Boolean {
        if (eventId != null && !seenEvents.add(eventId)) return false
        val t = turns.getOrPut(id) { Turn(id, previous) }
        val reordered = previous != null && t.previous != previous
        if (previous != null) t.previous = previous
        if (t.final != null) { if (reordered) revision++; return reordered }
        if (final != null) { if (t.final == final) return false; t.final = final }
        else if (delta != null) t.provisional += delta
        revision++
        return true
    }
    private fun ordered(): List<Turn> {
        val remaining = turns.values.toMutableList(); val result = mutableListOf<Turn>()
        while (remaining.isNotEmpty()) {
            val index = remaining.indexOfFirst { it.previous == null || result.any { p -> p.id == it.previous } || !turns.containsKey(it.previous) }
            result += remaining.removeAt(if (index < 0) 0 else index)
        }
        return result
    }
    fun text() = ordered().map { it.final ?: it.provisional }.filter { it.isNotBlank() }.joinToString(" ")
    fun stable() = ordered().mapNotNull { it.final }.filter { it.isNotBlank() }.joinToString(" ")
    fun finals() = ordered().mapNotNull { t -> t.final?.let { t.id to it } }
    fun displayed(corrections: Map<String, String>) = ordered().map { corrections[it.id] ?: it.final ?: it.provisional }.filter { it.isNotBlank() }.joinToString(" ")
    fun pending() = turns.values.any { it.final == null }
    fun has(id: String) = turns.containsKey(id)
}

/** B only accepts identical lexical tokens, allowing punctuation/case/apostrophes. */
object CorrectionGuard {
    private fun tokens(text: String): List<String> {
        // Preserve digits, signs, currency, paths and apostrophes. Only punctuation around
        // a word and case are editorial. Common ASR contraction spelling is explicit.
        val normalized = text.lowercase().replace('’', '\'')
        return Regex("[^\\s,!?;]+") .findAll(normalized).map { match ->
            val raw = match.value
            val token = if (raw.matches(Regex("[\\p{L}']+[.:]+"))) raw.trimEnd('.', ':') else raw
            if (token.all { it in ".:\"()" }) return@map ""
            when (token) { "dont" -> "don't"; "cant" -> "can't"; "wont" -> "won't"; "isnt" -> "isn't"; "didnt" -> "didn't"; else -> token }
        }.filter { it.isNotEmpty() }.toList()
    }
    fun accepts(source: String, result: String, mode: String, removeFillers: Boolean = false): Boolean {
        if (result.isBlank() || result.length > source.length * 3 + 64) return false
        if (mode == "A") return source == result
        if (mode == "C") {
            val protectedWords = setOf("not", "never", "no", "cannot", "without", "don't", "can't", "won't", "isn't", "didn't", "might", "maybe", "possibly", "perhaps", "probably", "could", "think", "fucking", "fuck", "shit", "bullshit", "damn", "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety", "hundred", "thousand", "million", "billion", "minus", "dollars", "euros", "grams", "kilograms", "miles", "kilometers")
            fun protectedTokens(t: List<String>) = t.filter { token -> token in protectedWords || token.any { it.isDigit() || it in "\$_/@\\:.-" } }.groupingBy { it }.eachCount()
            val names = Regex("\\b[A-Z][A-Za-z0-9]+\\b").findAll(source).map { it.value.lowercase() }.filter { it !in setOf("the", "this", "that", "it", "we", "you", "they", "he", "she", "a", "an") }.toSet()
            return protectedTokens(tokens(source)) == protectedTokens(tokens(result)) && names.all { name -> tokens(source).count { it == name || it.startsWith("$name'") } == tokens(result).count { it == name || it.startsWith("$name'") } }
        }
        val a = tokens(source); val b = tokens(result)
        if (a == b) return true
        // Conservative explicit day correction; sincere apologies and arbitrary names
        // are not correction commands. More ambiguous corrections stay verbatim.
        val days = setOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        val marker = a.indexOfFirst { it == "sorry" }
        if (marker > 0 && marker == a.size - 2 && a.count { it == "sorry" } == 1 &&
            a[marker - 1] in days && a.last() in days &&
            a.take(marker).none { it in setOf("said", "says", "say", "then", "because") } &&
            b == a.take(marker - 1) + a.last()) return true
        if (removeFillers) {
            val trimmed = a.filterNot { it in setOf("um", "uh") }.fold(mutableListOf<String>()) { out, word ->
                if (out.lastOrNull() != word) out.add(word); out
            }
            return b == trimmed
        }
        return false
    }
}

object CleanupChunks {
    /** Preserve exact separators and never cut a token or UTF16 surrogate pair. */
    fun split(source: String): List<String> {
        val chunks = mutableListOf<String>(); var remaining = source
        while (remaining.length > 3500) {
            val split = remaining.lastIndexOf(' ', 3500).takeIf { it > 0 } ?: break
            chunks.add(remaining.take(split + 1)); remaining = remaining.drop(split + 1)
        }
        chunks.add(remaining); return chunks
    }
}

/** Cancellation is checked around every blocking provider request. */
suspend fun cleanChunks(source: String, mode: String, fillers: Boolean, request: (String) -> String): String {
    val output = StringBuilder()
    for (chunk in CleanupChunks.split(source)) {
        currentCoroutineContext().ensureActive()
        val edited = if (chunk.isBlank() || chunk.length > 3500) chunk else request(chunk)
        currentCoroutineContext().ensureActive()
        val trailing = chunk.takeLastWhile { it.isWhitespace() }
        output.append(if (CorrectionGuard.accepts(chunk, edited, mode, fillers)) edited.trimEnd() + trailing else chunk)
    }
    return output.toString()
}

data class EditorSnapshot(val generation: Long, val text: String, val start: Int, val end: Int, val composing: Boolean = false)
class EditorOwnership(private val initial: EditorSnapshot, private val replaceSelection: Boolean = true) {
    var owned = ""; private set
    var certain = !initial.composing && initial.start >= 0 && initial.end >= initial.start && initial.end <= initial.text.length; private set
    private val prefix = initial.text.take((if (replaceSelection) initial.start else initial.end).coerceAtLeast(0))
    private val suffix = initial.text.drop(initial.end.coerceAtLeast(0))
    fun expected() = prefix + owned + suffix
    fun cursor() = prefix.length + owned.length
    fun proposedSnapshot(value: String) = EditorSnapshot(initial.generation, prefix + value + suffix,
        prefix.length + value.length, prefix.length + value.length)
    fun matches(snapshot: EditorSnapshot): Boolean = certain && snapshot.generation == initial.generation &&
        snapshot.text == expected() && snapshot.start == cursor() && snapshot.end == cursor() && !snapshot.composing
    fun canFirstWrite(snapshot: EditorSnapshot) = certain && snapshot == initial
    fun range() = prefix.length until (prefix.length + owned.length)
    fun initialRange() = (if (replaceSelection) initial.start else initial.end) until initial.end
    fun preservesAnchors(snapshot: EditorSnapshot) = snapshot.text.startsWith(prefix) && snapshot.text.endsWith(suffix) &&
        snapshot.start == snapshot.end && snapshot.end == snapshot.text.length - suffix.length
    fun separateFromTypedWord(value: String): String =
        if (prefix.lastOrNull()?.isLetterOrDigit() == true && value.firstOrNull()?.isLetterOrDigit() == true) " $value" else value
    fun ownsWholeField() = prefix.isEmpty() && suffix.isEmpty() && initial.text.isEmpty()
    fun confirm(snapshot: EditorSnapshot, proposed: String): Boolean {
        val old = owned; owned = proposed
        if (!matches(snapshot)) { owned = old; certain = false; return false }
        return true
    }
    fun invalidate() { certain = false }
    fun originalSelection() = if (replaceSelection) initial.text.substring(initial.start, initial.end) else ""
}
