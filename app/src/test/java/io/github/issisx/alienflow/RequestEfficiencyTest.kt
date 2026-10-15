package io.github.issisx.alienflow

import android.app.Application
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** Request shape and loopback HTTP, not a paid provider evaluation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RequestEfficiencyTest {
    @Test fun vocabularyIsOptInAndTheSavedListSurvivesBeingDisabled() {
        val store = LocalStore(org.robolectric.RuntimeEnvironment.getApplication())
        store.prefs.edit().clear().putString("vocabulary", "ScraperX, AlienFlow").commit()
        assertEquals("", store.vocabularyForSession())
        store.prefs.edit().putBoolean("use_vocabulary", true).commit()
        assertEquals("ScraperX, AlienFlow", store.vocabularyForSession())
        store.prefs.edit().putBoolean("use_vocabulary", false).commit()
        assertEquals("", store.vocabularyForSession())
        assertEquals("ScraperX, AlienFlow", store.prefs.getString("vocabulary", null))
    }
    @Test fun vocabularyUsesDistinctLiteralKeywordsWithoutAnyPromptOrHistory() {
        val config = RealtimeProtocol.sessionConfig("en", " ScraperX, scraperx\nAlienFlow\r\nJolt Physics, <bad>, , \tinvalid\u0000 ")
            .getJSONObject("audio").getJSONObject("input").getJSONObject("transcription")
        assertFalse(config.has("prompt"))
        val terms = config.getJSONArray("keywords")
        assertEquals(listOf("ScraperX", "AlienFlow", "Jolt Physics"), (0 until terms.length()).map(terms::getString))
        assertFalse(RealtimeProtocol.sessionConfig("", "").getJSONObject("audio").getJSONObject("input")
            .getJSONObject("transcription").has("keywords"))
    }
    @Test fun cleanupUsesNoReasoningAndAProportionalMultilingualOutputAllowance() {
        for (mode in listOf("B", "C")) {
            val short = CleanupRequest.body("hello world", mode, false)
            assertEquals("gpt-6-luna", short.getString("model"))
            assertEquals("none", short.getJSONObject("reasoning").getString("effort"))
            assertFalse(short.getBoolean("store")); assertEquals("hello world", short.getString("input"))
            assertTrue(short.getInt("max_output_tokens") < 2048)
            assertTrue(CleanupRequest.body("字".repeat(30), mode, false).getInt("max_output_tokens") >=
                CleanupRequest.body("x".repeat(30), mode, false).getInt("max_output_tokens"))
            assertEquals(2048, CleanupRequest.body("word ".repeat(1000), mode, false).getInt("max_output_tokens"))
        }
        assertTrue(CleanupRequest.instructions("B", false).contains("Keep fillers"))
        assertTrue(CleanupRequest.instructions("C", true).contains("Remove um/uh"))
    }
    @Test fun repeatedChunksReuseOnlyAcceptedResultsInTheSameRecording() = runBlocking {
        val memo = CleanupMemo(); var requests = 0
        suspend fun clean() = memo.clean("hello world", "B", false) { requests++; "Hello world." }
        assertEquals("Hello world.", clean()); assertEquals("Hello world.", clean()); assertEquals(1, requests)
        assertNull(memo.find("hello world", "C", false)); assertNull(memo.find("hello world", "B", true))
        assertNull(CleanupMemo().find("hello world", "B", false))
        memo.remember("not $15", "$50", "B", false)
        assertNull(memo.find("not $15", "B", false))
    }
    @Test fun memoBoundsAndEvictionDoNotChangeTheTranscript() {
        val memo = CleanupMemo(maxEntries = 2, maxCharacters = 100)
        memo.remember("one", "One.", "B", false); memo.remember("two", "Two.", "B", false)
        assertEquals("One.", memo.find("one", "B", false))
        memo.remember("three", "Three.", "B", false)
        assertNull(memo.find("two", "B", false)); assertEquals("One.", memo.find("one", "B", false))
        memo.remember("word ".repeat(100), "word ".repeat(100), "B", false)
        assertNull(memo.find("word ".repeat(100), "B", false))
    }
    @Test fun actualCleanupHttpBodyIsCompactStatelessAndRetainsTheFullSource() = runBlocking {
        val server = MockWebServer(); val http = OkHttpClient(); val originals = mutableListOf<Request>()
        server.start()
        val api = OpenAiClient(callFactory = Call.Factory { request ->
            originals.add(request)
            http.newCall(request.newBuilder().url(server.url(request.url.encodedPath)).build())
        })
        val source = "I might not pay $15 for ScraperX."
        server.enqueue(MockResponse().setBody(JSONObject().put("status", "completed").put("output",
            org.json.JSONArray().put(JSONObject().put("content", org.json.JSONArray().put(
                JSONObject().put("type", "output_text").put("text", source))))).toString()))
        try {
            assertEquals(source, api.cleanup("fixture-not-a-key", source, "B", false))
            val sent = server.takeRequest(5, TimeUnit.SECONDS)!!
            val body = JSONObject(sent.body.readUtf8())
            assertEquals("/v1/responses", sent.path)
            assertEquals("api.openai.com", originals.single().url.host)
            assertEquals("Bearer fixture-not-a-key", sent.getHeader("Authorization"))
            assertEquals(source, body.getString("input")); assertFalse(body.has("previous_response_id"))
            assertEquals("none", body.getJSONObject("reasoning").getString("effort"))
            assertTrue(body.getString("instructions").contains("sentence case"))
            assertTrue(body.getString("instructions").contains("without copying context"))
        } finally {
            http.dispatcher.cancelAll(); http.connectionPool.evictAll(); http.dispatcher.executorService.shutdownNow(); server.shutdown()
        }
    }
}
