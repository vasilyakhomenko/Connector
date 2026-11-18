package com.connector.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String,               // "user" | "assistant" | "system"
    val content: String,
    val isError: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class Chat(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "Новый чат",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class AppSettings(
    val provider: String = "anthropic",
    val apiKey: String = "",
    val model: String = "claude-sonnet-4-5",
    val baseUrl: String = "",          // custom override
    val systemPrompt: String = "",
    val proxyUrl: String = ""          // kept for compatibility, not needed in native
)

enum class Provider(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val isAnthropic: Boolean = false
) {
    ANTHROPIC(
        "anthropic",
        "Anthropic (Claude)",
        "https://api.anthropic.com",
        "claude-sonnet-4-5",
        isAnthropic = true
    ),
    OPENAI(
        "openai",
        "OpenAI",
        "https://api.openai.com/v1",
        "gpt-4o"
    ),
    OPENROUTER(
        "openrouter",
        "OpenRouter",
        "https://openrouter.ai/api/v1",
        "anthropic/claude-sonnet-4.5"
    ),
    DEEPSEEK(
        "deepseek",
        "DeepSeek",
        "https://api.deepseek.com",
        "deepseek-chat"
    ),
    GROQ(
        "groq",
        "Groq",
        "https://api.groq.com/openai/v1",
        "llama-3.3-70b-versatile"
    ),
    XAI(
        "xai",
        "xAI (Grok)",
        "https://api.x.ai/v1",
        "grok-2-latest"
    ),
    CUSTOM(
        "custom",
        "Custom (OpenAI-compatible)",
        "",
        ""
    );

    companion object {
        fun fromId(id: String): Provider =
            entries.find { it.id == id } ?: ANTHROPIC
    }
}
