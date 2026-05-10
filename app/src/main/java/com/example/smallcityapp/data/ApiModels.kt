package com.example.smallcityapp.data

import com.google.gson.annotations.SerializedName

data class AlarmResponse(
    val alert: Boolean = false,
)

data class NewsItem(
    val id: Long,
    @SerializedName("tg_id")
    val telegramId: Long? = null,
    val title: String,
    val content: String,
    val date: String,
)

data class ExternalLinkItem(
    val id: Long,
    val title: String,
    val url: String,
    @SerializedName("is_active")
    val isActive: Boolean,
)

data class NotificationHistoryResponse(
    val messages: List<NotificationMessage> = emptyList(),
)

data class NotificationMessage(
    val id: Long,
    val content: String,
    @SerializedName("created_at")
    val createdAt: String,
)

data class OutageLookupRequest(
    val city: String,
    val street: String? = null,
    val building: String? = null,
)

data class OutageResponse(
    @SerializedName("address_id")
    val addressId: Int? = null,
    val city: String? = null,
    val street: String? = null,
    val building: String? = null,
    val queue: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    val periods: List<OutagePeriod?>? = emptyList(),
)

data class OutagePeriod(
    val from: String? = null,
    val to: String? = null,
    val duration: String? = null,
)

data class OutageErrorPayload(
    val message: String,
    val availableCities: List<String> = emptyList(),
    val availableStreets: List<String> = emptyList(),
    val availableBuildings: List<String> = emptyList(),
)

data class OutageLookupState(
    val response: OutageResponse? = null,
    val message: String? = null,
    val availableCities: List<String> = emptyList(),
    val availableStreets: List<String> = emptyList(),
    val availableBuildings: List<String> = emptyList(),
)

data class LocalPushMessage(
    val title: String,
    val body: String,
    val receivedAt: Long,
)
