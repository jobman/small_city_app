package com.example.smallcityapp.notifications

import android.content.Context
import android.content.SharedPreferences
import com.example.smallcityapp.data.LocalPushMessage
import org.json.JSONArray
import org.json.JSONObject

class LocalPushStore(context: Context) {
    private val preferences = context.getSharedPreferences("digital_town_pushes", Context.MODE_PRIVATE)

    fun getMessages(): List<LocalPushMessage> = getMessagesResult().messages

    fun getMessagesResult(): LocalPushMessagesResult {
        val raw = preferences.getString(KEY_MESSAGES, null)
            ?: return LocalPushMessagesResult(messages = emptyList(), removedExpired = false)
        val savedMessages = runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                LocalPushMessage(
                    title = item.optString("title"),
                    body = item.optString("body"),
                    receivedAt = item.optLong("receivedAt"),
                )
            }
        }.getOrDefault(emptyList())

        val freshMessages = savedMessages.filter { message ->
            message.receivedAt >= System.currentTimeMillis() - MESSAGE_TTL_MS
        }
        val removedExpired = freshMessages.size != savedMessages.size
        if (removedExpired) {
            saveMessages(freshMessages)
        }
        return LocalPushMessagesResult(
            messages = freshMessages,
            removedExpired = removedExpired,
        )
    }

    fun saveMessage(message: LocalPushMessage) {
        val updated = buildList {
            add(message)
            addAll(
                getMessages().filterNot { savedMessage ->
                    savedMessage.title == message.title && savedMessage.body == message.body
                },
            )
        }.take(MAX_MESSAGES)

        saveMessages(updated)
    }

    private fun saveMessages(messages: List<LocalPushMessage>) {
        val serialized = JSONArray().apply {
            messages.forEach { item ->
                put(
                    JSONObject()
                        .put("title", item.title)
                        .put("body", item.body)
                        .put("receivedAt", item.receivedAt),
                )
            }
        }

        preferences.edit()
            .putString(KEY_MESSAGES, serialized.toString())
            .apply()
    }

    fun saveToken(token: String) {
        preferences.edit()
            .putString(KEY_TOKEN, token)
            .apply()
    }

    fun getSavedToken(): String? = preferences.getString(KEY_TOKEN, null)

    fun registerMessagesChangeListener(
        onMessagesChanged: () -> Unit,
    ): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_MESSAGES) {
                onMessagesChanged()
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unregisterMessagesChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val KEY_MESSAGES = "messages"
        private const val KEY_TOKEN = "firebase_token"
        private const val MAX_MESSAGES = 30
        private const val MESSAGE_TTL_MS = 12 * 60 * 60 * 1_000L
    }
}

data class LocalPushMessagesResult(
    val messages: List<LocalPushMessage>,
    val removedExpired: Boolean,
)
