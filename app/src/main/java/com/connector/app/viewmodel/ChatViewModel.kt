package com.connector.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.connector.app.data.api.LlmClient
import com.connector.app.data.model.*
import com.connector.app.data.repository.ChatRepository
import com.connector.app.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

data class ChatUiState(
    val chats: List<Chat> = emptyList(),
    val currentChat: Chat? = null,
    val settings: AppSettings = AppSettings(),
    val isGenerating: Boolean = false,
    val statusText: String = "",
    val error: String? = null,
    val drawerOpen: Boolean = false
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)
    private val chatRepo = ChatRepository(app)
    private val llm = LlmClient()

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var generateJob: Job? = null

    private val webToolPrompt = """
Ты имеешь доступ к интернету через инструменты. Когда нужна актуальная информация — используй их.

Формат вызова (строго):
<web_search>
query: текст запроса
</web_search>

или

<web_fetch>
url: https://example.com
</web_fetch>

После того как получишь результат инструмента — продолжи ответ пользователю.
Не выдумывай результаты поиска.
""".trimIndent()

    init {
        viewModelScope.launch {
            settingsRepo.settingsFlow.collect { s ->
                _state.update { it.copy(settings = s) }
            }
        }
        viewModelScope.launch {
            chatRepo.chatsFlow.collect { list ->
                _state.update { st ->
                    val current = st.currentChat?.let { c -> list.find { it.id == c.id } } ?: st.currentChat
                    st.copy(chats = list, currentChat = current)
                }
            }
        }
    }

    fun newChat() {
        val chat = Chat(title = "Новый чат")
        _state.update { it.copy(currentChat = chat, error = null) }
        viewModelScope.launch { chatRepo.upsert(chat) }
    }

    fun selectChat(chat: Chat) {
        _state.update { it.copy(currentChat = chat, error = null, drawerOpen = false) }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            chatRepo.delete(chatId)
            if (_state.value.currentChat?.id == chatId) {
                _state.update { it.copy(currentChat = null) }
            }
        }
    }

    fun toggleDrawer(open: Boolean? = null) {
        _state.update { it.copy(drawerOpen = open ?: !it.drawerOpen) }
    }

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch {
            settingsRepo.save(settings)
        }
    }

    fun stopGenerating() {
        generateJob?.cancel()
        generateJob = null
        _state.update { it.copy(isGenerating = false, statusText = "") }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val settings = _state.value.settings
        if (settings.apiKey.isBlank()) {
            _state.update { it.copy(error = "Добавьте API-ключ в настройках") }
            return
        }

        var chat = _state.value.currentChat ?: Chat(title = trimmed.take(40))
        val userMsg = ChatMessage(role = "user", content = trimmed)
        chat = chat.copy(
            messages = chat.messages + userMsg,
            title = if (chat.messages.isEmpty()) trimmed.take(40) else chat.title
        )

        _state.update {
            it.copy(
                currentChat = chat,
                isGenerating = true,
                statusText = "Думаю…",
                error = null
            )
        }

        viewModelScope.launch { chatRepo.upsert(chat) }

        generateJob = viewModelScope.launch {
            try {
                runGeneration(chat, settings)
            } catch (e: Exception) {
                val errMsg = e.message ?: "Ошибка"
                val updated = chat.copy(
                    messages = chat.messages + ChatMessage(
                        role = "assistant",
                        content = errMsg,
                        isError = true
                    )
                )
                _state.update {
                    it.copy(
                        currentChat = updated,
                        isGenerating = false,
                        statusText = "",
                        error = errMsg
                    )
                }
                chatRepo.upsert(updated)
            }
        }
    }

    private suspend fun runGeneration(chat: Chat, settings: AppSettings, depth: Int = 0) {
        if (depth > 4) {
            _state.update { it.copy(isGenerating = false, statusText = "") }
            return
        }

        val system = listOfNotNull(
            settings.systemPrompt.takeIf { it.isNotBlank() },
            webToolPrompt
        ).joinToString("\n\n")

        val history = chat.messages.filter { !it.isError }

        var assistantMsg = ChatMessage(role = "assistant", content = "")
        var workingChat = chat.copy(messages = chat.messages + assistantMsg)
        _state.update { it.copy(currentChat = workingChat, statusText = "Думаю…") }

        val sb = StringBuilder()

        try {
            llm.streamChat(settings, history, system)
                .collect { chunk ->
                    sb.append(chunk)
                    assistantMsg = assistantMsg.copy(content = sb.toString())
                    workingChat = workingChat.copy(
                        messages = workingChat.messages.dropLast(1) + assistantMsg
                    )
                    _state.update {
                        it.copy(
                            currentChat = workingChat,
                            statusText = ""
                        )
                    }
                }
        } catch (e: Exception) {
            if (sb.isEmpty()) {
                val full = llm.chatOnce(settings, history, system)
                sb.append(full)
                assistantMsg = assistantMsg.copy(content = full)
                workingChat = workingChat.copy(
                    messages = workingChat.messages.dropLast(1) + assistantMsg
                )
                _state.update { it.copy(currentChat = workingChat) }
            } else {
                throw e
            }
        }

        val toolResult = handleTools(sb.toString())
        if (toolResult != null) {
            val toolMsg = ChatMessage(
                role = "user",
                content = "[Результат инструмента]\n$toolResult"
            )
            workingChat = workingChat.copy(messages = workingChat.messages + toolMsg)
            _state.update {
                it.copy(
                    currentChat = workingChat,
                    statusText = "Обрабатываю результат…"
                )
            }
            chatRepo.upsert(workingChat)
            runGeneration(workingChat, settings, depth + 1)
            return
        }

        chatRepo.upsert(workingChat)
        _state.update { it.copy(isGenerating = false, statusText = "") }
    }

    private suspend fun handleTools(text: String): String? {
        val searchRegex = Regex(
            """<web_search>\s*query:\s*(.*?)\s*</web_search>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val fetchRegex = Regex(
            """<web_fetch>\s*url:\s*(.*?)\s*</web_fetch>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        searchRegex.find(text)?.groupValues?.getOrNull(1)?.trim()?.let { query ->
            _state.update { it.copy(statusText = "Ищу в DuckDuckGo…") }
            return searchDuckDuckGo(query)
        }

        fetchRegex.find(text)?.groupValues?.getOrNull(1)?.trim()?.let { url ->
            _state.update { it.copy(statusText = "Читаю страницу…") }
            return fetchPage(url)
        }

        return null
    }

    private suspend fun searchDuckDuckGo(query: String): String = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
            val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val request = okhttp3.Request.Builder().url(url).build()
            val body = okhttp3.OkHttpClient().newCall(request).execute().body?.string() ?: ""
            val element = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject

            buildString {
                element["AbstractText"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                    ?.let { append("Кратко: $it\n\n") }

                element["RelatedTopics"]?.jsonArray?.take(5)?.forEach { item ->
                    val obj = item.jsonObject
                    val text = obj["Text"]?.jsonPrimitive?.contentOrNull
                    val link = obj["FirstURL"]?.jsonPrimitive?.contentOrNull
                    if (!text.isNullOrBlank()) {
                        append("• $text")
                        if (!link.isNullOrBlank()) append(" ($link)")
                        append("\n")
                    }
                }

                if (isEmpty()) append("Ничего не найдено по запросу: $query")
            }
        } catch (e: Exception) {
            "Ошибка поиска: ${e.message}"
        }
    }

    private suspend fun fetchPage(url: String): String = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (compatible; ConnectorBot/1.0)")
                .build()
            val body = okhttp3.OkHttpClient().newCall(request).execute().body?.string() ?: ""
            body
                .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(6000)
                .ifBlank { "Не удалось извлечь текст со страницы." }
        } catch (e: Exception) {
            "Ошибка загрузки страницы: ${e.message}"
        }
    }
}
