package io.github.issisx.alienflow

import org.json.JSONArray
import org.json.JSONObject

/**
 * Transcript quality against a human reference. Word errors, capitalization, meaning-bearing
 * words, sentence breaks and missing phrases are counted separately: one number cannot show
 * whether a change fixed casing while dropping a negation.
 */
internal object EvaluationMetrics {
    data class Report(
        val referenceWords: Int,
        val substitutions: Int,
        val deletions: Int,
        val insertions: Int,
        val alignedWords: Int,
        val caseErrors: Int,
        val extraSentenceBreaks: Int,
        val missingSentenceBreaks: Int,
        val meaningWordsMissing: List<String>,
        val meaningWordsAdded: List<String>,
        val missingPhrases: List<String>,
    ) {
        val wordErrorRate: Double get() = if (referenceWords == 0) 0.0 else (substitutions + deletions + insertions).toDouble() / referenceWords
        val caseErrorRate: Double get() = if (alignedWords == 0) 0.0 else caseErrors.toDouble() / alignedWords
        fun json(): JSONObject = JSONObject()
            .put("reference_words", referenceWords).put("substitutions", substitutions).put("deletions", deletions)
            .put("insertions", insertions).put("word_error_rate", wordErrorRate)
            .put("aligned_words", alignedWords).put("case_errors", caseErrors).put("case_error_rate", caseErrorRate)
            .put("extra_sentence_breaks", extraSentenceBreaks).put("missing_sentence_breaks", missingSentenceBreaks)
            .put("meaning_words_missing", JSONArray(meaningWordsMissing)).put("meaning_words_added", JSONArray(meaningWordsAdded))
            .put("missing_phrases", JSONArray(missingPhrases))
    }

    private class Word(val surface: String, val key: String)
    private fun words(text: String) = Regex("\\S+").findAll(text).map { Word(it.value, PolishMerge.key(it.value)) }
        .filter { it.key.isNotEmpty() }.toList()
    private fun meaningBearing(key: String) = key in PolishMerge.protectedWords || key.any { it.isDigit() }
    private fun endsSentence(surface: String) = surface.trimEnd('"', '”', ')', '\'').lastOrNull() in setOf('.', '!', '?')
    private fun casePattern(surface: String) = surface.filter { it.isLetter() }.map { it.isUpperCase() }

    fun compare(reference: String, hypothesis: String): Report {
        val ref = words(reference); val hyp = words(hypothesis)
        val n = ref.size; val m = hyp.size
        // Edit distance with a backtrace; substitutions and gaps cost 1.
        val cost = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) cost[i][0] = i
        for (j in 0..m) cost[0][j] = j
        for (i in 1..n) for (j in 1..m) cost[i][j] = minOf(
            cost[i - 1][j - 1] + if (ref[i - 1].key == hyp[j - 1].key) 0 else 1, cost[i - 1][j] + 1, cost[i][j - 1] + 1)
        var i = n; var j = m
        var substitutions = 0; var deletions = 0; var insertions = 0; var aligned = 0; var caseErrors = 0
        var extraBreaks = 0; var missingBreaks = 0
        val deleted = BooleanArray(n)
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && ref[i - 1].key == hyp[j - 1].key && cost[i][j] == cost[i - 1][j - 1]) {
                aligned++
                if (casePattern(ref[i - 1].surface) != casePattern(hyp[j - 1].surface)) caseErrors++
                // The last word's terminal punctuation is not a break between phrases.
                if (i < n) {
                    val refBreak = endsSentence(ref[i - 1].surface); val hypBreak = endsSentence(hyp[j - 1].surface)
                    if (hypBreak && !refBreak) extraBreaks++
                    if (refBreak && !hypBreak) missingBreaks++
                }
                i--; j--
            } else if (i > 0 && j > 0 && cost[i][j] == cost[i - 1][j - 1] + 1) { substitutions++; i--; j-- }
            else if (i > 0 && cost[i][j] == cost[i - 1][j] + 1) { deletions++; deleted[i - 1] = true; i-- }
            else { insertions++; j-- }
        }
        fun counts(list: List<Word>) = list.map { it.key }.filter(::meaningBearing).groupingBy { it }.eachCount()
        val refMeaning = counts(ref); val hypMeaning = counts(hyp)
        fun difference(a: Map<String, Int>, b: Map<String, Int>) =
            a.flatMap { (key, count) -> List((count - (b[key] ?: 0)).coerceAtLeast(0)) { key } }.sorted()
        val phrases = mutableListOf<String>()
        var start = -1
        for (k in 0..n) {
            if (k < n && deleted[k]) { if (start < 0) start = k; continue }
            if (start >= 0 && k - start >= 3) phrases += ref.subList(start, k).joinToString(" ") { it.surface }
            start = -1
        }
        return Report(n, substitutions, deletions, insertions, aligned, caseErrors, extraBreaks, missingBreaks,
            difference(refMeaning, hypMeaning), difference(hypMeaning, refMeaning), phrases)
    }
}
