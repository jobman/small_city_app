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

enum class TownTab(val title: String) {
    Notifications("Сповіщ."),
    Outages("Графік"),
    News("Новини"),
    Links("Ще"),
}

data class MainUiState(
    val selectedTab: TownTab = TownTab.Notifications,
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

            _uiState.update { state ->
                state.copy(
                    isRefreshing = false,
                    alarmActive = alarmResult.getOrNull(),
                    alarmError = alarmResult.exceptionOrNull()?.localizedMessage,
                    news = newsResult.getOrDefault(emptyList()),
                    newsError = newsResult.exceptionOrNull()?.localizedMessage,
                    links = linksResult.getOrDefault(emptyList()),
                    linksError = linksResult.exceptionOrNull()?.localizedMessage,
                    firebaseToken = tokenResult.getOrNull().orEmpty(),
                    firebaseError = tokenResult.exceptionOrNull()?.localizedMessage,
                    localPushes = pushStore.getMessages(),
                )
            }
        }
    }

    fun refreshReceivedPushes() {
        _uiState.update { it.copy(localPushes = pushStore.getMessages()) }
    }

    fun refreshNotifications() {
        refreshReceivedPushes()
        refreshDashboard()
        uiState.value.outageResult?.addressId?.let { addressId ->
            viewModelScope.launch {
                loadHistoryForAddress(addressId = addressId, showLoading = true)
            }
        }
    }

    fun loadOutages() {
        val state = uiState.value
        if (state.outageCity.isBlank()) {
            _uiState.update { it.copy(outageError = "Поле міста є обов'язковим") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(outageLoading = true, outageError = null, outageResult = null) }
            val result = repository.getOutages(
                OutageLookupRequest(
                    city = state.outageCity.trim(),
                    street = state.outageStreet.trim().ifBlank { null },
                    building = state.outageBuilding.trim().ifBlank { null },
                ),
            )
            _uiState.update {
                val nextState = it.copy(
                    outageLoading = false,
                    outageResult = result.getOrNull(),
                    outageError = result.exceptionOrNull()?.localizedMessage,
                )
                saveOutageSelection(nextState)
                nextState
            }
            result.getOrNull()?.addressId?.let { addressId ->
                loadHistoryForAddress(addressId = addressId, showLoading = false)
            }
        }
    }

    fun loadOutageCityOptions() {
        if (uiState.value.outageCityOptionsLoading) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(outageCityOptionsLoading = true, outageError = null) }
            val result = repository.getOutageCities()
            _uiState.update {
                it.copy(
                    outageCityOptionsLoading = false,
                    outageCityOptions = result.getOrDefault(emptyList()),
                    outageError = result.exceptionOrNull()?.localizedMessage,
                )
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
                            outageError = error.localizedMessage,
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
                    historyError = tokenResult.exceptionOrNull()?.localizedMessage
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
            it.copy(
                historyLoading = false,
                historyMessages = result.getOrDefault(emptyList()),
                historyError = result.exceptionOrNull()?.localizedMessage,
                firebaseToken = token,
            )
        }
    }

    private fun loadSavedState(): MainUiState {
        val savedResult = prefs.getString(KEY_OUTAGE_RESULT, null)
            ?.let { rawResult ->
                runCatching { gson.fromJson(rawResult, OutageResponse::class.java) }.getOrNull()
            }

        return MainUiState(
            outageCity = prefs.getString(KEY_OUTAGE_CITY, "").orEmpty(),
            outageStreet = prefs.getString(KEY_OUTAGE_STREET, "").orEmpty(),
            outageBuilding = prefs.getString(KEY_OUTAGE_BUILDING, "").orEmpty(),
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
    }
}
