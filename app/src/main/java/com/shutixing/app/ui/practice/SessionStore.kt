package com.shutixing.app.ui.practice

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

@Serializable
data class SavedSession(
    val date: String,
    val mode: String,
    val queue: List<String>,
    val retestQueue: List<String> = emptyList(),
    val index: Int = 0,
    val inRetest: Boolean = false,
    val answeredMain: Int = 0,
    val totalAnswered: Int = 0,
    val correctCount: Int = 0,
)

class SessionStore(private val context: Context) {
    private val key = stringPreferencesKey("session_json")
    private val json = Json { ignoreUnknownKeys = true }

    val session: Flow<SavedSession?> = context.sessionDataStore.data.map { prefs ->
        prefs[key]?.let { text ->
            runCatching { json.decodeFromString<SavedSession>(text) }.getOrNull()
        }
    }

    suspend fun save(session: SavedSession) {
        context.sessionDataStore.edit { it[key] = json.encodeToString(session) }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.remove(key) }
    }
}
