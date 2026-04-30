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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.rememberDatePickerState
import com.example.smallcityapp.data.ExternalLinkItem
import com.example.smallcityapp.data.LocalPushMessage
import com.example.smallcityapp.data.NewsItem
import com.example.smallcityapp.data.NotificationMessage
import com.example.smallcityapp.data.OutagePeriod
import com.example.smallcityapp.data.OutageResponse
import com.example.smallcityapp.ui.theme.SmallCityAPPTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmallCityAPPTheme {
                var showSplash by remember { mutableStateOf(true) }
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    delay(1_000)
                    showSplash = false
                }

                if (showSplash) {
                    BrandSplashScreen()
                    return@SmallCityAPPTheme
                }

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
                                    icon = {
                                        Text(
                                            text = tab.symbol(),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                        )
                                    },
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
                            onLastDateChange = viewModel::updateHistoryLastDate,
                            onLoadHistory = viewModel::loadHistory,
                        )

                        TownTab.Outages -> OutagesScreen(
                            modifier = Modifier.padding(innerPadding),
                            uiState = uiState,
                            onCityChange = viewModel::updateOutageCity,
                            onStreetChange = viewModel::updateOutageStreet,
                            onBuildingChange = viewModel::updateOutageBuilding,
                            onLoadCityOptions = viewModel::loadOutageCityOptions,
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
private fun BrandSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        BrandLogo(
            modifier = Modifier.size(180.dp),
            alpha = 1f,
        )
    }
}

@Composable
private fun BrandLogo(
    modifier: Modifier = Modifier,
    alpha: Float,
) {
    val logoRes = if (isSystemInDarkTheme()) {
        R.drawable.logo_dark_theme
    } else {
        R.drawable.logo_light_theme
    }
    Image(
        painter = painterResource(logoRes),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
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
                title = "Стан зараз",
                actionLabel = "Оновити",
                onAction = onRefresh,
                isLoading = uiState.isRefreshing,
            )
        }
        item {
            StatusCard(
                title = "Тривога",
                body = when (uiState.alarmActive) {
                    true -> "Зараз активна повітряна тривога. Будь ласка, подбай про безпеку."
                    false -> "Наразі активної повітряної тривоги немає."
                    null -> "Оновлюємо інформацію про тривогу."
                },
            )
        }
        item {
            SectionHeader(
                title = "Останні сповіщення",
                actionLabel = "Оновити",
                onAction = onRefreshPushes,
                isLoading = false,
            )
        }
        if (uiState.localPushes.isEmpty()) {
            item {
                EmptyCard("Коли з'являться нові міські сповіщення, вони будуть показані тут.")
            }
        } else {
            items(uiState.localPushes) { push ->
                PushMessageCard(push)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
    onLastDateChange: (String) -> Unit,
    onLoadHistory: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val initialSelectedDateMillis = remember(uiState.historyLastDate) {
        parseHistoryDateMillis(uiState.historyLastDate)
    }

    LaunchedEffect(uiState.outageResult?.addressId, uiState.historyLastDate) {
        if (uiState.outageResult?.addressId != null) {
            onLoadHistory()
        }
    }

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
        if (uiState.outageResult?.addressId == null) {
            item {
                EmptyCard("Спочатку оберіть адресу на екрані графіка відключень, а потім тут з'явиться історія повідомлень для неї.")
            }
        } else {
            item {
                StatusCard(
                    title = "Обрана адреса",
                    body = buildString {
                        append(listOfNotNull(uiState.outageResult.city, uiState.outageResult.street, uiState.outageResult.building).joinToString(", "))
                        append("\n")
                        append("address_id=${uiState.outageResult.addressId}")
                    },
                )
            }
        }
        item {
            OutlinedTextField(
                value = formatHistoryDateForDisplay(uiState.historyLastDate),
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Дата за бажанням") },
                supportingText = { Text("Якщо дату не обирати, буде завантажено повідомлення за останній місяць") },
                readOnly = true,
                singleLine = true,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Обрати дату")
                }
                OutlinedButton(
                    onClick = { onLastDateChange("") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Очистити")
                }
            }
        }
        if (uiState.historyLoading) {
            item {
                StatusCard(
                    title = "Оновлення",
                    body = "Завантажуємо повідомлення для обраної адреси.",
                )
            }
        }
        if (uiState.historyMessages.isEmpty()) {
            item {
                EmptyCard("Тут буде показана історія повідомлень для обраної адреси.")
            }
        } else {
            items(uiState.historyMessages) { message ->
                MessageCard(message)
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialSelectedDateMillis,
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            onLastDateChange(historyDateToIsoString(selectedMillis))
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("Готово")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDatePicker = false }) {
                    Text("Скасувати")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun OutagesScreen(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
    onCityChange: (String) -> Unit,
    onStreetChange: (String) -> Unit,
    onBuildingChange: (String) -> Unit,
    onLoadCityOptions: () -> Unit,
    onLoadOutages: () -> Unit,
) {
    var cityDropdownExpanded by remember { mutableStateOf(false) }
    var streetDropdownExpanded by remember { mutableStateOf(false) }
    var buildingDropdownExpanded by remember { mutableStateOf(false) }

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
            ExposedDropdownMenuBox(
                expanded = cityDropdownExpanded,
                onExpandedChange = { expanded ->
                    cityDropdownExpanded = expanded
                    if (expanded && uiState.outageCityOptions.isEmpty()) {
                        onLoadCityOptions()
                    }
                },
            ) {
                OutlinedTextField(
                    value = uiState.outageCity,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    label = { Text("Місто або село") },
                    placeholder = { Text("Обери місто зі списку") },
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = {
                        if (uiState.outageCityOptionsLoading) {
                            CircularProgressIndicator()
                        } else {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityDropdownExpanded)
                        }
                    },
                )
                DropdownMenu(
                    expanded = cityDropdownExpanded,
                    onDismissRequest = { cityDropdownExpanded = false },
                ) {
                    uiState.outageCityOptions.forEach { city ->
                        DropdownMenuItem(
                            text = { Text(city) },
                            onClick = {
                                onCityChange(city)
                                cityDropdownExpanded = false
                            },
                        )
                    }
                }
            }
        }
        if (uiState.outageStreetOptions.isNotEmpty()) {
            item {
            ExposedDropdownMenuBox(
                expanded = streetDropdownExpanded,
                onExpandedChange = { expanded ->
                    streetDropdownExpanded = expanded
                },
            ) {
                OutlinedTextField(
                    value = uiState.outageStreet,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    label = { Text("Вулиця, якщо потрібна") },
                    placeholder = { Text("Обери вулицю") },
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = streetDropdownExpanded)
                    },
                )
                DropdownMenu(
                    expanded = streetDropdownExpanded,
                    onDismissRequest = { streetDropdownExpanded = false },
                ) {
                    uiState.outageStreetOptions.forEach { street ->
                        DropdownMenuItem(
                            text = { Text(street) },
                            onClick = {
                                onStreetChange(street)
                                streetDropdownExpanded = false
                            },
                        )
                    }
                }
            }
        }
        }
        if (uiState.outageBuildingOptions.isNotEmpty()) {
            item {
            ExposedDropdownMenuBox(
                expanded = buildingDropdownExpanded,
                onExpandedChange = { expanded ->
                    buildingDropdownExpanded = expanded
                },
            ) {
                OutlinedTextField(
                    value = uiState.outageBuilding,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    label = { Text("Будинок, якщо потрібен") },
                    placeholder = { Text("Обери будинок") },
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = buildingDropdownExpanded)
                    },
                )
                DropdownMenu(
                    expanded = buildingDropdownExpanded,
                    onDismissRequest = { buildingDropdownExpanded = false },
                ) {
                    uiState.outageBuildingOptions.forEach { building ->
                        DropdownMenuItem(
                            text = { Text(building) },
                            onClick = {
                                onBuildingChange(building)
                                buildingDropdownExpanded = false
                            },
                        )
                    }
                }
            }
        }
        }
        uiState.outageGuidance?.let { guidance ->
            item {
                EmptyCard("Ще трохи: $guidance")
            }
        }
        item {
            StatusCard(
                title = "Статус адреси",
                body = if (uiState.outageResult?.addressId != null) {
                    "Адресу підтверджено. Тепер можна переглядати графік та історію повідомлень для неї."
                } else {
                    "Щоб побачити точний графік, оберіть адресу повністю."
                },
            )
        }
        if (uiState.outageResult?.addressId != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF1B8A3C),
                    )
                    Text(
                        text = "Адресу підтверджено",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        item {
            Button(
                onClick = onLoadOutages,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.outageLoading && uiState.outageResult?.addressId != null,
            ) {
                if (uiState.outageLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text("Показати графік")
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
                    EmptyCard("Для цієї адреси зараз немає доступного графіка відключень.")
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
                title = "Новини громади",
                actionLabel = "Оновити",
                onAction = onRefresh,
                isLoading = uiState.isRefreshing,
            )
        }
        if (uiState.news.isEmpty()) {
            item {
                EmptyCard("Поки що новин немає або вони ще завантажуються.")
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
                title = "Корисні посилання",
                actionLabel = "Оновити",
                onAction = onRefresh,
                isLoading = uiState.isRefreshing,
            )
        }
        if (uiState.links.isEmpty()) {
            item {
                EmptyCard("Поки що немає доступних посилань.")
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FilledTonalButton(
            onClick = onAction,
            enabled = !isLoading,
            modifier = Modifier.padding(start = 12.dp),
        ) {
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
                text = "Надіслано ${formatIsoDateTime(message.createdAt)}",
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
            Text("Тривалість відключення: ${period.duration}")
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
            Text("Відкрити посилання", color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun TownTab.symbol(): String = when (this) {
    TownTab.Notifications -> "\uD83D\uDD14"
    TownTab.History -> "\uD83D\uDCDC"
    TownTab.Outages -> "\u26A1"
    TownTab.News -> "\uD83D\uDCF0"
    TownTab.Links -> "\uD83D\uDD17"
}

private fun formatIsoDateTime(value: String): String {
    return runCatching {
        val instant = Instant.parse(value)
        DATE_TIME_FORMATTER.format(instant.atZone(ZoneId.systemDefault()))
    }.getOrDefault(value)
}

private fun formatHistoryDateForDisplay(value: String): String {
    if (value.isBlank()) {
        return ""
    }
    return runCatching {
        val instant = Instant.parse(value)
        HISTORY_DATE_FORMATTER.format(instant.atZone(ZoneId.systemDefault()).toLocalDate())
    }.getOrDefault(value)
}

private fun parseHistoryDateMillis(value: String): Long? {
    if (value.isBlank()) {
        return null
    }
    return runCatching {
        Instant.parse(value).toEpochMilli()
    }.getOrNull()
}

private fun historyDateToIsoString(selectedMillis: Long): String {
    return Instant.ofEpochMilli(selectedMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toString()
}

private val DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

private val HISTORY_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy")
