package com.connector.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.connector.app.data.model.Chat
import com.connector.app.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.chatStore: DataStore<Preferences> by preferencesDataStore(name = "connector_chats")

class ChatRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val KEY = stringPreferencesKey("chats_json")

    val chatsFlow: Flow<List<Chat>> = context.chatStore.data.map { prefs ->
        val raw = prefs[KEY]
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString<List<Chat>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun saveAll(chats: List<Chat>) {
        context.chatStore.edit { prefs ->
            prefs[KEY] = json.encodeToString(chats)
        }
    }

    suspend fun upsert(chat: Chat) {
        context.chatStore.edit { prefs ->
            val current = prefs[KEY]?.let {
                runCatching { json.decodeFromString<List<Chat>>(it) }.getOrNull()
            } ?: emptyList()
            val updated = current.filter { it.id != chat.id } + chat.copy(updatedAt = System.currentTimeMillis())
            prefs[KEY] = json.encodeToString(updated.sortedByDescending { it.updatedAt })
        }
    }

    suspend fun delete(chatId: String) {
        context.chatStore.edit { prefs ->
            val current = prefs[KEY]?.let {
                runCatching { json.decodeFromString<List<Chat>>(it) }.getOrNull()
            } ?: emptyList()
            prefs[KEY] = json.encodeToString(current.filter { it.id != chatId })
        }
    }

    suspend fun clearAll() {
        context.chatStore.edit { it.clear() }
    }
}
