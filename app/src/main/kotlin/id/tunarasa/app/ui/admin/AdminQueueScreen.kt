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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.tunarasa.app.data.ApiClient
import id.tunarasa.app.data.ApiResult
import id.tunarasa.app.data.AdminChatApi
import id.tunarasa.app.data.PlatformApi
import id.tunarasa.app.data.session.SessionStore
import id.tunarasa.app.ui.summary.formatIndonesianTimestamp
import id.tunarasa.contracts.ConversationStatus
import id.tunarasa.contracts.InputMethod
import id.tunarasa.contracts.ServiceMode
import kotlinx.coroutines.launch

enum class QueuePriority(val label: String, val containerColor: Color, val contentColor: Color) {
    LOW("Rendah", Color(0xFFF3F4F6), Color(0xFF4B5563)),
    NORMAL("Normal", Color(0xFFEFF6FF), Color(0xFF1D4ED8)),
    HIGH("Tinggi", Color(0xFFFEF3C7), Color(0xFFB45309)),
    URGENT("Mendesak", Color(0xFFFEE2E2), Color(0xFFB91C1C)),
}

enum class QueueSenderRole {
    USER,
    AI_DRAFT,
    ADMIN,
}

data class QueueThreadMessage(
    val id: String,
    val sender: QueueSenderRole,
    val content: String,
    val timestamp: String,
    val inputMethod: InputMethod? = null,
    val adminName: String? = null,
)

data class AiSuggestionItem(
    val id: String,
    val content: String,
    val confidence: Float,
)

data class QueueConversationItem(
    val id: String,
    val sessionId: String,
    val userName: String,
    val institutionName: String,
    val status: ConversationStatus,
    val priority: QueuePriority,
    val lastMessage: String,
    val waitMinutes: Int,
    val messages: List<QueueThreadMessage>,
    val aiSuggestion: AiSuggestionItem? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminQueueScreen(
    platformApi: PlatformApi? = null,
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sessionStore = remember(context) {
        SessionStore(context.applicationContext)
    }

    val client = remember(sessionStore) {
        ApiClient(tokenProvider = { sessionStore.getAdminToken() }, enableLogging = false)
    }
    DisposableEffect(client) { onDispose { client.close() } }
    val api = remember(platformApi, client) { platformApi ?: PlatformApi(client) }
    val chatApi = remember(client) { AdminChatApi(client) }

    var serviceMode by remember { mutableStateOf(ServiceMode.BOT_WITH_ADMIN_VALIDATION) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf<ConversationStatus?>(null) }
    var conversations by remember { mutableStateOf<List<QueueConversationItem>>(emptyList()) }
    var selectedConversationId by remember { mutableStateOf<String?>(null) }
    var replyDraft by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }

    val selectedConversation = remember(conversations, selectedConversationId) {
        conversations.find { it.id == selectedConversationId }
    }

    val filteredConversations = remember(conversations, selectedFilter) {
        if (selectedFilter == null) {
            conversations
        } else {
            conversations.filter { it.status == selectedFilter }
        }
    }

    val waitingCount = conversations.count { it.status == ConversationStatus.WAITING }
    val activeCount = conversations.count {
        it.status == ConversationStatus.IN_PROGRESS || it.status == ConversationStatus.ACTIVE
    }
    val resolvedCount = conversations.count { it.status == ConversationStatus.RESOLVED }

    fun loadQueueData() {
        scope.launch {
            isRefreshing = true
            when (val configRes = api.getServiceConfig()) {
                is ApiResult.Success -> {
                    serviceMode = ServiceMode.entries.find {
                        it.wire == configRes.data.serviceMode
                    } ?: ServiceMode.BOT_WITH_ADMIN_VALIDATION
                }
                is ApiResult.Error -> {
                    // Gunakan state lokal saat server belum terjangkau
                }
            }

            when (val result = chatApi.getQueue()) {
                is ApiResult.Success -> {
                    conversations = result.data.conversations.map { item ->
                        QueueConversationItem(
                            id = item.id,
                            sessionId = item.sessionId,
                            userName = item.userName,
                            institutionName = item.institutionName.orEmpty(),
                            status = ConversationStatus.entries.first { it.wire == item.status },
                            priority = QueuePriority.entries.firstOrNull { it.name.equals(item.priority, true) }
                                ?: QueuePriority.NORMAL,
                            lastMessage = item.lastMessage,
                            waitMinutes = item.waitTime,
                            messages = emptyList(),
                        )
                    }
                }
                is ApiResult.Error -> bannerMessage = result.message
            }
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        loadQueueData()
    }

    suspend fun updateThread(id: String) {
        when (val result = chatApi.getMessages(id)) {
            is ApiResult.Success -> {
                val thread = result.data.messages
                val draft = thread.lastOrNull { it.type == "llm_recommendation" }
                conversations = conversations.map { item ->
                    if (item.id != id) item
                    else item.copy(
                        status = ConversationStatus.entries.first { it.wire == result.data.status },
                        messages = thread.map { message ->
                            QueueThreadMessage(
                                id = message.id,
                                sender = when (message.type) {
                                    "user" -> QueueSenderRole.USER
                                    "admin" -> QueueSenderRole.ADMIN
                                    else -> QueueSenderRole.AI_DRAFT
                                },
                                content = message.content,
                                timestamp = formatIndonesianTimestamp(message.timestamp),
                                inputMethod = InputMethod.entries.firstOrNull { it.wire == message.inputMethod },
                                adminName = message.adminName,
                            )
                        },
                        aiSuggestion = draft?.let {
                            AiSuggestionItem(it.id, it.content, (it.confidence ?: 0) / 100f)
                        },
                    )
                }
            }
            is ApiResult.Error -> bannerMessage = result.message
        }
    }

    fun openConversation(item: QueueConversationItem) {
        selectedConversationId = item.id
        replyDraft = ""
        bannerMessage = null
        scope.launch { updateThread(item.id) }
    }

    fun closeConversationDetail() {
        selectedConversationId = null
        replyDraft = ""
    }

    fun sendAdminReply() {
        val target = selectedConversation ?: return
        val trimmed = replyDraft.trim()
        if (trimmed.isEmpty() || isSending) return

        scope.launch {
            isSending = true
            try {
                val result = when (val claim = chatApi.assign(target.id)) {
                    is ApiResult.Success -> chatApi.respond(target.id, trimmed, trimmed == target.aiSuggestion?.content?.trim())
                    is ApiResult.Error -> claim
                }
                when (result) {
                    is ApiResult.Success -> {
                        replyDraft = ""
                        bannerMessage = "Tanggapan terkirim ke pengguna."
                        updateThread(target.id)
                    }
                    is ApiResult.Error -> bannerMessage = result.message
                }
            } finally {
                isSending = false
            }
        }
    }

    fun resolveSelectedConversation() {
        val target = selectedConversation ?: return
        if (isSending) return
        scope.launch {
            isSending = true
            try {
                val result = when (val claim = chatApi.assign(target.id)) {
                    is ApiResult.Success -> chatApi.resolve(target.id)
                    is ApiResult.Error -> claim
                }
                when (result) {
                    is ApiResult.Success -> {
                        closeConversationDetail()
                        bannerMessage = "Percakapan dengan ${target.userName} ditandai selesai."
                        loadQueueData()
                    }
                    is ApiResult.Error -> bannerMessage = result.message
                }
            } finally {
                isSending = false
            }
        }
    }

    if (selectedConversation != null) {
        ConversationValidationDetail(
            conversation = selectedConversation,
            replyDraft = replyDraft,
            isSending = isSending,
            bannerMessage = bannerMessage,
            onReplyDraftChange = { replyDraft = it },
            onUseSuggestion = { suggestion ->
                replyDraft = suggestion.content
            },
            onSendReply = { sendAdminReply() },
            onResolveClick = { resolveSelectedConversation() },
            onBackClick = { closeConversationDetail() },
        )
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Antrean Validasi",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
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
                            onClick = { loadQueueData() },
                            enabled = !isRefreshing,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Perbarui antrean",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Banner status jika mode Full LLM Bot sedang aktif
                if (serviceMode == ServiceMode.FULL_LLM_BOT) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFFEF3C7),
                            contentColor = Color(0xFF92400E),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Mode Full LLM Bot sedang aktif. Pertanyaan pengguna dijawab otomatis tanpa antrean validasi. Ubah ke mode Bot + Validasi Admin di Dashboard bila ingin meninjau jawaban.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                // Banner feedback aksi
                if (!bannerMessage.isNullOrBlank()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFDCFCE7),
                            contentColor = Color(0xFF166534),
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

                // Grid Statistik Antrean (2x2)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            QueueStatCard(
                                label = "Menunggu",
                                value = waitingCount.toString(),
                                accentColor = Color(0xFFD97706),
                                modifier = Modifier.weight(1f),
                            )
                            QueueStatCard(
                                label = "Diproses",
                                value = activeCount.toString(),
                                accentColor = Color(0xFF2563EB),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            QueueStatCard(
                                label = "Selesai",
                                value = resolvedCount.toString(),
                                accentColor = Color(0xFF16A34A),
                                modifier = Modifier.weight(1f),
                            )
                            QueueStatCard(
                                label = "Rata-rata Respons",
                                value = "1.4 mnt",
                                accentColor = Color(0xFF7C3AED),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // Filter Status
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedFilter == null,
                            onClick = { selectedFilter = null },
                            label = { Text("Semua (${conversations.size})") },
                        )
                        FilterChip(
                            selected = selectedFilter == ConversationStatus.WAITING,
                            onClick = { selectedFilter = ConversationStatus.WAITING },
                            label = { Text("Menunggu ($waitingCount)") },
                        )
                        FilterChip(
                            selected = selectedFilter == ConversationStatus.IN_PROGRESS,
                            onClick = { selectedFilter = ConversationStatus.IN_PROGRESS },
                            label = { Text("Diproses ($activeCount)") },
                        )
                        FilterChip(
                            selected = selectedFilter == ConversationStatus.RESOLVED,
                            onClick = { selectedFilter = ConversationStatus.RESOLVED },
                            label = { Text("Selesai ($resolvedCount)") },
                        )
                    }
                }

                // Header Daftar
                item {
                    Text(
                        text = "Daftar Percakapan (${filteredConversations.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                }

                if (filteredConversations.isEmpty()) {
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
                                    imageVector = Icons.Outlined.Forum,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp),
                                )
                                Text(
                                    text = "Tidak ada antrean pada filter ini",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Percakapan baru dari pengguna akan muncul di sini.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(filteredConversations, key = { it.id }) { item ->
                        QueueConversationCard(
                            item = item,
                            onClick = { openConversation(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueStatCard(
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
private fun QueueConversationCard(
    item: QueueConversationItem,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
                        text = item.userName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    StatusPill(status = item.status)
                }

                if (item.priority != QueuePriority.NORMAL) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = item.priority.containerColor,
                    ) {
                        Text(
                            text = item.priority.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = item.priority.contentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Text(
                text = item.institutionName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = item.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "${item.messages.size} pesan",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${item.waitMinutes} mnt lalu",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = "Tinjau →",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationValidationDetail(
    conversation: QueueConversationItem,
    replyDraft: String,
    isSending: Boolean,
    bannerMessage: String?,
    onReplyDraftChange: (String) -> Unit,
    onUseSuggestion: (AiSuggestionItem) -> Unit,
    onSendReply: () -> Unit,
    onResolveClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    val isResolved = conversation.status == ConversationStatus.RESOLVED

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = conversation.userName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = conversation.institutionName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali ke daftar antrean",
                        )
                    }
                },
                actions = {
                    if (!isResolved) {
                        FilledTonalButton(
                            onClick = onResolveClick,
                            enabled = !isSending,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Selesaikan",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    } else {
                        Box(modifier = Modifier.padding(end = 12.dp)) {
                            StatusPill(status = conversation.status)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = replyDraft,
                        onValueChange = onReplyDraftChange,
                        placeholder = {
                            Text(
                                if (isResolved) "Percakapan telah selesai"
                                else "Tulis atau sunting jawaban untuk pengguna..."
                            )
                        },
                        enabled = !isResolved && !isSending,
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                    )
                    FilledIconButton(
                        onClick = onSendReply,
                        enabled = !isResolved && !isSending && replyDraft.trim().isNotEmpty(),
                        modifier = Modifier.size(52.dp),
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Kirim jawaban",
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (bannerMessage != null) {
                item { Text(bannerMessage, color = MaterialTheme.colorScheme.onSurface) }
            }

            // Kartu Rekomendasi AI (LLM Suggestion)
            if (conversation.aiSuggestion != null && !isResolved) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFFFBEB),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFFB45309),
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = "Saran Jawaban AI",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E),
                                    )
                                }
                                Text(
                                    text = "Keyakinan ${(conversation.aiSuggestion.confidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFB45309),
                                )
                            }

                            Text(
                                text = conversation.aiSuggestion.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF78350F),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(
                                    onClick = { onUseSuggestion(conversation.aiSuggestion) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    border = BorderStroke(1.dp, Color(0xFFD97706)),
                                ) {
                                    Text(
                                        text = "Gunakan Saran",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF92400E),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Daftar Gelembung Pesan
            items(conversation.messages, key = { it.id }) { msg ->
                val isAdmin = msg.sender == QueueSenderRole.ADMIN
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isAdmin) Arrangement.End else Arrangement.Start,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = when (msg.sender) {
                            QueueSenderRole.ADMIN -> MaterialTheme.colorScheme.primary
                            QueueSenderRole.AI_DRAFT -> MaterialTheme.colorScheme.secondaryContainer
                            QueueSenderRole.USER -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = when (msg.sender) {
                            QueueSenderRole.ADMIN -> MaterialTheme.colorScheme.onPrimary
                            QueueSenderRole.AI_DRAFT -> MaterialTheme.colorScheme.onSecondaryContainer
                            QueueSenderRole.USER -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.widthIn(max = 310.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = when (msg.sender) {
                                        QueueSenderRole.ADMIN -> Icons.Outlined.SupportAgent
                                        QueueSenderRole.AI_DRAFT -> Icons.Outlined.SmartToy
                                        QueueSenderRole.USER -> Icons.Outlined.Person
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = when (msg.sender) {
                                        QueueSenderRole.ADMIN -> msg.adminName ?: "Admin"
                                        QueueSenderRole.AI_DRAFT -> "Draf AI"
                                        QueueSenderRole.USER -> conversation.userName
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (msg.inputMethod != null) {
                                    Text(
                                        text = "• ${msg.inputMethod.displayLabel()}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }

                            Text(
                                text = msg.content,
                                style = MaterialTheme.typography.bodyMedium,
                            )

                            Text(
                                text = msg.timestamp,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: ConversationStatus) {
    val (label, bg, fg) = when (status) {
        ConversationStatus.WAITING -> Triple("Menunggu", Color(0xFFFEF3C7), Color(0xFFB45309))
        ConversationStatus.IN_PROGRESS -> Triple("Diproses", Color(0xFFDBEAFE), Color(0xFF1D4ED8))
        ConversationStatus.ACTIVE -> Triple("Aktif", Color(0xFFDBEAFE), Color(0xFF1D4ED8))
        ConversationStatus.RESOLVED -> Triple("Selesai", Color(0xFFDCFCE7), Color(0xFF15803D))
    }

    Surface(
        shape = CircleShape,
        color = bg,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private fun InputMethod.displayLabel(): String = when (this) {
    InputMethod.TEXT -> "Teks"
    InputMethod.SPEECH -> "Suara"
    InputMethod.GESTURE -> "Isyarat SIBI"
}
