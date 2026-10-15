package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Test

class EvaluationMetricsTest {
    @Test fun wordCaseBreakAndMeaningErrorsAreCountedSeparately() {
        val report = EvaluationMetrics.compare("I don't think we should go. Call Maria at 5.", "I think. We should go call maria at 5")
        assertEquals(10, report.referenceWords)
        assertEquals(listOf(0, 1, 0), listOf(report.substitutions, report.deletions, report.insertions))
        assertEquals(0.1, report.wordErrorRate, 1e-9)
        assertEquals(9, report.alignedWords); assertEquals(3, report.caseErrors)
        assertEquals(1, report.extraSentenceBreaks); assertEquals(1, report.missingSentenceBreaks)
        assertEquals(listOf("don't"), report.meaningWordsMissing); assertEquals(emptyList<String>(), report.meaningWordsAdded)
        assertEquals(emptyList<String>(), report.missingPhrases)
    }
    @Test fun consecutiveDroppedWordsAreReportedAsAMissingPhrase() {
        val report = EvaluationMetrics.compare("please send the final report by friday", "Please send by Friday, $50")
        assertEquals(listOf("the final report"), report.missingPhrases)
        assertEquals(listOf("$50"), report.meaningWordsAdded)
        assertEquals(2, report.caseErrors)
        assertEquals(3, report.deletions); assertEquals(1, report.insertions)
    }
    @Test fun identicalTextHasNoErrors() {
        val report = EvaluationMetrics.compare("Hello there.", "Hello there.")
        assertEquals(0.0, report.wordErrorRate, 0.0); assertEquals(0, report.caseErrors)
        assertEquals(0.0, EvaluationMetrics.compare("", "").wordErrorRate, 0.0)
    }
}
