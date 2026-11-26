package id.tunarasa.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.tunarasa.app.data.ApiClient
import id.tunarasa.app.data.ApiResult
import id.tunarasa.app.data.AppSettingDto
import id.tunarasa.app.data.HealthStatusDto
import id.tunarasa.app.data.PlatformApi
import id.tunarasa.app.data.session.SessionStore
import id.tunarasa.app.data.UserResponse
import kotlinx.coroutines.launch

private enum class DashboardServiceMode(
    val wire: String,
    val label: String,
    val description: String,
) {
    FULL_LLM_BOT(
        wire = "full_llm_bot",
        label = "Full LLM Bot",
        description = "AI menjawab pertanyaan pengguna secara otomatis berdasarkan dokumen RAG institusi.",
    ),
    BOT_WITH_ADMIN_VALIDATION(
        wire = "bot_with_admin_validation",
        label = "Bot + Validasi Admin",
        description = "Draf jawaban AI masuk ke antrean untuk ditinjau dan disetujui petugas sebelum dikirim.",
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    platformApi: PlatformApi? = null,
    onNavigateToQueue: () -> Unit = {},
    onNavigateToQaLogs: () -> Unit = {},
    onNavigateToInstitutions: () -> Unit = {},
    onNavigateToUsers: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sessionStore = remember(context) {
        SessionStore(context.applicationContext)
    }

    val api = remember(platformApi, sessionStore) {
        platformApi ?: PlatformApi(
            ApiClient(
                tokenProvider = { sessionStore.getAdminToken() }
            )
        )
    }

    var currentUser by remember { mutableStateOf<UserResponse?>(null) }
    var healthStatus by remember { mutableStateOf<HealthStatusDto?>(null) }
    var healthError by remember { mutableStateOf<String?>(null) }
    var currentMode by remember { mutableStateOf(DashboardServiceMode.FULL_LLM_BOT) }
    var institutionCount by remember { mutableStateOf<Int?>(null) }
    var adminUserCount by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isUpdatingMode by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isFeedbackError by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val roleName = currentUser?.role
    val isSuperAdmin = roleName == "super_admin"

    fun refreshDashboard() {
        scope.launch {
            isLoading = true
            healthError = null

            when (val userRes = api.getCurrentUser()) {
                is ApiResult.Success -> currentUser = userRes.data
                is ApiResult.Error -> {
                    // Tetap lanjutkan pemuatan metrik publik jika sesi lokal dipakai saat offline
                }
            }

            when (val healthRes = api.getHealth()) {
                is ApiResult.Success -> healthStatus = healthRes.data
                is ApiResult.Error -> {
                    healthStatus = null
                    healthError = healthRes.message
                }
            }

            when (val configRes = api.getServiceConfig()) {
                is ApiResult.Success -> {
                    currentMode = DashboardServiceMode.entries.find {
                        it.wire == configRes.data.serviceMode
                    } ?: DashboardServiceMode.FULL_LLM_BOT
                }
                is ApiResult.Error -> Unit
            }

            when (val instRes = api.getInstitutions()) {
                is ApiResult.Success -> institutionCount = instRes.data.size
                is ApiResult.Error -> Unit
            }

            when (val usersRes = api.getAdminUsers()) {
                is ApiResult.Success -> adminUserCount = usersRes.data.size
                is ApiResult.Error -> Unit
            }

            isLoading = false
        }
    }

    fun updateServiceMode(target: DashboardServiceMode) {
        if (isUpdatingMode || target == currentMode) return
        scope.launch {
            isUpdatingMode = true
            feedbackMessage = null

            val result = api.updateAppSetting(
                AppSettingDto(
                    key = "service_mode",
                    value = target.wire,
                    description = "Mode operasional layanan AI TunaRasa",
                )
            )

            when (result) {
                is ApiResult.Success -> {
                    currentMode = target
                    isFeedbackError = false
                    feedbackMessage = "Mode layanan berhasil diubah ke ${target.label}."
                }
                is ApiResult.Error -> {
                    isFeedbackError = true
                    feedbackMessage = "Gagal mengubah mode layanan: ${result.message}"
                }
            }
            isUpdatingMode = false
        }
    }

    LaunchedEffect(Unit) {
        refreshDashboard()
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Keluar dari Panel Admin?") },
            text = {
                Text("Sesi masuk Anda pada perangkat ini akan diakhiri dan Anda perlu masuk kembali untuk mengakses panel admin.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        scope.launch {
                            sessionStore.clearAdminSession()
                            onLogout()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Keluar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Batal")
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Dashboard Admin",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Monitor sistem & kelola layanan",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshDashboard() },
                        enabled = !isLoading,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Perbarui data dashboard",
                        )
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Keluar dari akun admin",
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
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Banner Notifikasi Aksi
            if (!feedbackMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isFeedbackError) Color(0xFFFEE2E2) else Color(0xFFDCFCE7),
                    contentColor = if (isFeedbackError) Color(0xFF991B1B) else Color(0xFF166534),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = feedbackMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { feedbackMessage = null }) {
                            Text("Tutup")
                        }
                    }
                }
            }

            // 1. Kartu Profil & Peran Admin
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSuperAdmin) Color(0xFFFEF3C7) else Color(0xFFDBEAFE),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isSuperAdmin) Icons.Outlined.WorkspacePremium else Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = if (isSuperAdmin) Color(0xFFB45309) else Color(0xFF1D4ED8),
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentUser?.fullName?.takeIf { it.isNotBlank() }
                                    ?: currentUser?.email
                                    ?: "Administrator TunaRasa",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!currentUser?.email.isNullOrBlank()) {
                                Text(
                                    text = currentUser!!.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (isSuperAdmin) Color(0xFFFEF3C7) else Color(0xFFDBEAFE),
                        ) {
                            Text(
                                text = if (isSuperAdmin) "Super Admin" else "Admin Instansi",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSuperAdmin) Color(0xFFB45309) else Color(0xFF1D4ED8),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }

                    if (!isSuperAdmin) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Akses terlingkup pada institusi Anda. Pengaturan global sistem dan penambahan institusi baru hanya dapat dilakukan oleh Super Admin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }

            // 2. Kartu Status Kesehatan Sistem (BE-1 /api/v1/health)
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MonitorHeart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Kesehatan Sistem",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() },
                            )
                        }

                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            val isUp = healthStatus?.status == "UP"
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isUp) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                            ) {
                                Text(
                                    text = healthStatus?.status ?: "OFFLINE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUp) Color(0xFF15803D) else Color(0xFFB91C1C),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    if (healthStatus != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SystemMetricBox(
                                label = "Database",
                                value = healthStatus!!.database.uppercase(),
                                isHealthy = healthStatus!!.database == "up",
                                modifier = Modifier.weight(1f),
                            )
                            SystemMetricBox(
                                label = "Redis Cache",
                                value = if (healthStatus!!.redis == "up") "UP" else "DEGRADED",
                                isHealthy = healthStatus!!.redis == "up",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else if (!isLoading) {
                        Text(
                            text = healthError ?: "Server backend belum terjangkau. Periksa koneksi jaringan atau server lokal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 3. Kartu Mode Layanan AI
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Mode Layanan AI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() },
                        )
                    }

                    Text(
                        text = currentMode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (isSuperAdmin) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DashboardServiceMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = mode == currentMode,
                                    onClick = { updateServiceMode(mode) },
                                    enabled = !isUpdatingMode,
                                    label = {
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = "Mode Aktif: ${currentMode.label}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            // 4. Menu Manajemen Admin
            Text(
                text = "Menu Pengelolaan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .semantics { heading() },
            )

            if (isSuperAdmin) {
                DashboardMenuCard(
                    title = "Antrean Validasi Layanan",
                    subtitle = "Tinjau percakapan pengguna & validasi rekomendasi jawaban AI",
                    badgeText = if (currentMode == DashboardServiceMode.BOT_WITH_ADMIN_VALIDATION) "Aktif" else null,
                    icon = Icons.Outlined.SupportAgent,
                    accentColor = Color(0xFF2563EB),
                    onClick = onNavigateToQueue,
                )

                DashboardMenuCard(
                    title = "Log Q&A & Evaluasi",
                    subtitle = "Riwayat tanya jawab, tingkat kepercayaan AI, dan waktu respons",
                    icon = Icons.Outlined.QuestionAnswer,
                    accentColor = Color(0xFF7C3AED),
                    onClick = onNavigateToQaLogs,
                )
            }

            DashboardMenuCard(
                title = "Institusi & Dokumen RAG",
                subtitle = "Kelola daftar institusi serta unggah file knowledge base (PDF/TXT/MD/DOCX)",
                badgeText = institutionCount?.let { "$it aktif" },
                icon = Icons.Outlined.Business,
                accentColor = Color(0xFF059669),
                onClick = onNavigateToInstitutions,
            )

            DashboardMenuCard(
                title = "Manajemen Administrator & Pengguna",
                subtitle = if (isSuperAdmin) {
                    "Undang admin baru, atur peran, dan kelola status akses pengguna"
                } else {
                    "Lihat daftar anggota admin pada institusi Anda"
                },
                badgeText = adminUserCount?.let { "$it akun" },
                icon = Icons.Outlined.Group,
                accentColor = Color(0xFFD97706),
                onClick = onNavigateToUsers,
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SystemMetricBox(
    label: String,
    value: String,
    isHealthy: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (isHealthy) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = if (isHealthy) Color(0xFF16A34A) else Color(0xFFD97706),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isHealthy) Color(0xFF15803D) else Color(0xFFB45309),
                )
            }
        }
    }
}

@Composable
private fun DashboardMenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    badgeText: String? = null,
    onClick: () -> Unit,
) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.12f),
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!badgeText.isNullOrBlank()) {
                        Surface(
                            shape = CircleShape,
                            color = accentColor.copy(alpha = 0.12f),
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
