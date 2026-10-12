package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Test

class SpeechContinuationTest {
    @Test fun keyboardBeforeFirstSpeechDoesNotLoseTheFirstWords() {
        val source = SpeechContinuation(); source.protectDispatched()
        assertEquals("hello world", source.tail("hello world"))
    }
    @Test fun lateFinalCaseAndPunctuationCannotReinsertEditedSpeech() {
        val source = SpeechContinuation(); source.dispatched("hello world"); source.protectDispatched()
        assertEquals("", source.tail("Hello, world."))
        source.dispatched("Hello, world.")
        assertEquals(" next words", source.tail("Hello, world. next words"))
    }
    @Test fun oldRevisionAndNewSpeechAreSeparatedWithinTheSameTurn() {
        val source = SpeechContinuation(); source.dispatched("a blue car"); source.protectDispatched()
        assertEquals(" is coming", source.tail("a red truck is coming"))
    }
    @Test fun provisionalWordGrowthDoesNotOverwriteAnEditedWord() {
        val source = SpeechContinuation(); source.dispatched("hel"); source.protectDispatched()
        assertEquals("", source.tail("hello")); source.dispatched("hello")
        assertEquals(" there", source.tail("hello there"))
    }
    @Test fun multipleKeyboardEditsRetireOnlyAlreadyDispatchedSpeech() {
        val source = SpeechContinuation(); source.dispatched("one"); source.protectDispatched()
        assertEquals(" two", source.tail("one two")); source.dispatched("one two")
        source.protectDispatched()
        assertEquals(" three", source.tail("one two three"))
        assertEquals(" three", source.tail("one two three")) // Preview is not a dispatch.
    }
    @Test fun sourceRevisionsBeforeAnUntouchedSuffixRemapTheProtectedBoundary() {
        val source = SpeechContinuation(); source.dispatched("we go tomorrow"); source.protectDispatched()
        source.dispatched("we will go tomorrow")
        assertEquals(" now", source.tail("we will go tomorrow now"))
    }
    @Test fun resetStartsWithFullSpeechAgain() {
        val source = SpeechContinuation(); source.dispatched("old speech"); source.protectDispatched(); source.reset()
        assertEquals("new speech", source.tail("new speech"))
    }
}
