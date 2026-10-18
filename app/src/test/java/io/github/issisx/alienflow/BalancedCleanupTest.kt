package io.github.issisx.alienflow

import android.Manifest
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.WebSocket
import okio.Buffer
import okio.Timeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.time.Duration

/** Production start/preferences, coordinator, editor and HTTP adapter; speech and provider replies
 * are deterministic fixtures. No microphone, provider connection or billed-token measurement.
 * Actual socket cancellation remains covered by StopDeliveryTest and ApiSpendingSafetyTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = AlienApp::class, shadows = [EditorBridgeNodeRouteTest.Broker::class,
    EditorBridgeNodeRouteTest.NodeIpc::class, EditorBridgeNodeRouteTest.Ime::class,
    EditorBridgeNodeRouteTest.KeyboardIpc::class])
class BalancedCleanupTest {
    private class PendingCall(private val target: Request) : Call {
        val payload = Buffer().also { checkNotNull(target.body).writeTo(it) }.readByteArray()
        val body = JSONObject(payload.toString(Charsets.UTF_8))
        private val deadline = Timeout()
        private var callback: Callback? = null
        private var executed = false
        private var canceled = false
        private var answered = false

        override fun request() = target
        override fun execute(): Response = error("Cleanup fixture expects asynchronous HTTP")
        override fun enqueue(responseCallback: Callback) {
            check(!executed) { "Cleanup request was enqueued more than once" }
            executed = true
            callback = responseCallback
        }
        override fun cancel() { canceled = true }
        override fun isExecuted() = executed
        override fun isCanceled() = canceled
        override fun timeout() = deadline
        override fun clone(): Call = PendingCall(target)

        /** A single response may race cancellation, as it can at the real HTTP boundary. */
        fun reply(text: String) {
            check(executed && !answered) { "Cleanup fixture requires one response per enqueued request" }
            answered = true
            val json = JSONObject().put("status", "completed").put("output",
                org.json.JSONArray().put(JSONObject().put("content", org.json.JSONArray().put(
                    JSONObject().put("type", "output_text").put("text", text)))))
            checkNotNull(callback).onResponse(this, Response.Builder().request(target)
                .protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toString().toResponseBody("application/json".toMediaType())).build())
        }
    }

    private class Requests : Call.Factory {
        val calls = mutableListOf<PendingCall>()
        override fun newCall(request: Request): Call {
            assertEquals("https://api.openai.com/v1/responses", request.url.toString())
            return PendingCall(request).also { calls.add(it) }
        }
        fun counts() = JSONObject()
            .put("cleanup_requests", calls.size)
            .put("request_body_utf8_bytes", calls.sumOf { it.payload.size.toLong() })
            .put("serialized_input_utf8_bytes", calls.sumOf {
                it.body.getString("input").toByteArray(Charsets.UTF_8).size.toLong()
            })
    }

    private class Session(private val scope: TestScope, val coordinator: DictationCoordinator,
                          val requests: Requests) {
        private val phrases = mutableListOf<String>()
        val source: String get() = phrases.joinToString(" ")
        val fieldText: String get() = checkNotNull(EditorBridgeNodeRouteTest.field).text.toString()
        fun flushCurrentWork() {
            scope.runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
            scope.runCurrent()
        }
        fun speech(text: String) {
            val index = phrases.size
            phrases += text
            coordinator.event(JSONObject()
                .put("type", "conversation.item.input_audio_transcription.completed")
                .put("event_id", "final-$index").put("item_id", "item-$index")
                .put("previous_item_id", if (index == 0) "" else "item-${index - 1}")
                .put("transcript", text))
            flushCurrentWork()
        }
        fun stop() { coordinator.stop(); flushCurrentWork() }
        fun reply(text: String = source) { requests.calls.last().reply(text); flushCurrentWork() }
        fun drainCanceledDeadlines() {
            scope.advanceTimeBy(30_000)
            flushCurrentWork()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
            flushCurrentWork()
        }
    }

    private fun TestScope.withSession(savedLiveCleanup: Boolean? = null, mode: String? = null,
                                      block: (Session) -> Unit) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val work = SupervisorJob()
        val app = RuntimeEnvironment.getApplication() as AlienApp
        val previousCoordinator = app.coordinator
        val editors = EditorBridgeNodeRouteTest()
        Dispatchers.setMain(dispatcher)
        editors.reset()
        try {
            val preferences = app.store.prefs.edit().clear()
                .putBoolean("paid_api_enabled", true).putInt("retention", -1)
            if (savedLiveCleanup != null) preferences.putBoolean("live_cleanup", savedLiveCleanup)
            if (mode != null) preferences.putString("correction", mode)
            assertTrue(preferences.commit())
            val requests = Requests()
            val api = OpenAiClient(callFactory = requests,
                socketFactory = WebSocket.Factory { _, _ -> error("Cleanup fixture must not open a realtime socket") },
                allowed = { app.store.prefs.getBoolean("paid_api_enabled", false) })
            val coordinator = DictationCoordinator(app, app.store, api) { "fixture-not-a-key" }
            ReflectionHelpers.getField<CoroutineScope>(coordinator, "io").cancel()
            ReflectionHelpers.setField(coordinator, "io", CoroutineScope(work + dispatcher))
            ReflectionHelpers.setField(app, "coordinator", coordinator)
            shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
            // The existing empty-field fixture exercises the production accessibility writer
            // without real keyboard IPC or an IO worker that would require timing sleeps.
            editors.withField { service ->
                coordinator.service = service
                val session = Session(this, coordinator, requests)
                try {
                    coordinator.start()
                    session.flushCurrentWork()
                    assertTrue(coordinator.status, coordinator.active)
                    assertEquals(mode ?: "B", app.store.mode())
                    assertEquals(0, requests.calls.size)
                    assertEquals(savedLiveCleanup != null, app.store.prefs.contains("live_cleanup"))
                    if (savedLiveCleanup != null)
                        assertEquals(savedLiveCleanup, app.store.prefs.getBoolean("live_cleanup", !savedLiveCleanup))
                    block(session)
                } finally {
                    coordinator.cancel()
                    session.flushCurrentWork()
                }
            }
        } finally {
            work.cancel()
            runCurrent()
            editors.reset()
            ReflectionHelpers.setField(app, "coordinator", previousCoordinator)
            Dispatchers.resetMain()
        }
    }

    @Test fun defaultStreamsWordsWithoutCleanupAndPolishesOnceAfterStop() = runTest {
        withSession { session ->
            for (phrase in listOf("i might not", "pay $15", "for ScraperX")) {
                session.speech(phrase)
                assertEquals(session.source, session.fieldText)
                assertEquals("Live text must not require a cleanup request", 0, session.requests.calls.size)
            }
            session.stop()
            assertTrue(session.coordinator.finishing)
            assertEquals(1, session.requests.calls.size)
            assertEquals(session.source, session.requests.calls.single().body.getString("input"))
            session.reply("I might not pay $15 for ScraperX.")
            assertFalse(session.coordinator.active)
            assertFalse(session.coordinator.finishing)
            assertEquals("I might not pay $15 for ScraperX.", session.coordinator.result)
            assertEquals(session.coordinator.result, session.fieldText)
            session.drainCanceledDeadlines()
            assertEquals(1, session.requests.calls.size)
        }
    }

    @Test fun savedLiveOptInStillPolishesDuringSpeechWithoutAnotherFinalRequest() = runTest {
        withSession(savedLiveCleanup = true) { session ->
            session.speech("i think")
            assertEquals(1, session.requests.calls.size)
            session.reply("I think")
            assertEquals("I think", session.fieldText)
            session.speech("that works")
            assertEquals(2, session.requests.calls.size)
            session.reply("I think that works.")
            assertEquals("I think that works.", session.fieldText)
            session.stop()
            assertFalse(session.coordinator.finishing)
            assertEquals(2, session.requests.calls.size)
        }
    }

    @Test fun savedLiveOffRemainsOffAndStillReceivesFinalLightPolish() = runTest {
        withSession(savedLiveCleanup = false) { session ->
            session.speech("keep these words")
            assertEquals("keep these words", session.fieldText)
            assertEquals(0, session.requests.calls.size)
            session.stop()
            assertEquals(1, session.requests.calls.size)
            session.reply("Keep these words.")
            assertFalse(session.coordinator.finishing)
            assertEquals("Keep these words.", session.fieldText)
        }
    }

    @Test fun verbatimModeMakesNoCleanupRequestsEvenWithLiveOptIn() = runTest {
        withSession(savedLiveCleanup = true, mode = "A") { session ->
            session.speech("AlienFlow might not cost $15")
            session.speech("keep every fucking word")
            assertEquals(session.source, session.fieldText)
            session.stop()
            session.drainCanceledDeadlines()
            assertFalse(session.coordinator.finishing)
            assertEquals(session.source, session.coordinator.result)
            assertEquals(session.source, session.fieldText)
            assertEquals(0, session.requests.calls.size)
        }
    }

    private fun TestScope.cancelFinalCleanup(pause: Boolean) = withSession { session ->
        // More than one cleanup chunk exposes a cancellation bug that could start later requests.
        session.speech("please ".repeat(800) + "the last words")
        assertTrue("Cancellation fixture must contain at least two requestable chunks",
            CleanupChunks.split(session.source).count { it.isNotBlank() && it.length <= 3500 } >= 2)
        session.stop()
        assertTrue(session.coordinator.finishing)
        assertEquals(1, session.requests.calls.size)
        val pending = session.requests.calls.single()
        if (pause) session.coordinator.pausePaidApi() else session.coordinator.cancel()
        session.flushCurrentWork()
        assertTrue("Cancellation must reach the production HTTP call", pending.isCanceled())
        assertFalse(session.coordinator.active)
        assertFalse(session.coordinator.finishing)
        if (pause) {
            assertFalse(session.coordinator.paidApiEnabled)
            assertFalse((RuntimeEnvironment.getApplication() as AlienApp).store.prefs
                .getBoolean("paid_api_enabled", true))
            assertEquals(session.source, session.fieldText)
        }
        val retainedResult = session.coordinator.result
        val retainedField = session.fieldText
        pending.reply("A late response must not restart cleanup")
        session.flushCurrentWork()
        session.drainCanceledDeadlines()
        assertEquals(1, session.requests.calls.size)
        assertEquals(retainedResult, session.coordinator.result)
        assertEquals(retainedField, session.fieldText)
    }

    @Test fun cancelClosesFinalCleanupAndCannotLaunchLaterChunksOrApplyALateReply() = runTest {
        cancelFinalCleanup(pause = false)
    }

    @Test fun pauseClosesFinalCleanupAndCannotLaunchLaterChunksOrApplyALateReply() = runTest {
        cancelFinalCleanup(pause = true)
    }

    @Test fun finalCleanupDeadlineCancelsOnceAndPreservesRecognizedWords() = runTest {
        withSession { session ->
            session.speech("preserve this unfinished thought")
            session.stop()
            val pending = session.requests.calls.single()
            advanceTimeBy(2_499)
            session.flushCurrentWork()
            assertTrue(session.coordinator.finishing)
            assertFalse(pending.isCanceled())
            advanceTimeBy(1)
            session.flushCurrentWork()
            assertTrue(pending.isCanceled())
            assertFalse(session.coordinator.finishing)
            assertEquals(session.source, session.coordinator.result)
            assertEquals(session.source, session.fieldText)
            pending.reply("Preserve this unfinished thought.")
            session.flushCurrentWork()
            session.drainCanceledDeadlines()
            assertEquals(session.source, session.coordinator.result)
            assertEquals(session.source, session.fieldText)
            assertEquals(1, session.requests.calls.size)
        }
    }

    @Test fun fixtureComparisonRecordsActualRequestCountsAndPayloadBytes() = runTest {
        val phrases = listOf("i think", "the current version", "keeps every word", "without changing $15",
            "ScraperX or AlienFlow", "and it might not be ready")
        fun measure(live: Boolean?): JSONObject {
            var counts: JSONObject? = null
            withSession(savedLiveCleanup = live) { session ->
                for (phrase in phrases) {
                    session.speech(phrase)
                    if (live == true) session.reply()
                    else assertEquals(0, session.requests.calls.size)
                }
                session.stop()
                if (live != true) session.reply()
                assertFalse(session.coordinator.finishing)
                assertEquals(phrases.joinToString(" "), session.coordinator.result)
                assertEquals(session.coordinator.result, session.fieldText)
                counts = session.requests.counts()
            }
            return checkNotNull(counts)
        }
        val default = measure(null)
        val live = measure(true)
        assertEquals(1, default.getInt("cleanup_requests"))
        assertEquals(phrases.size, live.getInt("cleanup_requests"))
        assertTrue(default.getLong("request_body_utf8_bytes") < live.getLong("request_body_utf8_bytes"))
        val report = JSONObject()
            .put("evidence", "Scripted transcript events and controlled HTTP callbacks; no microphone or provider")
            .put("measurement", "Actual serialized request bodies, not billed tokens, dollars or transcription quality")
            .put("source_revision", BuildConfig.SOURCE_REVISION)
            .put("completed_phrase_count", phrases.size)
            .put("default_after_stop", default)
            .put("explicit_live_opt_in", live)
        val destination = File("build/reports/request-efficiency/balanced-cleanup-fixture.json")
        destination.parentFile.mkdirs()
        destination.writeText(report.toString(2) + "\n")
    }
}
