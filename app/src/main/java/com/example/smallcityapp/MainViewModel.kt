package com.example.smallcityapp

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smallcityapp.data.DigitalTownRepository
import com.example.smallcityapp.data.ExternalLinkItem
import com.example.smallcityapp.data.LocalPushMessage
import com.example.smallcityapp.data.NewsItem
import com.example.smallcityapp.data.NotificationMessage
import com.example.smallcityapp.data.OutageLookupRequest
import com.example.smallcityapp.data.OutageResponse
import com.example.smallcityapp.notifications.FirebaseTokenProvider
import com.example.smallcityapp.notifications.LocalPushStore
import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

enum class TownTab(val title: String) {
    Notifications("Сповіщ."),
    Outages("Графік"),
    News("Новини"),
    Links("Ще"),
    Profile("Профіль"),
}

enum class AppTheme {
    System,
    Light,
    Dark,
}

data class MainUiState(
    val selectedTab: TownTab = TownTab.Notifications,
    val appTheme: AppTheme = AppTheme.System,
    val isRefreshing: Boolean = false,
    val alarmActive: Boolean? = null,
    val alarmError: String? = null,
    val firebaseToken: String = "",
    val firebaseError: String? = null,
    val localPushes: List<LocalPushMessage> = emptyList(),
    val news: List<NewsItem> = emptyList(),
    val newsError: String? = null,
    val links: List<ExternalLinkItem> = emptyList(),
    val linksError: String? = null,
    val historyMessages: List<NotificationMessage> = emptyList(),
    val historyLoading: Boolean = false,
    val historyError: String? = null,
    val outageCityOptions: List<String> = emptyList(),
    val outageCityOptionsLoading: Boolean = false,
    val outageStreetOptions: List<String> = emptyList(),
    val outageBuildingOptions: List<String> = emptyList(),
    val outageCity: String = "",
    val outageStreet: String = "",
    val outageBuilding: String = "",
    val outageGuidance: String? = null,
    val outageLoading: Boolean = false,
    val outageResult: OutageResponse? = null,
    val outageError: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DigitalTownRepository()
    private val pushStore = LocalPushStore(application)
    private val tokenProvider = FirebaseTokenProvider(application)
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _uiState = MutableStateFlow(loadSavedState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        refreshDashboard()
    }

    fun selectTab(tab: TownTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun updateAppTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_APP_THEME, theme.name).apply()
        _uiState.update { it.copy(appTheme = theme) }
    }

    fun updateOutageCity(value: String) {
        _uiState.update {
            it.copy(
                outageCity = value,
                outageStreetOptions = emptyList(),
                outageBuildingOptions = emptyList(),
                outageStreet = "",
                outageBuilding = "",
                outageGuidance = null,
                historyMessages = emptyList(),
                historyError = null,
                outageResult = null,
                outageError = null,
            )
        }
        saveOutageSelection(_uiState.value)
        resolveOutageSelection()
    }

    fun updateOutageStreet(value: String) {
        _uiState.update {
            it.copy(
                outageStreet = value,
                outageBuildingOptions = emptyList(),
                outageBuilding = "",
                outageGuidance = null,
                historyMessages = emptyList(),
                historyError = null,
                outageResult = null,
                outageError = null,
            )
        }
        saveOutageSelection(_uiState.value)
        resolveOutageSelection()
    }

    fun updateOutageBuilding(value: String) {
        _uiState.update {
            it.copy(
                outageBuilding = value,
                outageGuidance = null,
                historyMessages = emptyList(),
                historyError = null,
                outageResult = null,
                outageError = null,
            )
        }
        saveOutageSelection(_uiState.value)
        resolveOutageSelection()
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, alarmError = null, newsError = null, linksError = null) }

            val alarmDeferred = async { repository.getAlarmState() }
            val newsDeferred = async { repository.getNews() }
            val linksDeferred = async { repository.getLinks() }
            val tokenDeferred = async { tokenProvider.getToken() }

            val results = awaitAll(alarmDeferred, newsDeferred, linksDeferred, tokenDeferred)

            @Suppress("UNCHECKED_CAST")
            val alarmResult = results[0] as Result<Boolean>
            @Suppress("UNCHECKED_CAST")
            val newsResult = results[1] as Result<List<NewsItem>>
            @Suppress("UNCHECKED_CAST")
            val linksResult = results[2] as Result<List<ExternalLinkItem>>
            @Suppress("UNCHECKED_CAST")
            val tokenResult = results[3] as Result<String>

            var reloadHistoryAfterPushExpiry: Int? = null
            _uiState.update { state ->
                val localPushResult = pushStore.getMessagesResult()
                if (localPushResult.removedExpired) {
                    reloadHistoryAfterPushExpiry = state.outageResult?.addressId
                }
                state.copy(
                    isRefreshing = false,
                    alarmActive = alarmResult.getOrNull() ?: state.alarmActive,
                    alarmError = alarmResult.exceptionOrNull()?.toUserMessage(),
                    news = newsResult.getOrNull() ?: state.news,
                    newsError = newsResult.exceptionOrNull()?.toUserMessage(),
                    links = linksResult.getOrNull() ?: state.links,
                    linksError = linksResult.exceptionOrNull()?.toUserMessage(),
                    firebaseToken = tokenResult.getOrNull() ?: state.firebaseToken,
                    firebaseError = tokenResult.exceptionOrNull()?.toUserMessage(),
                    localPushes = localPushResult.messages,
                    historyMessages = state.historyMessages.withoutActivePushDuplicates(localPushResult.messages),
                )
            }
            reloadHistoryAfterPushExpiry?.let { addressId ->
                loadHistoryForAddress(addressId = addressId, showLoading = false)
            }
        }
    }

    fun refreshReceivedPushes() {
        refreshReceivedPushes(reloadHistoryOnExpiry = true)
    }

    fun refreshNotifications() {
        refreshReceivedPushes(reloadHistoryOnExpiry = false)
        refreshDashboard()
        uiState.value.outageResult?.addressId?.let { addressId ->
            viewModelScope.launch {
                loadHistoryForAddress(addressId = addressId, showLoading = true)
            }
        }
    }

    private fun refreshReceivedPushes(reloadHistoryOnExpiry: Boolean) {
        val localPushResult = pushStore.getMessagesResult()
        var reloadHistoryAfterPushExpiry: Int? = null
        _uiState.update { state ->
            if (reloadHistoryOnExpiry && localPushResult.removedExpired) {
                reloadHistoryAfterPushExpiry = state.outageResult?.addressId
            }
            state.copy(
                localPushes = localPushResult.messages,
                historyMessages = state.historyMessages.withoutActivePushDuplicates(localPushResult.messages),
            )
        }
        reloadHistoryAfterPushExpiry?.let { addressId ->
            viewModelScope.launch {
                loadHistoryForAddress(addressId = addressId, showLoading = false)
            }
        }
    }

    fun refreshOutages() {
        resolveOutageSelection()
    }

    fun loadProfileAddressOptions() {
        if (uiState.value.outageCityOptionsLoading) {
            return
        }

        val savedSelection = uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(outageCityOptionsLoading = true, outageError = null) }

            val citiesResult = repository.getOutageCities()
            val cityLookupResult = savedSelection.outageCity
                .takeIf { it.isNotBlank() }
                ?.let { city ->
                    repository.lookupOutageState(OutageLookupRequest(city = city.trim()))
                }
            val streetLookupResult = savedSelection.outageStreet
                .takeIf { savedSelection.outageCity.isNotBlank() && it.isNotBlank() }
                ?.let { street ->
                    repository.lookupOutageState(
                        OutageLookupRequest(
                            city = savedSelection.outageCity.trim(),
                            street = street.trim(),
                        ),
                    )
                }

            val error = citiesResult.exceptionOrNull()
                ?: cityLookupResult?.exceptionOrNull()
                ?: streetLookupResult?.exceptionOrNull()

            var shouldResolveSavedSelection = false
            _uiState.update { current ->
                shouldResolveSavedSelection =
                    current.outageResult?.addressId == null &&
                    current.outageCity.isNotBlank() &&
                    current.outageCity == savedSelection.outageCity &&
                    current.outageStreet == savedSelection.outageStreet &&
                    current.outageBuilding == savedSelection.outageBuilding

                current.copy(
                    outageCityOptionsLoading = false,
                    outageCityOptions = citiesResult.getOrNull() ?: current.outageCityOptions,
                    outageStreetOptions = if (current.outageCity == savedSelection.outageCity) {
                        cityLookupResult
                            ?.getOrNull()
                            ?.availableStreets
                            ?.ifEmpty { current.outageStreetOptions }
                            ?: current.outageStreetOptions
                    } else {
                        current.outageStreetOptions
                    },
                    outageBuildingOptions = if (
                        current.outageCity == savedSelection.outageCity &&
                        current.outageStreet == savedSelection.outageStreet
                    ) {
                        streetLookupResult
                            ?.getOrNull()
                            ?.availableBuildings
                            ?.ifEmpty { current.outageBuildingOptions }
                            ?: current.outageBuildingOptions
                    } else {
                        current.outageBuildingOptions
                    },
                    outageError = error?.toUserMessage(),
                )
            }
            if (shouldResolveSavedSelection) {
                resolveOutageSelection()
            }
        }
    }

    private fun resolveOutageSelection() {
        val state = uiState.value
        if (state.outageCity.isBlank()) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(outageLoading = true, outageError = null, outageGuidance = null) }
            val result = repository.lookupOutageState(
                OutageLookupRequest(
                    city = state.outageCity.trim(),
                    street = state.outageStreet.trim().ifBlank { null },
                    building = state.outageBuilding.trim().ifBlank { null },
                ),
            )

            var selectedAddressId: Int? = null
            _uiState.update { current ->
                result.fold(
                    onSuccess = { lookup ->
                        val nextState = current.copy(
                            outageLoading = false,
                            outageResult = lookup.response,
                            outageGuidance = lookup.message,
                            outageStreetOptions = lookup.availableStreets.ifEmpty {
                                current.outageStreetOptions
                            },
                            outageBuildingOptions = lookup.availableBuildings.ifEmpty {
                                current.outageBuildingOptions
                            },
                            outageError = null,
                        )
                        saveOutageSelection(nextState)
                        selectedAddressId = nextState.outageResult?.addressId
                        nextState
                    },
                    onFailure = { error ->
                        current.copy(
                            outageLoading = false,
                            outageError = error.toUserMessage(),
                        )
                    },
                )
            }
            selectedAddressId?.let { addressId ->
                loadHistoryForAddress(addressId = addressId, showLoading = false)
            }
        }
    }

    private suspend fun loadHistoryForAddress(addressId: Int, showLoading: Boolean) {
        if (showLoading) {
            _uiState.update { it.copy(historyLoading = true, historyError = null) }
        }

        val tokenResult = if (uiState.value.firebaseToken.isBlank()) {
            tokenProvider.getToken()
        } else {
            Result.success(uiState.value.firebaseToken)
        }

        val token = tokenResult.getOrNull()
        if (token.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    historyLoading = false,
                    historyError = tokenResult.exceptionOrNull()?.toUserMessage()
                        ?: "Не вдалося отримати Firebase token",
                )
            }
            return
        }

        val result = repository.getHistory(
            addressId = addressId,
            firebaseToken = token,
        )

        _uiState.update {
            val localPushes = it.localPushes
            it.copy(
                historyLoading = false,
                historyMessages = result.getOrDefault(emptyList()).withoutActivePushDuplicates(localPushes),
                historyError = result.exceptionOrNull()?.toUserMessage(),
                firebaseToken = token,
            )
        }
    }

    private fun loadSavedState(): MainUiState {
        val savedResult = prefs.getString(KEY_OUTAGE_RESULT, null)
            ?.let { rawResult ->
                runCatching { gson.fromJson(rawResult, OutageResponse::class.java) }.getOrNull()
            }

        val appTheme = prefs.getString(KEY_APP_THEME, null)
            ?.let { savedTheme -> AppTheme.entries.firstOrNull { it.name == savedTheme } }
            ?: AppTheme.System

        return MainUiState(
            selectedTab = if (savedResult?.addressId == null) TownTab.Profile else TownTab.Notifications,
            appTheme = appTheme,
            outageCity = prefs.getString(KEY_OUTAGE_CITY, "").orEmpty()
                .ifBlank { savedResult?.city.orEmpty() },
            outageStreet = prefs.getString(KEY_OUTAGE_STREET, "").orEmpty()
                .ifBlank { savedResult?.street.orEmpty() },
            outageBuilding = prefs.getString(KEY_OUTAGE_BUILDING, "").orEmpty()
                .ifBlank { savedResult?.building.orEmpty() },
            outageResult = savedResult,
        )
    }

    private fun saveOutageSelection(state: MainUiState) {
        prefs.edit()
            .putString(KEY_OUTAGE_CITY, state.outageCity)
            .putString(KEY_OUTAGE_STREET, state.outageStreet)
            .putString(KEY_OUTAGE_BUILDING, state.outageBuilding)
            .apply {
                val result = state.outageResult
                if (result?.addressId != null) {
                    putString(KEY_OUTAGE_RESULT, gson.toJson(result))
                } else {
                    remove(KEY_OUTAGE_RESULT)
                }
            }
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "main_state"
        private const val KEY_OUTAGE_CITY = "outage_city"
        private const val KEY_OUTAGE_STREET = "outage_street"
        private const val KEY_OUTAGE_BUILDING = "outage_building"
        private const val KEY_OUTAGE_RESULT = "outage_result"
        private const val KEY_APP_THEME = "app_theme"
    }
}

private fun Throwable.toUserMessage(): String {
    val isNetworkError = generateSequence(this) { it.cause }.any { it is IOException }
    return if (isNetworkError) {
        "Немає з'єднання з інтернетом. Перевірте мережу та спробуйте ще раз."
    } else {
        "Не вдалося оновити дані. Спробуйте ще раз трохи пізніше."
    }
}

private fun List<NotificationMessage>.withoutActivePushDuplicates(
    localPushes: List<LocalPushMessage>,
): List<NotificationMessage> {
    val activePushBodies = localPushes
        .map { it.body.normalizedMessageText() }
        .filter { it.isNotBlank() }
        .toSet()

    if (activePushBodies.isEmpty()) {
        return this
    }

    return filterNot { message ->
        message.content.normalizedMessageText() in activePushBodies
    }
}

private fun String.normalizedMessageText(): String {
    return trim().replace(Regex("\\s+"), " ")
}
