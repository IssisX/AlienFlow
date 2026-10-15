package io.github.issisx.alienflow

/**
 * Light polish keeps every recognized word. Where the model's words align with the
 * recognized words, its casing and punctuation are adopted; any other lexical change
 * reverts to the recognized words unless one bounded rule below authorizes it.
 * A single unauthorized word therefore costs only that word, not the whole polish.
 */
internal object PolishMerge {
    private class Token(val separator: String, val surface: String, val key: String)

    /** Alignment is quadratic; larger inputs keep the whole-output decision. */
    private const val MAX_ALIGNMENT_CELLS = 4_000_000L
    private const val EDITORIAL = ".:\"(),!?;…“”«»[]{}"
    private val contractions = mapOf("dont" to "don't", "cant" to "can't", "wont" to "won't", "isnt" to "isn't", "didnt" to "didn't")
    private val explicitSpelling = mapOf("thet" to "that", "teh" to "the", "thier" to "their", "recieve" to "receive", "becuase" to "because")
    private val fillers = setOf("um", "uh")

    /** Meaning-bearing words never change spelling: negation, modality, quantity, time, direction, profanity. */
    internal val protectedWords = setOf(
        "not", "never", "no", "nor", "neither", "none", "nothing", "nobody", "nowhere", "cannot", "without",
        "don't", "can't", "won't", "isn't", "didn't", "doesn't", "aren't", "wasn't", "weren't", "haven't", "hasn't",
        "hadn't", "shouldn't", "wouldn't", "couldn't", "mustn't",
        "might", "maybe", "possibly", "perhaps", "probably", "could", "would", "should", "will", "shall", "must",
        "can", "may", "think", "sure", "likely", "unlikely", "certainly", "definitely", "always", "sometimes",
        "fucking", "fuck", "shit", "bullshit", "damn",
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
        "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty", "thirty",
        "forty", "fifty", "sixty", "seventy", "eighty", "ninety", "hundred", "thousand", "million", "billion",
        "first", "second", "third", "half", "once", "twice", "double", "triple", "minus", "plus", "percent",
        "dollars", "dollar", "cents", "euros", "pounds", "grams", "kilograms", "miles", "kilometers",
        "seconds", "minutes", "hours", "days", "weeks", "months", "years",
        "today", "tonight", "tomorrow", "yesterday", "monday", "tuesday", "wednesday", "thursday", "friday",
        "saturday", "sunday", "before", "after", "above", "below", "over", "under", "left", "right",
        "more", "less", "most", "least", "all", "every", "some", "many", "few", "yes", "yeah", "true", "false",
    )

    fun merge(source: String, result: String, mode: String, removeFillers: Boolean): String {
        if (CorrectionGuard.accepts(source, result, mode, removeFillers)) return result
        if (mode != "B" || result.isBlank() || result.length > source.length * 3 + 64) return source
        val sourceWords = tokens(source).filter { it.key.isNotEmpty() }
        val resultTokens = tokens(result)
        val resultWords = resultTokens.indices.filter { resultTokens[it].key.isNotEmpty() }
        if (sourceWords.isEmpty() || sourceWords.size.toLong() * resultWords.size > MAX_ALIGNMENT_CELLS) return source
        val n = sourceWords.size; val m = resultWords.size
        val common = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            common[i][j] = if (sourceWords[i].key == resultTokens[resultWords[j]].key) common[i + 1][j + 1] + 1
                else maxOf(common[i + 1][j], common[i][j + 1])
        }
        val out = StringBuilder()
        fun emit(separator: String, surface: String) {
            out.append(if (out.isEmpty()) "" else separator.ifEmpty { " " }).append(surface)
        }
        var emittedThrough = 0 // next unconsumed index into resultTokens
        fun emitResultThrough(index: Int) {
            for (k in emittedThrough..index) emit(resultTokens[k].separator, resultTokens[k].surface)
            emittedThrough = index + 1
        }
        fun resolve(fromSource: Int, toSource: Int, fromResult: Int, toResult: Int, nextMatch: Int) {
            val removed = (fromSource until toSource).map { sourceWords[it] }
            val added = (fromResult until toResult).map { resultWords[it] }
            when {
                removed.isEmpty() -> emittedThrough = nextMatch // Unrecognized words are never added.
                added.isEmpty() -> {
                    for ((offset, word) in removed.withIndex()) {
                        val previous = (fromSource + offset - 1).takeIf { it >= 0 }?.let { sourceWords[it].key }
                        val deletable = removeFillers && (word.key in fillers || word.key == previous)
                        if (!deletable) emit(" ", word.surface)
                    }
                    emittedThrough = nextMatch
                }
                removed.size == added.size -> {
                    for ((offset, word) in removed.withIndex()) {
                        val proposal = resultTokens[added[offset]]
                        if (spellingFix(word, proposal)) emitResultThrough(added[offset])
                        else { emit(proposal.separator, word.surface); emittedThrough = added[offset] + 1 }
                    }
                    emittedThrough = nextMatch
                }
                else -> { for (word in removed) emit(" ", word.surface); emittedThrough = nextMatch }
            }
        }
        var i = 0; var j = 0
        while (i < n || j < m) {
            if (i < n && j < m && sourceWords[i].key == resultTokens[resultWords[j]].key) {
                emitResultThrough(resultWords[j]); i++; j++; continue
            }
            val fromSource = i; val fromResult = j
            while ((i < n || j < m) && !(i < n && j < m && sourceWords[i].key == resultTokens[resultWords[j]].key &&
                    common[i][j] == common[i + 1][j + 1] + 1)) {
                if (j < m && (i == n || common[i][j + 1] >= common[i + 1][j])) j++ else i++
            }
            resolve(fromSource, i, fromResult, j, if (j < m) resultWords[j] else resultTokens.size)
        }
        // Trailing editorial tokens (for example a closing quote) follow the last aligned word.
        for (k in emittedThrough until resultTokens.size) emit(resultTokens[k].separator, resultTokens[k].surface)
        return out.toString()
    }

    /** A bounded, model-proposed near-miss of one ordinary lowercase word. Never names or meaning words. */
    private fun spellingFix(source: Token, proposal: Token): Boolean {
        val a = source.key; val b = proposal.key
        if (source.surface.any { it.isUpperCase() }) return false
        if (explicitSpelling[a] == b) return true
        if (!a.all { it.isLetter() } || !b.all { it.isLetter() }) return false
        if (a in protectedWords || b in protectedWords) return false
        if (a.length < 4 || b.length < 4 || a[0] != b[0]) return false
        return distance(a, b) <= if (maxOf(a.length, b.length) >= 7) 2 else 1
    }

    /** Optimal string alignment distance; inputs are single short words. */
    private fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (x in 0..a.length) d[x][0] = x
        for (y in 0..b.length) d[0][y] = y
        for (x in 1..a.length) for (y in 1..b.length) {
            val cost = if (a[x - 1] == b[y - 1]) 0 else 1
            d[x][y] = minOf(d[x - 1][y] + 1, d[x][y - 1] + 1, d[x - 1][y - 1] + cost)
            if (x > 1 && y > 1 && a[x - 1] == b[y - 2] && a[x - 2] == b[y - 1]) d[x][y] = minOf(d[x][y], d[x - 2][y - 2] + 1)
        }
        return d[a.length][b.length]
    }

    private fun tokens(text: String): List<Token> =
        Regex("(\\s*)(\\S+)").findAll(text).map { Token(it.groupValues[1], it.groupValues[2], key(it.groupValues[2])) }.toList()

    /** Lexical identity: case, edge punctuation and a sentence-final period are editorial; leading dots are not. */
    internal fun key(surface: String): String {
        if (surface.all { it in EDITORIAL }) return ""
        var value = surface.lowercase().replace('’', '\'').replace('‘', '\'')
        value = value.trimStart { it in "\"“”«»([{¿¡" }.trimEnd { it in "\"“”«»)]},!?;…" }
        val withoutStop = value.trimEnd('.', ':')
        if (withoutStop.isNotEmpty() && withoutStop.last().isLetterOrDigit()) value = withoutStop
        return contractions[value] ?: value
    }
}
