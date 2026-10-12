package io.github.issisx.alienflow

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/** Metadata only. Never retain server messages, supplied audio/text or credentials. */
data class LiveConnectionProblem private constructor(
    val httpStatus: Int?,
    val code: String?,
    val type: String?,
    val param: String?,
    val requestId: String?,
) {
    fun message(): String = buildString {
        append("[AF-N01] Live connection failed")
        httpStatus?.let { append(" (HTTP $it)") }
        code?.let { append(" · $it") }
        param?.let { append(" · $it") }
    }
    fun diagnostic(): String = "http=${httpStatus ?: "none"}; code=${code ?: "none"}; " +
        "type=${type ?: "none"}; param=${param ?: "none"}; request_id=${requestId ?: "none"}"

    companion object {
        private fun token(value: String?): String? = value?.takeIf {
            it != "null" && !it.startsWith("sk-", true) &&
                it.matches(Regex("[A-Za-z0-9_.\\[\\]:/-]{1,120}"))
        }
        fun event(value: JSONObject): LiveConnectionProblem {
            val error = value.optJSONObject("error")
            return LiveConnectionProblem(null, token(error?.optString("code")),
                token(error?.optString("type")), token(error?.optString("param")), null)
        }
        fun http(response: Response?): LiveConnectionProblem {
            val body = try { response?.peekBody(8192)?.string()?.let(::JSONObject) }
                catch (_: Exception) { null }
            val error = body?.optJSONObject("error")
            return LiveConnectionProblem(response?.code,
                token(error?.optString("code")) ?: if (response == null) "network_error" else null,
                token(error?.optString("type")), token(error?.optString("param")),
                token(response?.header("x-request-id")))
        }
        internal fun setup(code: String): LiveConnectionProblem =
            LiveConnectionProblem(null, token(code), null, null, null)
    }
}

class LiveConnectionException(val problem: LiveConnectionProblem) :
    IllegalStateException(problem.message())

object RealtimeHandshake {
    // Transcription is a connection intent, not a conversational model in the URL.
    // The actual transcription model stays in RealtimeProtocol.sessionConfig().
    const val ENDPOINT = "wss://api.openai.com/v1/realtime?intent=transcription"
    fun request(key: String): Request = Request.Builder().url(ENDPOINT)
        .header("Authorization", "Bearer $key").build()

    fun acknowledgementProblem(event: JSONObject): LiveConnectionProblem? {
        val session = event.optJSONObject("session")
        val model = session?.optJSONObject("audio")?.optJSONObject("input")
            ?.optJSONObject("transcription")?.optString("model")
        return when {
            session?.optString("type") != "transcription" -> LiveConnectionProblem.setup("wrong_session_type")
            model != RealtimeProtocol.MODEL -> LiveConnectionProblem.setup("wrong_transcription_model")
            else -> null
        }
    }

    /** Same URL, headers, session update and acknowledgement as dictation. No mic/audio. */
    suspend fun check(api: OpenAiClient, key: String, language: String, vocabulary: String,
                      timeoutMs: Long = 20_000L) {
        val done = CompletableDeferred<Unit>()
        val socket = api.realtimeWebSocket(key, object : WebSocketListener() {
            private fun fail(problem: LiveConnectionProblem) {
                done.completeExceptionally(LiveConnectionException(problem))
            }
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!webSocket.send(RealtimeProtocol.sessionUpdate(language, vocabulary))) {
                    fail(LiveConnectionProblem.setup("session_update_not_sent"))
                }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = try { JSONObject(text) } catch (_: Exception) {
                    fail(LiveConnectionProblem.setup("invalid_session_event")); return
                }
                when {
                    event.optString("type") == "error" -> fail(LiveConnectionProblem.event(event))
                    RealtimeProtocol.isSessionReadyEvent(event.optString("type")) -> {
                        val problem = acknowledgementProblem(event)
                        if (problem == null) done.complete(Unit) else fail(problem)
                    }
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                fail(LiveConnectionProblem.http(response))
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                fail(LiveConnectionProblem.setup("closed_before_session_ack"))
                webSocket.close(code, null)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                fail(LiveConnectionProblem.setup("closed_before_session_ack"))
            }
        })
        try {
            withTimeout(timeoutMs) { done.await() }
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive() // Preserve an enclosing caller cancellation.
            throw LiveConnectionException(LiveConnectionProblem.setup("session_ack_timeout"))
        } finally {
            done.cancel()
            socket.cancel()
        }
    }
}
