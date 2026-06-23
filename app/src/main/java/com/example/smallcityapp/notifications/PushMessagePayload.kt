package com.example.smallcityapp.notifications

import android.content.Intent
import android.os.Bundle

object PushMessagePayload {
    const val EXTRA_TITLE = "com.example.smallcityapp.PUSH_TITLE"
    const val EXTRA_BODY = "com.example.smallcityapp.PUSH_BODY"
    const val EXTRA_RECEIVED_AT = "com.example.smallcityapp.PUSH_RECEIVED_AT"
    const val EXTRA_HANDLED = "com.example.smallcityapp.PUSH_HANDLED"

    private val TITLE_KEYS = listOf(
        "title",
        "notification_title",
        "gcm.n.title",
        "gcm.notification.title",
    )
    private val BODY_KEYS = listOf(
        "body",
        "message",
        "content",
        "text",
        "notification_body",
        "gcm.n.body",
        "gcm.notification.body",
    )

    fun titleFromNotification(title: String?): String? = title.visibleValueOrNull()

    fun titleFromData(data: Map<String, String>): String? = data.firstValue(TITLE_KEYS)

    fun bodyFromData(data: Map<String, String>): String? = data.firstValue(BODY_KEYS)

    fun titleFromIntent(intent: Intent): String? {
        return intent.getStringExtra(EXTRA_TITLE).visibleValueOrNull()
            ?: intent.extras?.firstValue(TITLE_KEYS)
    }

    fun bodyFromIntent(intent: Intent): String? {
        return intent.getStringExtra(EXTRA_BODY).visibleValueOrNull()
            ?: intent.extras?.firstValue(BODY_KEYS)
    }

    fun receivedAtFromIntent(intent: Intent): Long? {
        return intent.getLongExtra(EXTRA_RECEIVED_AT, 0L).takeIf { it > 0L }
    }

    private fun Map<String, String>.firstValue(keys: List<String>): String? {
        return keys.firstNotNullOfOrNull { key ->
            get(key).visibleValueOrNull()
        }
    }

    private fun Bundle.firstValue(keys: List<String>): String? {
        return keys.firstNotNullOfOrNull { key ->
            get(key)?.toString().visibleValueOrNull()
        }
    }

    private fun String?.visibleValueOrNull(): String? {
        return this
            ?.replace(INVISIBLE_CHARACTERS, "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private val INVISIBLE_CHARACTERS = Regex("[\\u200B\\u200C\\u200D\\uFEFF]")
}
