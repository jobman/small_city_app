package com.example.smallcityapp.data

import com.example.smallcityapp.config.ServerConfig
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
    ): Result<List<NotificationMessage>> = runCatching {
        api.getNotificationHistory(
            addressId = addressId,
            firebaseToken = firebaseToken,
        ).messages
    }

    suspend fun getOutages(request: OutageLookupRequest): Result<OutageResponse> {
        return runCatching {
            val state = lookupOutageState(request).getOrThrow()
            state.response ?: throw IllegalStateException(
                buildString {
                    append(state.message?.toFriendlyOutageMessage() ?: "Не вдалося отримати графік відключень")
                    val options = buildList {
                        addAll(state.availableCities)
                        addAll(state.availableStreets)
                        addAll(state.availableBuildings)
                    }
                    if (options.isNotEmpty()) {
                        append("\n")
                        append(options.joinToString())
                    }
                },
            )
        }
    }

    suspend fun getOutageCities(): Result<List<String>> {
        return runCatching {
            lookupOutageState(OutageLookupRequest(city = "")).getOrThrow().availableCities
        }
    }

    suspend fun lookupOutageState(request: OutageLookupRequest): Result<OutageLookupState> {
        return runCatching {
            val response = api.getOutages(request)
            if (response.isSuccessful) {
                OutageLookupState(
                    response = response.body()
                        ?.withFallbackAddress(request)
                        ?: error("Порожня відповідь сервера"),
                )
            } else {
                val payload = parseOutageError(response.errorBody()?.string())
                OutageLookupState(
                    message = payload.message.toFriendlyOutageMessage(),
                    availableCities = payload.availableCities,
                    availableStreets = payload.availableStreets,
                    availableBuildings = payload.availableBuildings,
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
            OutageErrorPayload(
                message = json.optString("error", "Не вдалося отримати графік відключень"),
                availableCities = json.optJSONArrayStrings("available_cities"),
                availableStreets = json.optJSONArrayStrings("available_streets"),
                availableBuildings = json.optJSONArrayStrings("available_buildings"),
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
                .baseUrl(ServerConfig.BASE_URL)
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

        private fun OutageResponse.withFallbackAddress(request: OutageLookupRequest): OutageResponse =
            copy(
                city = city?.takeIf { it.isNotBlank() } ?: request.city,
                street = street?.takeIf { it.isNotBlank() } ?: request.street,
                building = building?.takeIf { it.isNotBlank() } ?: request.building,
                periods = periods.orEmpty().filterNotNull(),
            )

        private fun String.toFriendlyOutageMessage(): String {
            val normalized = trim().lowercase()
            return if (
                normalized == "schedule not found for the selected address" ||
                normalized.contains("не підтримує графіка відключень")
            ) {
                "Для обраної адреси відсутній графік відключень"
            } else {
                this
            }
        }
    }
}
