package ua.gov.trostyanets.digital.data

import ua.gov.trostyanets.digital.config.ServerConfig
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
                    append(state.message?.toFriendlyOutageMessage() ?: "РќРµ РІРґР°Р»РѕСЃСЏ РѕС‚СЂРёРјР°С‚Рё РіСЂР°С„С–Рє РІС–РґРєР»СЋС‡РµРЅСЊ")
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
                        ?: error("РџРѕСЂРѕР¶РЅСЏ РІС–РґРїРѕРІС–РґСЊ СЃРµСЂРІРµСЂР°"),
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
            return OutageErrorPayload(message = "РќРµ РІРґР°Р»РѕСЃСЏ РѕС‚СЂРёРјР°С‚Рё РіСЂР°С„С–Рє РІС–РґРєР»СЋС‡РµРЅСЊ")
        }

        return runCatching {
            val json = JSONObject(rawBody)
            OutageErrorPayload(
                message = json.optString("error", "РќРµ РІРґР°Р»РѕСЃСЏ РѕС‚СЂРёРјР°С‚Рё РіСЂР°С„С–Рє РІС–РґРєР»СЋС‡РµРЅСЊ"),
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
                message = message?.toFriendlyOutageMessage(),
            )

        private fun String.toFriendlyOutageMessage(): String {
            val normalized = trim().lowercase()
            return if (
                normalized == "schedule not found for the selected address" ||
                normalized.contains("РЅРµ РїС–РґС‚СЂРёРјСѓС” РіСЂР°С„С–РєР° РІС–РґРєР»СЋС‡РµРЅСЊ")
            ) {
                "Р”Р»СЏ РѕР±СЂР°РЅРѕС— Р°РґСЂРµСЃРё РІС–РґСЃСѓС‚РЅС–Р№ РіСЂР°С„С–Рє РІС–РґРєР»СЋС‡РµРЅСЊ"
            } else {
                this
            }
        }
    }
}
