package io.github.issisx.alienflow

import android.app.Application
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Actual OkHttp HTTP upgrades and frames on loopback; no paid provider requests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RealtimeHandshakeTest {
    private fun withServer(test: (MockWebServer, OpenAiClient, LinkedBlockingQueue<Request>) -> Unit) {
        val server = MockWebServer()
        val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        val requests = LinkedBlockingQueue<Request>()
        server.start()
        val api = OpenAiClient(WebSocket.Factory { request, listener ->
            requests.add(request)
            val local = server.url(request.url.encodedPath + "?" + request.url.encodedQuery)
            client.newWebSocket(request.newBuilder().url(local).build(), listener)
        })
        try { test(server, api, requests) }
        finally {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
            server.shutdown()
        }
    }
    private fun ack(model: String = RealtimeProtocol.MODEL, type: String = "transcription") =
        JSONObject().put("type", "session.updated").put("session",
            RealtimeProtocol.sessionConfig("en", "").put("type", type).apply {
                getJSONObject("audio").getJSONObject("input").getJSONObject("transcription").put("model", model)
            }).toString()

    @Test fun keyCheckUsesActualTranscriptionRouteAndWaitsForConfiguredSession() = withServer { server, api, requests ->
        val frames = LinkedBlockingQueue<String>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("{\"type\":\"session.created\"}")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                frames.add(text)
                webSocket.send(ack())
            }
        }))
        runBlocking { RealtimeHandshake.check(api, "test-placeholder-not-a-key", "en", "ScraperX", 5000) }
        val request = requests.poll(5, TimeUnit.SECONDS)!!
        assertEquals("api.openai.com", request.url.host)
        assertEquals("https", request.url.scheme)
        assertEquals("/v1/realtime", request.url.encodedPath)
        assertEquals(setOf("intent"), request.url.queryParameterNames)
        assertEquals("transcription", request.url.queryParameter("intent"))
        assertNull(request.url.queryParameter("model"))
        assertEquals("Bearer test-placeholder-not-a-key", request.header("Authorization"))
        val handshake = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/v1/realtime?intent=transcription", handshake.path)
        assertEquals("websocket", handshake.getHeader("Upgrade")?.lowercase())
        val sent = frames.poll(5, TimeUnit.SECONDS)!!
        assertEquals(JSONObject(RealtimeProtocol.sessionUpdate("en", "ScraperX")).toString(), JSONObject(sent).toString())
        assertNull("A connection check must never send audio", frames.poll(100, TimeUnit.MILLISECONDS))
    }

    @Test fun httpRejectionBeforeOnOpenIsReportedWithoutExposingBody() = withServer { server, api, _ ->
        server.enqueue(MockResponse().setResponseCode(401).setHeader("x-request-id", "req_test-401")
            .setBody("""{"error":{"code":"invalid_api_key","type":"invalid_request_error","message":"sk-secret PRIVATE WORDS"}}"""))
        val problem = runBlocking {
            try { RealtimeHandshake.check(api, "test-placeholder-not-a-key", "en", "", 5000); error("Expected rejection") }
            catch (e: LiveConnectionException) { e.problem }
        }
        assertEquals(401, problem.httpStatus)
        assertEquals("invalid_api_key", problem.code)
        assertEquals("req_test-401", problem.requestId)
        assertFalse(problem.diagnostic().contains("sk-secret"))
        assertFalse(problem.message().contains("PRIVATE WORDS"))
    }

    @Test fun sessionSetupErrorCannotPassTheKeyCheck() = withServer { server, api, _ ->
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                webSocket.send("""{"type":"error","error":{"code":"invalid_value","type":"invalid_request_error","param":"session.audio.input.transcription.languages[0]","message":"DO NOT COPY MY SPEECH"}}""")
            }
        }))
        val problem = runBlocking {
            try { RealtimeHandshake.check(api, "test-placeholder-not-a-key", "bad-lang", "", 5000); error("Expected rejection") }
            catch (e: LiveConnectionException) { e.problem }
        }
        assertEquals("invalid_value", problem.code)
        assertEquals("session.audio.input.transcription.languages[0]", problem.param)
        assertFalse(problem.diagnostic().contains("MY SPEECH"))
    }

    @Test fun wrongAcknowledgedModelIsRejected() = withServer { server, api, _ ->
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) { webSocket.send(ack("unrequested-model")) }
        }))
        val problem = runBlocking {
            try { RealtimeHandshake.check(api, "test-placeholder-not-a-key", "en", "", 5000); error("Expected rejection") }
            catch (e: LiveConnectionException) { e.problem }
        }
        assertEquals("wrong_transcription_model", problem.code)
    }

    @Test fun socketOpeningAloneDoesNotCountAsAcceptedSession() = withServer { server, api, _ ->
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) { webSocket.send("{\"type\":\"session.created\"}") }
        }))
        val problem = runBlocking {
            try { RealtimeHandshake.check(api, "test-placeholder-not-a-key", "en", "", 1000); error("Expected timeout") }
            catch (e: LiveConnectionException) { e.problem }
        }
        assertEquals("session_ack_timeout", problem.code)
    }

    @Test fun conversationAcknowledgementCannotStartTranscriptionCapture() {
        assertEquals("wrong_session_type",
            RealtimeHandshake.acknowledgementProblem(JSONObject(ack(type = "realtime")))!!.code)
        assertNull(RealtimeHandshake.acknowledgementProblem(JSONObject(ack())))
    }
}
