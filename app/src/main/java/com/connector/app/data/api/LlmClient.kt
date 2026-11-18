package com.connector.app.data.api

import com.connector.app.data.model.AppSettings
import com.connector.app.data.model.ChatMessage
import com.connector.app.data.model.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

class LlmClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun streamChat(
        settings: AppSettings,
        messages: List<ChatMessage>,
        systemPrompt: String
    ): Flow<String> = callbackFlow {
        val provider = Provider.fromId(settings.provider)
        val isAnthropic = provider.isAnthropic

        val base = settings.baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')
        val url = if (isAnthropic) {
            "$base/v1/messages"
        } else {
            "$base/chat/completions"
        }

        val bodyJson = buildRequestBody(settings, messages, systemPrompt, isAnthropic, stream = true)
        val requestBody = bodyJson.toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Content-Type", "application/json")

        if (isAnthropic) {
            requestBuilder
                .header("x-api-key", settings.apiKey)
                .header("anthropic-version", "2023-06-01")
        } else {
            requestBuilder.header("Authorization", "Bearer ${settings.apiKey}")
        }

        val request = requestBuilder.build()

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                if (data == "[DONE]") {
                    channel.close()
                    return
                }
                try {
                    val chunk = parseStreamChunk(data, isAnthropic)
                    if (chunk.isNotEmpty()) {
                        trySend(chunk)
                    }
                } catch (_: Exception) {
                }
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                val msg = buildString {
                    append(t?.message ?: "Network error")
                    if (response != null) {
                        append(" (HTTP ${response.code})")
                        try {
                            val errBody = response.body?.string()?.take(300)
                            if (!errBody.isNullOrBlank()) append("\n$errBody")
                        } catch (_: Exception) {}
                    }
                }
                channel.close(Exception(msg))
            }

            override fun onClosed(eventSource: EventSource) {
                channel.close()
            }
        }

        val eventSource = EventSources.createFactory(client)
            .newEventSource(request, listener)

        awaitClose {
            eventSource.cancel()
        }
    }

    suspend fun chatOnce(
        settings: AppSettings,
        messages: List<ChatMessage>,
        systemPrompt: String
    ): String = withContext(Dispatchers.IO) {
        val provider = Provider.fromId(settings.provider)
        val isAnthropic = provider.isAnthropic

        val base = settings.baseUrl.ifBlank { provider.defaultBaseUrl }.trimEnd('/')
        val url = if (isAnthropic) "$base/v1/messages" else "$base/chat/completions"

        val bodyJson = buildRequestBody(settings, messages, systemPrompt, isAnthropic, stream = false)
        val requestBody = bodyJson.toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Content-Type", "application/json")

        if (isAnthropic) {
            requestBuilder
                .header("x-api-key", settings.apiKey)
                .header("anthropic-version", "2023-06-01")
        } else {
            requestBuilder.header("Authorization", "Bearer ${settings.apiKey}")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${body.take(400)}")
            }
            parseFullResponse(body, isAnthropic)
        }
    }

    private fun buildRequestBody(
        settings: AppSettings,
        messages: List<ChatMessage>,
        systemPrompt: String,
        isAnthropic: Boolean,
        stream: Boolean
    ): String {
        return if (isAnthropic) {
            buildJsonObject {
                put("model", settings.model)
                put("max_tokens", 8192)
                put("stream", stream)
                if (systemPrompt.isNotBlank()) put("system", systemPrompt)
                putJsonArray("messages") {
                    messages
                        .filter { it.role == "user" || it.role == "assistant" }
                        .forEach { msg ->
                            addJsonObject {
                                put("role", msg.role)
                                put("content", msg.content)
                            }
                        }
                }
            }.toString()
        } else {
            buildJsonObject {
                put("model", settings.model)
                put("stream", stream)
                putJsonArray("messages") {
                    if (systemPrompt.isNotBlank()) {
                        addJsonObject {
                            put("role", "system")
                            put("content", systemPrompt)
                        }
                    }
                    messages
                        .filter { it.role == "user" || it.role == "assistant" }
                        .forEach { msg ->
                            addJsonObject {
                                put("role", msg.role)
                                put("content", msg.content)
                            }
                        }
                }
            }.toString()
        }
    }

    private fun parseStreamChunk(data: String, isAnthropic: Boolean): String {
        val element = json.parseToJsonElement(data).jsonObject
        return if (isAnthropic) {
            if (element["type"]?.jsonPrimitive?.content == "content_block_delta") {
                element["delta"]?.jsonObject
                    ?.get("text")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: ""
            } else ""
        } else {
            element["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("delta")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.contentOrNull
                ?: ""
        }
    }

    private fun parseFullResponse(body: String, isAnthropic: Boolean): String {
        val element = json.parseToJsonElement(body).jsonObject
        return if (isAnthropic) {
            element["content"]
                ?.jsonArray
                ?.joinToString("") {
                    it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: ""
                }
                ?: ""
        } else {
            element["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.contentOrNull
                ?: ""
        }
    }
}
