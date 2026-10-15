package io.github.issisx.alienflow

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Uses only the cleanup entry point, so scripts/prove_live_polish_regression.py can replay it on shipped source. */
class LightPolishRegressionTest {
    @Test fun oneUnauthorizedWordNoLongerDiscardsSentenceCaseAndPunctuationAcrossPauses() = runBlocking {
        val source = "I went to the store. And then. I bought seperate tickets. For Maria."
        val model = "I went to the store, and then I purchased separate tickets for Maria."
        assertFalse("The whole-output guard rejects this proposal", CorrectionGuard.accepts(source, model, "B"))
        val polished = cleanChunks(source, "B", false) { model }
        assertEquals("I went to the store, and then I bought separate tickets for Maria.", polished)
    }
}
