package com.example.smallcityapp.notifications

import android.content.Context
import com.example.smallcityapp.data.LocalPushMessage
import org.json.JSONArray
import org.json.JSONObject

class LocalPushStore(context: Context) {
    private val preferences = context.getSharedPreferences("digital_town_pushes", Context.MODE_PRIVATE)

    fun getMessages(): List<LocalPushMessage> {
        val raw = preferences.getString(KEY_MESSAGES, null) ?: return emptyList()
        return runCatching {
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
    }

    fun saveMessage(message: LocalPushMessage) {
        val updated = buildList {
            add(message)
            addAll(getMessages())
        }.take(MAX_MESSAGES)

        val serialized = JSONArray().apply {
            updated.forEach { item ->
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

    companion object {
        private const val KEY_MESSAGES = "messages"
        private const val KEY_TOKEN = "firebase_token"
        private const val MAX_MESSAGES = 30
    }
}
