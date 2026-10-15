package io.github.issisx.alienflow

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SafeApiException(val httpCode: Int, message: String) : IllegalStateException(message)

class OpenAiClient internal constructor(private val socketFactory: WebSocket.Factory? = null,
                                       private val callFactory: okhttp3.Call.Factory? = null) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
    private val realtimeClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private fun execute(path: String, key: String, body: okhttp3.RequestBody): String {
        val request = Request.Builder()
            .url("https://api.openai.com/v1/$path")
            .header("Authorization", "Bearer $key")
            .post(body)
            .build()
        (callFactory ?: client).newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    401 -> "API key rejected"
                    403 -> "Account or model access denied"
                    429 -> "Account allowance or request limit reached"
                    else -> "OpenAI request failed"
                }
                // Never display the server's message/body: it can contain supplied text.
                fun safe(value: String?) = value?.takeIf {
                    it.matches(Regex("[A-Za-z0-9_.\\[\\]/:-]{1,120}"))
                }
                val error = try {
                    JSONObject(response.body?.string().orEmpty()).optJSONObject("error")
                } catch (_: Exception) { null }
                val details = listOfNotNull(
                    safe(error?.optString("code")),
                    safe(error?.optString("type")),
                    safe(error?.optString("param")),
                    safe(response.header("x-request-id")),
                )
                throw SafeApiException(
                    response.code,
                    "$message (HTTP ${response.code})${if (details.isEmpty()) "" else " · ${details.joinToString(" · ")}"}",
                )
            }
            return response.body?.string() ?: error("OpenAI returned an empty response")
        }
    }

    fun realtimeWebSocket(key: String, listener: WebSocketListener): WebSocket {
        return (socketFactory ?: realtimeClient).newWebSocket(RealtimeHandshake.request(key), listener)
    }

    internal suspend fun cleanup(key: String, source: String, mode: String, fillers: Boolean, context: WritingContext = WritingContext()): String {
        val body = CleanupRequest.body(source, mode, fillers, context)
        val response = JSONObject(
            executeCleanup(key, body.toString().toRequestBody("application/json".toMediaType())),
        )
        check(response.optString("status") == "completed") { "Cleanup response incomplete" }
        val output = response.getJSONArray("output")
        val text = StringBuilder()
        for (i in 0 until output.length()) {
            val content = output.getJSONObject(i).optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.getJSONObject(j)
                if (part.optString("type") == "output_text") text.append(part.optString("text"))
            }
        }
        return text.toString().trim()
    }

    /** Cancelling polishing must cancel its actual HTTP call, not leave a 45s IO worker. */
    private suspend fun executeCleanup(key: String, body: okhttp3.RequestBody): String = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url("https://api.openai.com/v1/responses")
            .header("Authorization", "Bearer $key").post(body).build()
        val call = (callFactory ?: client).newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(SafeApiException(0, "Cleanup connection unavailable"))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        if (!it.isSuccessful) throw SafeApiException(it.code, "Cleanup unavailable (HTTP ${it.code})")
                        it.body?.string() ?: error("Empty cleanup response")
                    }
                    continuation.resume(text)
                } catch (e: Exception) { continuation.resumeWithException(e) }
            }
        })
    }

    fun recover(key: String, audio: File, language: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "model",
                "gpt-transcribe",
            )
            .addFormDataPart(
                "file",
                "recording.wav",
                audio.asRequestBody("audio/wav".toMediaType()),
            )
        if (language.isNotBlank()) body.addFormDataPart("languages[]", language)
        return JSONObject(execute("audio/transcriptions", key, body.build())).getString("text")
    }
}
