package id.tunarasa.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.tunarasa.app.data.AdminChatApi
import id.tunarasa.app.data.ApiClient
import id.tunarasa.app.data.ApiResult
import id.tunarasa.app.data.PlatformApi
import id.tunarasa.app.data.session.SessionStore
import id.tunarasa.app.ui.summary.formatIndonesianTimestamp
import id.tunarasa.contracts.InputMethod
import id.tunarasa.contracts.ServiceMode
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private enum class QaDateRangeFilter(val wire: String, val label: String, val hours: Long) {
    ONE_HOUR("1h", "1 Jam", 1),
    TWENTY_FOUR_HOURS("24h", "24 Jam", 24),
    SEVEN_DAYS("7d", "7 Hari", 7 * 24L),
    THIRTY_DAYS("30d", "30 Hari", 30 * 24L),
}

private enum class QaResponderFilter(val wire: String?, val label: String) {
    ALL(null, "Semua Penjawab"),
    LLM("llm", "Hanya LLM"),
    ADMIN("admin", "Hanya Admin"),
}

data class QaLogEntry(
    val id: String,
    val sessionId: String,
    val institutionName: String,
    val userName: String,
    val question: String,
    val answer: String,
    val confidence: Float,
    val responseTimeSeconds: Float,
    val serviceMode: ServiceMode,
    val respondedBy: String, // "llm" | "admin"
    val adminName: String? = null,
    val inputMethod: InputMethod,
    val contextSource: String? = null,
    val adminNote: String? = null,
    val isActiveSession: Boolean = false,
    val createdAtLabel: String,
    val evaluationScore: Int?,
)

data class LlmEvaluationMetric(
    val name: String,
    val scorePercent: Int,
    val description: String,
    val isHealthy: Boolean,
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QaLogsScreen(
    platformApi: PlatformApi? = null,
    grafanaBaseUrl: String = "http://10.0.2.2:3030",
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val sessionStore = remember(context) {
        SessionStore(context.applicationContext)
    }

    val client = remember(sessionStore) {
        ApiClient(tokenProvider = { sessionStore.getAdminToken() }, enableLogging = false)
    }
    DisposableEffect(client) { onDispose { client.close() } }
    val api = remember(platformApi, client) { platformApi ?: PlatformApi(client) }
    val chatApi = remember(client) { AdminChatApi(client) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var activeServiceMode by remember { mutableStateOf(ServiceMode.FULL_LLM_BOT) }

    var filteredLogs by remember { mutableStateOf<List<QaLogEntry>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedDateRange by remember { mutableStateOf(QaDateRangeFilter.SEVEN_DAYS) }
    var selectedResponder by remember { mutableStateOf(QaResponderFilter.ALL) }
    var selectedModeFilter by remember { mutableStateOf<ServiceMode?>(null) }

    var inspectedLog by remember { mutableStateOf<QaLogEntry?>(null) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }

    var refreshAttempt by remember { mutableIntStateOf(0) }
    var page by remember(searchQuery, selectedDateRange, selectedResponder, selectedModeFilter, refreshAttempt) {
        mutableIntStateOf(1)
    }
    var hasNextPage by remember { mutableStateOf(false) }

    val avgConfidencePercent = remember(filteredLogs) {
        if (filteredLogs.isEmpty()) 0f
        else (filteredLogs.sumOf { it.confidence.toDouble() } / filteredLogs.size * 100.0).toFloat()
    }

    val avgResponseSeconds = remember(filteredLogs) {
        if (filteredLogs.isEmpty()) 0f
        else (filteredLogs.sumOf { it.responseTimeSeconds.toDouble() } / filteredLogs.size).toFloat()
    }

    val llmResponseCount = remember(filteredLogs) {
        filteredLogs.count { it.respondedBy == "llm" }
    }

    val adminResponseCount = remember(filteredLogs) {
        filteredLogs.count { it.respondedBy == "admin" }
    }

    fun openGrafanaDashboard(slugPath: String, title: String) {
        val url = "$grafanaBaseUrl$slugPath?from=now-${selectedDateRange.wire}&to=now"
        runCatching {
            uriHandler.openUri(url)
        }.onFailure {
            bannerMessage = "Tidak dapat membuka $title ($url). Pastikan browser tersedia di perangkat."
        }
    }

    LaunchedEffect(refreshAttempt) {
        when (val config = api.getServiceConfig()) {
            is ApiResult.Success -> {
                activeServiceMode = ServiceMode.entries.firstOrNull { it.wire == config.data.serviceMode }
                    ?: ServiceMode.FULL_LLM_BOT
            }
            is ApiResult.Error -> Unit
        }
    }

    LaunchedEffect(searchQuery, selectedDateRange, selectedResponder, selectedModeFilter, refreshAttempt, page) {
        isRefreshing = true
        hasNextPage = false
        if (page == 1) filteredLogs = emptyList()
        bannerMessage = null
        delay(250)
        val dateFrom = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(System.currentTimeMillis() - selectedDateRange.hours * 3_600_000L))
        when (val result = chatApi.getQaLogs(
            dateFrom, searchQuery.trim(), selectedResponder.wire, selectedModeFilter?.wire, page,
        )) {
            is ApiResult.Success -> {
                filteredLogs = filteredLogs + result.data.data.qaLogs.mapNotNull { row ->
                    val mode = ServiceMode.entries.firstOrNull { it.wire == row.serviceMode }
                        ?: return@mapNotNull null
                    QaLogEntry(
                        id = row.id,
                        sessionId = row.conversation.sessionId.orEmpty(),
                        institutionName = row.institutionName.orEmpty(),
                        userName = row.conversation.sessionId?.takeLast(8).orEmpty(),
                        question = row.question,
                        answer = row.answer,
                        confidence = (row.confidence ?: 0) / 100f,
                        responseTimeSeconds = (row.responseTime ?: 0) / 1_000f,
                        serviceMode = mode,
                        respondedBy = row.respondedBy,
                        inputMethod = InputMethod.entries.firstOrNull { it.wire == row.inputMethod }
                            ?: if (row.gestureInput != null) InputMethod.GESTURE else InputMethod.TEXT,
                        isActiveSession = row.conversation.status in setOf("active", "waiting", "in_progress"),
                        createdAtLabel = formatIndonesianTimestamp(row.createdAt),
                        evaluationScore = row.evaluationScore,
                    )
                }
                hasNextPage = result.data.data.pagination.hasNextPage
            }
            is ApiResult.Error -> bannerMessage = result.message
        }
        isRefreshing = false
    }

    // BottomSheet Detail Percakapan & Log Q&A
    if (inspectedLog != null) {
        val detail = inspectedLog!!
        ModalBottomSheet(
            onDismissRequest = { inspectedLog = null },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Detail Log Q&A",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Sesi ${detail.sessionId} • ${detail.institutionName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    ConfidenceBadge(confidence = detail.confidence)
                }

                // Metadata Ringkas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DetailMetaChip(
                        icon = Icons.Outlined.Person,
                        text = detail.userName,
                    )
                    DetailMetaChip(
                        icon = Icons.Outlined.TouchApp,
                        text = detail.inputMethod.labelId(),
                    )
                    DetailMetaChip(
                        icon = Icons.Outlined.Schedule,
                        text = String.format(Locale.US, "%.1f dtk", detail.responseTimeSeconds),
                    )
                }

                HorizontalDivider()

                // Pertanyaan Pengguna
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Pertanyaan Pengguna (${detail.createdAtLabel})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = detail.question,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                // Jawaban Sistem / Admin
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (detail.respondedBy == "admin") Color(0xFFECFDF5) else Color(0xFFEFF6FF),
                    border = BorderStroke(
                        1.dp,
                        if (detail.respondedBy == "admin") Color(0xFFA7F3D0) else Color(0xFFBFDBFE),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (detail.respondedBy == "admin") {
                                "Jawaban Divalidasi Admin (${detail.adminName ?: "Petugas"})"
                            } else {
                                "Jawaban Otomatis LLM (${detail.serviceMode.labelId()})"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (detail.respondedBy == "admin") Color(0xFF065F46) else Color(0xFF1E40AF),
                        )
                        Text(
                            text = detail.answer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (!detail.contextSource.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Sumber Konteks RAG: ${detail.contextSource}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (!detail.adminNote.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFFBEB),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Catatan Admin",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E),
                            )
                            Text(
                                text = detail.adminNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF78350F),
                            )
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Log Q&A & Monitoring",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Riwayat tanya jawab, evaluasi LLM & Grafana",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali ke Dashboard",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshAttempt++ },
                        enabled = !isRefreshing,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Perbarui log Q&A",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Log Percakapan (${filteredLogs.size})") },
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Evaluasi LLM") },
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Banner Notifikasi
                if (!bannerMessage.isNullOrBlank()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = bannerMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { bannerMessage = null }) {
                                    Text("Tutup")
                                }
                            }
                        }
                    }
                }

                // Grid Statistik 2x2
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            QaMetricCard(
                                label = "Total Log Q&A",
                                value = filteredLogs.size.toString(),
                                accentColor = Color(0xFF2563EB),
                                modifier = Modifier.weight(1f),
                            )
                            QaMetricCard(
                                label = "Rata-rata Keyakinan",
                                value = String.format(Locale.US, "%.1f%%", avgConfidencePercent),
                                accentColor = Color(0xFF16A34A),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            QaMetricCard(
                                label = "Rata-rata Respons",
                                value = String.format(Locale.US, "%.1f dtk", avgResponseSeconds),
                                accentColor = Color(0xFFD97706),
                                modifier = Modifier.weight(1f),
                            )
                            QaMetricCard(
                                label = "LLM / Admin",
                                value = "$llmResponseCount / $adminResponseCount",
                                accentColor = Color(0xFF7C3AED),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // Pintasan Dashboard Grafana
                item {
                    ElevatedCard(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.BarChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Dashboard Monitoring Grafana",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GrafanaChipButton(
                                    label = "System Overview",
                                    onClick = {
                                        openGrafanaDashboard(
                                            "/d/tunarasa-overview/tunarasa-overview",
                                            "System Overview",
                                        )
                                    },
                                )
                                GrafanaChipButton(
                                    label = "LLM Performance",
                                    onClick = {
                                        openGrafanaDashboard(
                                            "/d/llm-metrics/llm-performance",
                                            "LLM Performance",
                                        )
                                    },
                                )
                                GrafanaChipButton(
                                    label = "Conversation Analytics",
                                    onClick = {
                                        openGrafanaDashboard(
                                            "/d/conversation-analytics/conversation-analytics",
                                            "Conversation Analytics",
                                        )
                                    },
                                )
                                GrafanaChipButton(
                                    label = "DeepEval Monitoring",
                                    onClick = {
                                        openGrafanaDashboard(
                                            "/d/deepeval-monitoring/deepeval-llm-evaluation",
                                            "DeepEval Monitoring",
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                if (selectedTabIndex == 0) {
                    // Tab 0: Pencarian & Filter
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Cari pertanyaan, jawaban, atau instansi...") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = null,
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = "Hapus pencarian",
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            // Filter Rentang Waktu
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                QaDateRangeFilter.entries.forEach { range ->
                                    FilterChip(
                                        selected = selectedDateRange == range,
                                        onClick = { selectedDateRange = range },
                                        label = { Text(range.label) },
                                    )
                                }
                            }

                            // Filter Penjawab & Mode Layanan
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                QaResponderFilter.entries.forEach { responder ->
                                    FilterChip(
                                        selected = selectedResponder == responder,
                                        onClick = { selectedResponder = responder },
                                        label = { Text(responder.label) },
                                    )
                                }
                                FilterChip(
                                    selected = selectedModeFilter == ServiceMode.FULL_LLM_BOT,
                                    onClick = {
                                        selectedModeFilter = if (selectedModeFilter == ServiceMode.FULL_LLM_BOT) {
                                            null
                                        } else {
                                            ServiceMode.FULL_LLM_BOT
                                        }
                                    },
                                    label = { Text("Full LLM") },
                                )
                                FilterChip(
                                    selected = selectedModeFilter == ServiceMode.BOT_WITH_ADMIN_VALIDATION,
                                    onClick = {
                                        selectedModeFilter =
                                            if (selectedModeFilter == ServiceMode.BOT_WITH_ADMIN_VALIDATION) {
                                                null
                                            } else {
                                                ServiceMode.BOT_WITH_ADMIN_VALIDATION
                                            }
                                    },
                                    label = { Text("LLM + Admin") },
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Riwayat Tanya Jawab (${filteredLogs.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() },
                        )
                    }

                    if (filteredLogs.isEmpty()) {
                        item {
                            ElevatedCard(
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.QuestionAnswer,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp),
                                    )
                                    Text(
                                        text = "Tidak ada log Q&A yang cocok",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "Ubah kata kunci pencarian atau perluas rentang waktu filter.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredLogs, key = { it.id }) { entry ->
                            QaLogItemCard(
                                entry = entry,
                                onClick = { inspectedLog = entry },
                            )
                        }
                        if (hasNextPage) item {
                            TextButton(onClick = { page++ }, enabled = !isRefreshing) { Text("Tampilkan lagi") }
                        }
                    }
                } else {
                    // Tab 1: Evaluasi LLM & Rekomendasi DeepEval
                    item {
                        Text(
                            text = "Skor Evaluasi RAG",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() },
                        )
                    }

                    val scores = filteredLogs.mapNotNull { it.evaluationScore }
                    if (scores.isEmpty()) {
                        item { Text("Belum ada skor evaluasi untuk log yang dimuat.") }
                    } else {
                        val average = scores.average().toInt()
                        item {
                            EvaluationMetricCard(
                                LlmEvaluationMetric("Skor rata-rata", average, "${scores.size} log dievaluasi", average >= 80),
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun QaMetricCard(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GrafanaChipButton(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun QaLogItemCard(
    entry: QaLogEntry,
    onClick: () -> Unit,
) {
    val isAdmin = entry.respondedBy == "admin"

    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Baris Header: Sesi, Penjawab, Keyakinan
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = entry.sessionId,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (isAdmin) Color(0xFFDBEAFE) else Color(0xFFF3E8FF),
                    ) {
                        Text(
                            text = if (isAdmin) "Admin" else "LLM",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isAdmin) Color(0xFF1D4ED8) else Color(0xFF7E22CE),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }

                ConfidenceBadge(confidence = entry.confidence)
            }

            // Nama Institusi
            Text(
                text = entry.institutionName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Pertanyaan
            Text(
                text = "T: ${entry.question}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Ringkasan Jawaban
            Text(
                text = "J: ${entry.answer}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // Footer Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = entry.inputMethod.labelId(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "• ${String.format(Locale.US, "%.1f dtk", entry.responseTimeSeconds)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = entry.createdAtLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: Float) {
    val pct = (confidence * 100).toInt()
    val (bg, fg) = when {
        pct >= 85 -> Pair(Color(0xFFDCFCE7), Color(0xFF15803D))
        pct >= 70 -> Pair(Color(0xFFFEF3C7), Color(0xFFB45309))
        else -> Pair(Color(0xFFFEE2E2), Color(0xFFB91C1C))
    }

    Surface(
        shape = CircleShape,
        color = bg,
    ) {
        Text(
            text = "$pct% yakin",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun DetailMetaChip(
    icon: ImageVector,
    text: String,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EvaluationMetricCard(metric: LlmEvaluationMetric) {
    val accent = if (metric.isHealthy) Color(0xFF16A34A) else Color(0xFFD97706)
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = metric.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${metric.scorePercent}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }

            LinearProgressIndicator(
                progress = { (metric.scorePercent / 100f).coerceIn(0f, 1f) },
                color = accent,
                trackColor = accent.copy(alpha = 0.15f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )

            Text(
                text = metric.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


private fun InputMethod.labelId(): String = when (this) {
    InputMethod.TEXT -> "Teks"
    InputMethod.SPEECH -> "Suara"
    InputMethod.GESTURE -> "Isyarat SIBI"
}

private fun ServiceMode.labelId(): String = when (this) {
    ServiceMode.FULL_LLM_BOT -> "Full LLM Bot"
    ServiceMode.BOT_WITH_ADMIN_VALIDATION -> "Bot + Validasi Admin"
}
