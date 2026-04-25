package com.example.smallcityapp.data

import com.example.smallcityapp.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DigitalTownRepository(
    private val api: DigitalTownApi = createApi(),
) {
    suspend fun getNews(): Result<List<NewsItem>> = runCatching {
        api.getNews().sortedByDescending { it.date }
    }

    suspend fun getLinks(): Result<List<ExternalLinkItem>> = runCatching {
        api.getLinks().filter { it.isActive }
    }

    suspend fun getAlarmState(): Result<Boolean> = runCatching {
        api.getAlarmState().alert
    }

    suspend fun getHistory(
        addressId: Int,
        firebaseToken: String,
        lastDate: String?,
    ): Result<List<NotificationMessage>> = runCatching {
        api.getNotificationHistory(
            addressId = addressId,
            firebaseToken = firebaseToken,
            lastDate = lastDate?.takeIf { it.isNotBlank() },
        ).messages
    }

    suspend fun getOutages(request: OutageLookupRequest): Result<OutageResponse> {
        return runCatching {
            val response = api.getOutages(request)
            if (response.isSuccessful) {
                response.body() ?: error("Порожня відповідь сервера")
            } else {
                val payload = parseOutageError(response.errorBody()?.string())
                throw IllegalStateException(
                    buildString {
                        append(payload.message)
                        if (payload.options.isNotEmpty()) {
                            append("\n")
                            append(payload.options.joinToString())
                        }
                    },
                )
            }
        }
    }

    private fun parseOutageError(rawBody: String?): OutageErrorPayload {
        if (rawBody.isNullOrBlank()) {
            return OutageErrorPayload(message = "Не вдалося отримати графік відключень")
        }

        return runCatching {
            val json = JSONObject(rawBody)
            val options = buildList {
                addAll(json.optJSONArrayStrings("available_cities"))
                addAll(json.optJSONArrayStrings("available_streets"))
                addAll(json.optJSONArrayStrings("available_buildings"))
            }.distinct()
            OutageErrorPayload(
                message = json.optString("error", "Не вдалося отримати графік відключень"),
                options = options,
            )
        }.getOrElse {
            OutageErrorPayload(message = rawBody)
        }
    }

    companion object {
        private fun createApi(): DigitalTownApi {
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logger)
                .build()

            return Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(DigitalTownApi::class.java)
        }

        private fun JSONObject.optJSONArrayStrings(name: String): List<String> {
            val jsonArray = optJSONArray(name) ?: return emptyList()
            return List(jsonArray.length()) { index ->
                jsonArray.optString(index)
            }.filter { it.isNotBlank() }
        }
    }
}
