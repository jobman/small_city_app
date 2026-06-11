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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.example.smallcityapp.data.ExternalLinkItem
import com.example.smallcityapp.data.LocalPushMessage
import com.example.smallcityapp.data.NewsItem
import com.example.smallcityapp.data.NotificationMessage
import com.example.smallcityapp.data.OutagePeriod
import com.example.smallcityapp.data.OutageResponse
import com.example.smallcityapp.ui.theme.SmallCityAPPTheme
import com.example.smallcityapp.ui.theme.SportGreen
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (uiState.appTheme) {
                AppTheme.System -> systemDarkTheme
                AppTheme.Light -> false
                AppTheme.Dark -> true
            }

            SmallCityAPPTheme(darkTheme = darkTheme) {
                var showSplash by remember { mutableStateOf(true) }
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    delay(1_000)
                    showSplash = false
                }

                if (showSplash) {
                    BrandSplashScreen(darkTheme = darkTheme)
                    return@SmallCityAPPTheme
                }

                RequestNotificationPermission()

                LaunchedEffect(
                    uiState.firebaseError,
                    uiState.historyError,
                    uiState.outageError,
                    uiState.alarmError,
                    uiState.newsError,
                    uiState.linksError,
                ) {
                    val message = uiState.firebaseError
                        ?: uiState.historyError
                        ?: uiState.outageError
                        ?: uiState.alarmError
                        ?: uiState.newsError
                        ?: uiState.linksError
                    if (!message.isNullOrBlank()) {
                        snackbarHostState.showSnackbar(message)
                    }
                }

                LaunchedEffect(uiState.selectedTab) {
                    while (true) {
                        when (uiState.selectedTab) {
                            TownTab.Notifications -> viewModel.refreshNotifications()
                            TownTab.News,
                            TownTab.Links -> viewModel.refreshDashboard()
                            TownTab.Outages -> viewModel.refreshOutages()
                            TownTab.Profile -> viewModel.loadProfileAddressOptions()
                        }
                        if (uiState.selectedTab == TownTab.Outages || uiState.selectedTab == TownTab.Profile) {
                            break
                        }
                        delay(NOTIFICATIONS_REFRESH_INTERVAL_MS)
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
                        )

                        TownTab.Outages -> OutagesScreen(
                            modifier = Modifier.padding(innerPadding),
                            uiState = uiState,
                        )

                        TownTab.News -> NewsScreen(
                            modifier = Modifier.padding(innerPadding),
                            uiState = uiState,
                        )

                        TownTab.Links -> LinksScreen(
                            modifier = Modifier.padding(innerPadding),
                            uiState = uiState,
                        )

                        TownTab.Profile -> ProfileScreen(
                            modifier = Modifier.padding(innerPadding),
                            uiState = uiState,
                            onCityChange = viewModel::updateOutageCity,
                            onStreetChange = viewModel::updateOutageStreet,
                            onBuildingChange = viewModel::updateOutageBuilding,
                            onLoadCityOptions = viewModel::loadProfileAddressOptions,
                            onThemeChange = viewModel::updateAppTheme,
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
private fun BrandSplashScreen(darkTheme: Boolean) {
    val splashBackground = if (darkTheme) {
        Color(0xFF052858)
    } else {
        Color.White
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(splashBackground),
        contentAlignment = Alignment.Center,
    ) {
        BrandLogo(
            modifier = Modifier.size(180.dp),
            alpha = 1f,
            darkTheme = darkTheme,
        )
    }
}

@Composable
private fun BrandLogo(
    modifier: Modifier = Modifier,
    alpha: Float,
    darkTheme: Boolean,
) {
    val logoRes = if (darkTheme) {
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
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Сповіщення",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        item {
            AlarmStatusCard(alarmActive = uiState.alarmActive)
        }
        item {
            Text(
                text = "Нові повідомлення",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (uiState.localPushes.isEmpty()) {
            item {
                EmptyCard("Коли з'являться нові сповіщення, вони будуть тут.")
            }
        } else {
            items(uiState.localPushes) { push ->
                PushMessageCard(push)
            }
        }
        item {
            Text(
                text = "Історія повідомлень за останній місяць",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (uiState.outageResult?.addressId == null) {
            item {
                EmptyCard("Для перегляду повідомлень оберіть адресу в Профілі.")
            }
        } else {
            item {
                StatusCard(
                    title = "Обрана адреса",
                    body = listOfNotNull(
                        uiState.outageResult.city,
                        uiState.outageResult.street,
                        uiState.outageResult.building,
                    ).joinToString(", "),
                )
            }
        }
        if (uiState.outageResult?.addressId != null) {
            if (uiState.historyLoading) {
                item {
                    StatusCard(
                        title = "Оновлення",
                        body = "Завантажуємо повідомлення для обраної адреси.",
                    )
                }
            }
            if (!uiState.historyLoading && uiState.historyMessages.isEmpty()) {
                item {
                    EmptyCard("Повідомлень для цієї адреси поки немає. Коли з'являться нові оновлення, вони будуть тут.")
                }
            } else {
                items(uiState.historyMessages) { message ->
                    MessageCard(message)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutagesScreen(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Графік відключень світла",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        if (uiState.outageResult?.addressId == null) {
            item {
                EmptyCard("Для перегляду графіка оберіть адресу в Профілі.")
            }
        } else {
            if (uiState.outageLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            uiState.outageResult.let { result ->
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
                val outagePeriods = result.periods.orEmpty().filterNotNull()
                if (!uiState.outageLoading && outagePeriods.isEmpty()) {
                    item {
                        EmptyCard("Для обраної адреси зараз немає доступного графіка відключень.")
                    }
                } else {
                    items(outagePeriods) { period ->
                        OutagePeriodCard(period)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreen(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
    onCityChange: (String) -> Unit,
    onStreetChange: (String) -> Unit,
    onBuildingChange: (String) -> Unit,
    onLoadCityOptions: () -> Unit,
    onThemeChange: (AppTheme) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Профіль",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        item {
            Text("Оберіть адресу для отримування персоналізованих сповіщень")
        }
        item {
            AddressSelectionFields(
                uiState = uiState,
                onCityChange = onCityChange,
                onStreetChange = onStreetChange,
                onBuildingChange = onBuildingChange,
                onLoadCityOptions = onLoadCityOptions,
            )
        }
        uiState.outageGuidance?.let { guidance ->
            item {
                EmptyCard(guidance)
            }
        }
        item {
            Text(
                text = "Кольорова тема",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = uiState.appTheme == theme,
                        onClick = { onThemeChange(theme) },
                        label = { Text(theme.title()) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressSelectionFields(
    uiState: MainUiState,
    onCityChange: (String) -> Unit,
    onStreetChange: (String) -> Unit,
    onBuildingChange: (String) -> Unit,
    onLoadCityOptions: () -> Unit,
) {
    var cityDropdownExpanded by remember { mutableStateOf(false) }
    var streetDropdownExpanded by remember { mutableStateOf(false) }
    var buildingDropdownExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                label = { Text("Населений пункт") },
                placeholder = { Text("Оберіть населений пункт") },
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

        if (uiState.outageStreetOptions.isNotEmpty() || uiState.outageStreet.isNotBlank()) {
            ExposedDropdownMenuBox(
                expanded = streetDropdownExpanded,
                onExpandedChange = { streetDropdownExpanded = it },
            ) {
                OutlinedTextField(
                    value = uiState.outageStreet,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    label = { Text("Адреса") },
                    placeholder = { Text("Оберіть вулицю") },
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

        if (uiState.outageBuildingOptions.isNotEmpty() || uiState.outageBuilding.isNotBlank()) {
            ExposedDropdownMenuBox(
                expanded = buildingDropdownExpanded,
                onExpandedChange = { buildingDropdownExpanded = it },
            ) {
                OutlinedTextField(
                    value = uiState.outageBuilding,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    label = { Text("Будинок") },
                    placeholder = { Text("Оберіть будинок") },
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
}

@Composable
private fun NewsScreen(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
) {
    var selectedNews by remember { mutableStateOf<NewsItem?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Новини громади",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        if (uiState.news.isEmpty()) {
            item {
                EmptyCard("Поки що новин немає або вони ще завантажуються.")
            }
        } else {
            items(uiState.news) { news ->
                NewsCard(
                    news = news,
                    onClick = { selectedNews = news },
                )
            }
        }
    }

    selectedNews?.let { news ->
        NewsDetailsDialog(
            news = news,
            onDismiss = { selectedNews = null },
        )
    }
}

@Composable
private fun NewsDetailsDialog(
    news: NewsItem,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 20.dp,
                    end = 20.dp,
                    bottom = 40.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        text = news.title.breakLongWords(),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item {
                    Text(
                        text = formatIsoDateTime(news.date),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                item {
                    Text(
                        text = news.content,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                item {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SportGreen,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("Назад")
                    }
                }
            }
        }
    }
}

@Composable
private fun LinksScreen(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
) {
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Корисні посилання",
                style = MaterialTheme.typography.headlineSmall,
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
private fun AlarmStatusCard(alarmActive: Boolean?) {
    val containerColor = when (alarmActive) {
        true -> MaterialTheme.colorScheme.errorContainer
        false -> MaterialTheme.colorScheme.secondaryContainer
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (alarmActive) {
        true -> MaterialTheme.colorScheme.onErrorContainer
        false -> MaterialTheme.colorScheme.onSecondaryContainer
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    StatusCard(
        title = "Тривога",
        body = when (alarmActive) {
            true -> "Зараз активна повітряна тривога. Будь ласка, подбайте про безпеку."
            false -> "Наразі активної повітряної тривоги немає."
            null -> "Оновлюємо інформацію про тривогу."
        },
        containerColor = containerColor,
        contentColor = contentColor,
    )
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
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
    val periodStatus = period.status()
    val containerColor = when (periodStatus) {
        OutagePeriodStatus.PowerOn -> MaterialTheme.colorScheme.secondaryContainer
        OutagePeriodStatus.PowerOff -> MaterialTheme.colorScheme.errorContainer
        OutagePeriodStatus.ScheduledOutage -> MaterialTheme.colorScheme.tertiaryContainer
        OutagePeriodStatus.Unknown -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (periodStatus) {
        OutagePeriodStatus.PowerOn -> MaterialTheme.colorScheme.onSecondaryContainer
        OutagePeriodStatus.PowerOff -> MaterialTheme.colorScheme.onErrorContainer
        OutagePeriodStatus.ScheduledOutage -> MaterialTheme.colorScheme.onTertiaryContainer
        OutagePeriodStatus.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = periodStatus.title,
                fontWeight = FontWeight.SemiBold,
            )
            Text("Час: ${period.formatTimeRange()}")
            period.duration
                ?.takeIf { it.isNotBlank() && it.uppercase() !in OUTAGE_STATUS_VALUES }
                ?.let { duration ->
                    Text("Тривалість відключення: $duration")
                }
        }
    }
}

@Composable
private fun NewsCard(
    news: NewsItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = news.title.breakLongWords(),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
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
        ) {
            Text(link.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun TownTab.symbol(): String = when (this) {
    TownTab.Notifications -> "\uD83D\uDD14"
    TownTab.Outages -> "\u26A1"
    TownTab.News -> "\uD83D\uDCF0"
    TownTab.Links -> "\uD83D\uDD17"
    TownTab.Profile -> "\uD83D\uDC64"
}

private fun formatIsoDateTime(value: String?): String {
    if (value.isNullOrBlank()) {
        return "невідомо"
    }
    val normalizedValue = value.trim()
    return runCatching {
        val instant = runCatching { Instant.parse(normalizedValue) }
            .recoverCatching { OffsetDateTime.parse(normalizedValue).toInstant() }
            .recoverCatching {
                LocalDateTime.parse(normalizedValue)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            }
            .getOrThrow()
        DATE_TIME_FORMATTER.format(instant.atZone(ZoneId.systemDefault()))
    }.getOrDefault("дату не вказано")
}

private fun OutagePeriod.status(): OutagePeriodStatus {
    return when (duration?.trim()?.uppercase()) {
        "ON" -> OutagePeriodStatus.PowerOn
        "OFF" -> OutagePeriodStatus.PowerOff
        null, "" -> OutagePeriodStatus.Unknown
        else -> OutagePeriodStatus.ScheduledOutage
    }
}

private fun OutagePeriod.formatTimeRange(): String {
    val fromTime = from.formatOutageTime()
    val toTime = to.formatOutageTime()
    return when {
        fromTime == "00:00" && toTime == "24:00" -> "увесь день"
        fromTime != null && toTime != null -> "з $fromTime до $toTime"
        fromTime != null -> "з $fromTime"
        toTime != null -> "до $toTime"
        else -> "невідомо"
    }
}

private fun String?.formatOutageTime(): String? {
    if (isNullOrBlank()) {
        return null
    }
    val value = trim()
    return runCatching {
        val instant = Instant.parse(value)
        DATE_TIME_FORMATTER.format(instant.atZone(ZoneId.systemDefault()))
    }.getOrElse {
        value
    }
}

private val DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")

private fun AppTheme.title(): String = when (this) {
    AppTheme.System -> "Як у телефоні"
    AppTheme.Light -> "Світла"
    AppTheme.Dark -> "Темна"
}

private enum class OutagePeriodStatus(val title: String) {
    PowerOn("Світло є"),
    PowerOff("Світла немає"),
    ScheduledOutage("Планове відключення"),
    Unknown("Статус графіка"),
}

private fun String.breakLongWords(maxChunkLength: Int = 16): String {
    return splitToSequence(' ')
        .joinToString(" ") { word ->
            if (word.length <= maxChunkLength) {
                word
            } else {
                word.chunked(maxChunkLength).joinToString(ZERO_WIDTH_SPACE)
            }
        }
}

private val OUTAGE_STATUS_VALUES = setOf("ON", "OFF")
private const val NOTIFICATIONS_REFRESH_INTERVAL_MS = 5 * 60 * 1_000L
private const val ZERO_WIDTH_SPACE = "\u200B"
