package io.github.issisx.alienflow

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PolishMergeTest {
    private fun merge(source: String, result: String, fillers: Boolean = false) = PolishMerge.merge(source, result, "B", fillers)

    @Test fun meaningWordsNumbersAndNamesRevertWhilePunctuationIsKept() {
        assertEquals("I might not pay $15 to Thet", merge("i might not pay $15 to Thet", "I will pay $50 to That."))
        assertEquals("I can now go.", merge("i can now go", "I can not go."))
        assertEquals("he left early.", merge("he left early", "She left early."))
        assertEquals("Hold the fold", merge("hold the fold", "Hold the hold."))
        assertEquals("We have twenty five", merge("we have twenty five", "We have 25."))
    }
    @Test fun proposedWordsAreNeverAddedAndRecognizedWordsAreNeverDropped() {
        assertEquals("call me tomorrow", merge("call me tomorrow", "Please call me tomorrow morning."))
        assertEquals("so um I think we should go.", merge("so um i think we should go", "I think we should go."))
        assertEquals("that works", merge("that works", "I think that works today"))
        assertEquals("I think that works", merge("I think that works", "that works"))
        assertEquals("that works.", merge("that works", "I think that works."))
    }
    @Test fun fillerAndExactRepeatRemovalStillNeedTheExplicitSetting() {
        assertEquals("so I think we should go.", merge("so um i think we should go", "I think we should go.", fillers = true))
        assertEquals("I think so.", merge("i i think so", "I think so.", fillers = true))
        assertEquals("I i think so.", merge("i i think so", "I think so."))
    }
    @Test fun boundedNearMissSpellingOfOrdinaryWordsOnly() {
        assertEquals("The meeting is at noon.", merge("the meating is at noon", "The meeting is at noon."))
        assertEquals("We can accommodate them.", merge("we can accomodate them", "We can accommodate them."))
        assertEquals("Certainty words never become reachable by spelling", "I definately agree.", merge("i definately agree", "I definitely agree."))
        assertEquals("i definately agree", PolishMerge.merge("i definately agree", "I definitely agree.", "A", false))
        assertEquals("Their plan works.", merge("thier plan works", "Their plan works."))
        assertEquals("I'll by the bread.", merge("i'll by the bread", "I'll buy the bread."))
        assertEquals("Meet Jon there.", merge("meet Jon there", "Meet John there."))
        assertEquals("Open .env now.", merge("open .env now", "Open env now."))
        assertEquals("I don't know.", merge("i dont know", "I don't know."))
        assertEquals("We met Paris.", merge("we met paris", "We met Paris."))
    }
    @Test fun rewriteModeKeepsItsWholeOutputContract() {
        assertEquals("i might not pay", PolishMerge.merge("i might not pay", "I will pay.", "C", false))
        assertEquals("I might not pay.", PolishMerge.merge("i might not pay", "I might not pay.", "C", false))
    }
    @Test fun partiallyUsableAnswersAreMemoizedInsteadOfRequestedAgain() = runBlocking {
        val memo = CleanupMemo(); var requests = 0
        repeat(2) {
            val output = memo.cleanWithContext("i bought apples", "B", false, WritingContext()) { _, _ -> requests++; "I purchased apples." }
            assertEquals("I bought apples.", output)
        }
        assertEquals(1, requests)
    }
}
