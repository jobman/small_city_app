package com.example.smallcityapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.smallcityapp.data.ExternalLinkItem
import com.example.smallcityapp.data.LocalPushMessage
import com.example.smallcityapp.data.NewsItem
import com.example.smallcityapp.data.NotificationMessage
import com.example.smallcityapp.data.OutagePeriod
import com.example.smallcityapp.data.OutageResponse
import com.example.smallcityapp.ui.theme.SmallCityAPPTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmallCityAPPTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                RequestNotificationPermission()

                LaunchedEffect(uiState.firebaseError, uiState.historyError, uiState.outageError) {
                    val message = uiState.firebaseError ?: uiState.historyError ?: uiState.outageError
                    if (!message.isNullOrBlank()) {
                        snackbarHostState.showSnackbar(message)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        NavigationBar {
                            TownTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = uiState.selectedTab == tab,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = { Text(tab.title.take(1)) },
                                    label = { Text(tab.title) },
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    when (uiState.selectedTab) {
                        TownTab.Notifications -> NotificationsScreen(
                            modifier = Modifier.padding(innerPadding),
                            uiState = uiState,
                            onRefresh = viewModel::refreshDashboard,
                            onRefreshPushes = viewModel::refreshReceivedPushes,
                        )

                        TownTab.History -> HistoryScreen(
                            modifier = Modifier.padding(innerPadding),
                            uiState = uiState,
                            onAddressIdChange = viewModel::updateHistoryAddressId,
                            onLastDateChange = viewModel::updateHistoryLastDate,
                            onLoadHistory = viewModel::loadHistory,
                        )

                        TownTab.Outages -> OutagesScreen(
                            modifier = Modifier.padding(innerPadding),
                            uiState = uiState,
                            onCityChange = viewModel::updateOutageCity,
                            onStreetChange = viewModel::updateOutageStreet,
                            onBuildingChange = viewModel::updateOutageBuilding,
                            onLoadOutages = viewModel::loadOutages,
                        )

                        TownTab.News -> NewsScreen(
                            modifier = Modifier.padding(innerPadding),
                            uiState = uiState,
                            onRefresh = viewModel::refreshDashboard,
                        )

                        TownTab.Links -> LinksScreen(
                            modifier = Modifier.padding(innerPadding),
                            uiState = uiState,
                            onRefresh = viewModel::refreshDashboard,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun NotificationsScreen(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
    onRefresh: () -> Unit,
    onRefreshPushes: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = "Поточний стан",
                actionLabel = "Оновити",
                onAction = onRefresh,
                isLoading = uiState.isRefreshing,
            )
        }
        item {
            StatusCard(
                title = "Тривога",
                body = when (uiState.alarmActive) {
                    true -> "Зараз активна повітряна тривога."
                    false -> "Активної тривоги немає."
                    null -> "Стан ще не завантажено."
                },
            )
        }
        item {
            StatusCard(
                title = "Firebase token",
                body = if (uiState.firebaseToken.isBlank()) {
                    "Токен ще недоступний. Це нормально, якщо Firebase ще не підключений."
                } else {
                    uiState.firebaseToken
                },
            )
        }
        item {
            SectionHeader(
                title = "Отримані push-повідомлення",
                actionLabel = "Оновити",
                onAction = onRefreshPushes,
                isLoading = false,
            )
        }
        if (uiState.localPushes.isEmpty()) {
            item {
                EmptyCard("Тут з'являться повідомлення, які прийде через Firebase Cloud Messaging.")
            }
        } else {
            items(uiState.localPushes) { push ->
                PushMessageCard(push)
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
    onAddressIdChange: (String) -> Unit,
    onLastDateChange: (String) -> Unit,
    onLoadHistory: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Історія повідомлень",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        item {
            Text(
                text = "Сервер вимагає address_id. У поточному описі API немає публічного endpoint-а для пошуку цього ID, тому тут поки ручний ввід.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            OutlinedTextField(
                value = uiState.historyAddressId,
                onValueChange = onAddressIdChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("address_id") },
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = uiState.historyLastDate,
                onValueChange = onLastDateChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("last_date, optional") },
                supportingText = { Text("Наприклад: 2026-04-16T10:00:00Z") },
                singleLine = true,
            )
        }
        item {
            Button(
                onClick = onLoadHistory,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.historyLoading,
            ) {
                if (uiState.historyLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text("Завантажити історію")
            }
        }
        if (uiState.historyMessages.isEmpty()) {
            item {
                EmptyCard("Після запиту тут з'явиться історія сповіщень для вибраної адреси.")
            }
        } else {
            items(uiState.historyMessages) { message ->
                MessageCard(message)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutagesScreen(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
    onCityChange: (String) -> Unit,
    onStreetChange: (String) -> Unit,
    onBuildingChange: (String) -> Unit,
    onLoadOutages: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Графік відключень",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        item {
            OutlinedTextField(
                value = uiState.outageCity,
                onValueChange = onCityChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Місто або село") },
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = uiState.outageStreet,
                onValueChange = onStreetChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Вулиця, якщо потрібна") },
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = uiState.outageBuilding,
                onValueChange = onBuildingChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Будинок, якщо потрібен") },
                singleLine = true,
            )
        }
        item {
            Button(
                onClick = onLoadOutages,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.outageLoading,
            ) {
                if (uiState.outageLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text("Перевірити графік")
            }
        }
        uiState.outageResult?.let { result ->
            item {
                StatusCard(
                    title = "Адреса",
                    body = listOfNotNull(result.city, result.street, result.building).joinToString(", "),
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.queue?.let {
                        AssistChip(onClick = {}, label = { Text("Черга: $it") })
                    }
                    result.updatedAt?.let {
                        AssistChip(onClick = {}, label = { Text("Оновлено: ${formatIsoDateTime(it)}") })
                    }
                }
            }
            if (result.periods.isEmpty()) {
                item {
                    EmptyCard("Для цієї адреси сервер не повернув часових проміжків.")
                }
            } else {
                items(result.periods) { period ->
                    OutagePeriodCard(period)
                }
            }
        }
    }
}

@Composable
private fun NewsScreen(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = "Стрічка новин",
                actionLabel = "Оновити",
                onAction = onRefresh,
                isLoading = uiState.isRefreshing,
            )
        }
        if (uiState.news.isEmpty()) {
            item {
                EmptyCard("Новини ще не завантажені або сервер повернув порожній список.")
            }
        } else {
            items(uiState.news) { news ->
                NewsCard(news)
            }
        }
    }
}

@Composable
private fun LinksScreen(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
    onRefresh: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = "Додаткові ресурси",
                actionLabel = "Оновити",
                onAction = onRefresh,
                isLoading = uiState.isRefreshing,
            )
        }
        if (uiState.links.isEmpty()) {
            item {
                EmptyCard("Активні ресурси поки відсутні.")
            }
        } else {
            items(uiState.links) { link ->
                LinkCard(link = link, onOpen = { uriHandler.openUri(link.url) })
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    isLoading: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
        )
        FilledTonalButton(onClick = onAction, enabled = !isLoading) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body)
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun PushMessageCard(message: LocalPushMessage) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(message.title, fontWeight = FontWeight.SemiBold)
            Text(message.body)
            Text(
                text = DateUtils.getRelativeTimeSpanString(message.receivedAt).toString(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MessageCard(message: NotificationMessage) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(message.content)
            Text(
                text = formatIsoDateTime(message.createdAt),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun OutagePeriodCard(period: OutagePeriod) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("З ${formatIsoDateTime(period.from)} до ${formatIsoDateTime(period.to)}")
            Text("Тривалість: ${period.duration}")
        }
    }
}

@Composable
private fun NewsCard(news: NewsItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(news.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = news.content,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatIsoDateTime(news.date),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LinkCard(
    link: ExternalLinkItem,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(link.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(link.url, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun formatIsoDateTime(value: String): String {
    return runCatching {
        val instant = Instant.parse(value)
        DATE_TIME_FORMATTER.format(instant.atZone(ZoneId.systemDefault()))
    }.getOrDefault(value)
}

private val DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
