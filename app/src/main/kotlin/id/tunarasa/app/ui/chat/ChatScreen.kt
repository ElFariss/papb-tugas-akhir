package id.tunarasa.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tunarasa.app.data.ApiClient
import id.tunarasa.app.data.ApiResult
import id.tunarasa.app.data.ChatEntry
import id.tunarasa.app.data.InstitutionResponse
import id.tunarasa.app.data.PlatformApi
import id.tunarasa.app.data.PublicChatApi
import id.tunarasa.app.data.session.SessionStore
import id.tunarasa.app.ui.theme.BlueHeroCard
import id.tunarasa.app.ui.theme.MintBadgeBg
import id.tunarasa.app.ui.theme.MintBadgeText
import id.tunarasa.contracts.ServiceMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class UiChatMessage(
    val id: String,
    val isUser: Boolean,
    val title: String? = null,
    val content: String,
    val sourceLabel: String? = null,
    val relatedQuestion: String? = null,
    val inputMethod: String = "text",
)

@Composable
fun ChatScreen(
    institutionSlug: String = "dukcapil",
    initialQuestion: String = "",
    inputMethod: String = "text",
    onBackClick: () -> Unit = {},
    onSwitchToSibi: () -> Unit = {},
    onSwitchToSpeech: () -> Unit = {},
    onFinishConversation: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val client = remember { ApiClient(enableLogging = false) }
    val api = remember(client) { PublicChatApi(client) }
    val platform = remember(client) { PlatformApi(client) }
    val session = remember(context) { SessionStore(context.applicationContext) }
    var institutionTitle by remember(institutionSlug) {
        mutableStateOf(institutionSlug.replaceFirstChar { it.uppercase() })
    }
    var selectedInstitution by remember(institutionSlug) { mutableStateOf<InstitutionResponse?>(null) }
    DisposableEffect(client) { onDispose { client.close() } }

    val messages = remember {
        mutableStateListOf(
            UiChatMessage(
                id = "welcome-msg",
                isUser = false,
                content = "Halo! Ada yang bisa kami bantu tentang layanan $institutionTitle?"
            )
        )
    }

    var inputText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var requestError by remember { mutableStateOf<String?>(null) }
    var pendingQuestion by remember { mutableStateOf<Pair<String, String>?>(null) }
    var savedConversationId by remember { mutableStateOf<Int?>(null) }
    var validationConversationId by remember { mutableStateOf<Int?>(null) }
    var serviceMode by remember { mutableStateOf<ServiceMode?>(null) }
    var validationPending by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    fun sendQuestion(text: String, method: String = "text") {
        val cleanText = text.trim()
        if (cleanText.isBlank() || isProcessing || savedConversationId != null) return
        if (cleanText.length > 500 || messages.size >= 501) {
            coroutineScope.launch { snackbar.showSnackbar("Batas panjang pertanyaan atau jumlah pesan tercapai.") }
            return
        }
        isProcessing = true

        coroutineScope.launch {
            try {
                val sessionId = session.getOrCreateSessionId()
                if (serviceMode == null) {
                    when (val result = platform.getServiceConfig()) {
                        is ApiResult.Success -> {
                            serviceMode = ServiceMode.entries.find { it.wire == result.data.serviceMode }
                            if (serviceMode == null) {
                                requestError = "Mode layanan tidak dikenali."
                                pendingQuestion = cleanText to method
                                return@launch
                            }
                        }
                        is ApiResult.Error -> {
                            requestError = result.message
                            pendingQuestion = cleanText to method
                            return@launch
                        }
                    }
                }
                var institution = selectedInstitution
                if (institution == null) {
                    institution = when (val result = platform.getInstitutions()) {
                        is ApiResult.Success -> result.data.firstOrNull { it.slug == institutionSlug }
                        is ApiResult.Error -> {
                            requestError = result.message
                            pendingQuestion = cleanText to method
                            return@launch
                        }
                    }
                    if (institution == null) {
                        requestError = "Institusi tidak tersedia."
                        pendingQuestion = cleanText to method
                        return@launch
                    }
                    selectedInstitution = institution
                    institutionTitle = institution.name
                }
                if (serviceMode == ServiceMode.BOT_WITH_ADMIN_VALIDATION) {
                    when (val result = api.submitForValidation(cleanText, sessionId, institution.id, institutionSlug, method)) {
                        is ApiResult.Success -> {
                            validationPending = true
                            validationConversationId = result.data.conversationId
                            if (inputText.trim() == cleanText) inputText = ""
                        }
                        is ApiResult.Error -> {
                            requestError = result.message
                            pendingQuestion = cleanText to method
                        }
                    }
                    return@launch
                }
                when (val result = api.ask(cleanText, sessionId, institutionSlug, method)) {
                    is ApiResult.Success -> {
                        val index = messages.size
                        messages.add(UiChatMessage("user-$index", true, content = cleanText, inputMethod = method))
                        messages.add(
                            UiChatMessage(
                                id = "bot-$index",
                                isUser = false,
                                content = result.data.answer,
                                sourceLabel = result.data.sources.firstOrNull()?.let { "Sumber: ${it.documentId}" },
                                relatedQuestion = result.data.followUpSuggestions.firstOrNull(),
                                inputMethod = method,
                            )
                        )
                        if (inputText.trim() == cleanText) inputText = ""
                        listState.animateScrollToItem(messages.lastIndex)
                    }
                    is ApiResult.Error -> {
                        pendingQuestion = cleanText to method
                        requestError = result.message
                    }
                }
            } finally {
                isProcessing = false
            }
        }
    }

    LaunchedEffect(initialQuestion) {
        if (initialQuestion.isNotBlank() && messages.size == 1) {
            sendQuestion(initialQuestion, inputMethod)
        }
    }

    LaunchedEffect(validationConversationId) {
        val conversationId = validationConversationId ?: return@LaunchedEffect
        val sessionId = session.getOrCreateSessionId()
        while (true) {
            when (val result = api.getMessages(conversationId, sessionId)) {
                is ApiResult.Success -> {
                    val thread = result.data.messages.map {
                        UiChatMessage(
                            id = it.id,
                            isUser = it.type == "user",
                            title = it.adminName,
                            content = it.content,
                            inputMethod = it.inputMethod ?: "text",
                        )
                    }
                    if (thread.isNotEmpty() && messages.drop(1) != thread) {
                        messages.removeRange(1, messages.size)
                        messages.addAll(thread)
                        validationPending = thread.last().isUser
                        listState.animateScrollToItem(messages.lastIndex)
                    }
                }
                is ApiResult.Error -> Unit
            }
            delay(2_000)
        }
    }

    fun finishConversation() {
        isProcessing = true
        coroutineScope.launch {
            try {
                val sessionId = session.getOrCreateSessionId()
                var conversationId = savedConversationId ?: validationConversationId
                if (conversationId == null) {
                    val institution = selectedInstitution ?: return@launch
                    val entries = messages.drop(1).map {
                        ChatEntry(it.content, if (it.isUser) "user" else "assistant", it.inputMethod)
                    }
                    when (val result = api.saveConversation(sessionId, institution.id, entries)) {
                        is ApiResult.Success -> {
                            conversationId = result.data.conversationId
                            savedConversationId = conversationId
                        }
                        is ApiResult.Error -> {
                            snackbar.showSnackbar(result.message)
                            return@launch
                        }
                    }
                }
                when (val result = api.generateSummary(conversationId, sessionId)) {
                    is ApiResult.Success -> onFinishConversation(result.data.qrCode.accessToken, institutionTitle)
                    is ApiResult.Error -> snackbar.showSnackbar(result.message)
                }
            } finally {
                isProcessing = false
            }
        }
    }

    val error = requestError
    if (error != null) {
        ConnectionErrorScreen(
            message = error,
            onBackClick = {
                inputText = pendingQuestion?.first.orEmpty()
                pendingQuestion = null
                requestError = null
            },
            onRetryClick = {
                val retry = pendingQuestion
                requestError = null
                pendingQuestion = null
                if (retry != null) sendQuestion(retry.first, retry.second)
            }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = institutionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MintBadgeBg)
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Asisten AI",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MintBadgeText
                        )
                    }

                    if (messages.size > 1) {
                        TextButton(
                            onClick = ::finishConversation,
                            enabled = !isProcessing && !validationPending,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "Selesai",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Tulis pertanyaan...") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        IconButton(
                            onClick = { sendQuestion(inputText) },
                            enabled = inputText.isNotBlank() && !isProcessing && savedConversationId == null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (inputText.isNotBlank() && !isProcessing) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Kirim pesan",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Pintasan Beralih Metode Input (sesuai mockup)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SIBI",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (inputMethod == "gesture") FontWeight.Bold else FontWeight.Normal,
                            color = if (inputMethod == "gesture") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onSwitchToSibi() }
                        )
                        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "Suara",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (inputMethod == "speech") FontWeight.Bold else FontWeight.Normal,
                            color = if (inputMethod == "speech") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onSwitchToSpeech() }
                        )
                        Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "Teks",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (inputMethod == "text") FontWeight.Bold else FontWeight.Normal,
                            color = if (inputMethod == "text") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubbleItem(
                    message = msg,
                    onRelatedQuestionClick = { related -> sendQuestion(related) }
                )
            }

            if (isProcessing || validationPending) {
                item {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = if (validationPending) "Menunggu validasi petugas..." else "Sedang memproses...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: UiChatMessage,
    onRelatedQuestionClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        if (message.isUser) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                    color = Color.White
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(0.92f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (message.title != null) {
                        Text(
                            text = message.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    if (message.sourceLabel != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(BlueHeroCard)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = message.sourceLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (message.relatedQuestion != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Pertanyaan terkait",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    onClick = { onRelatedQuestionClick(message.relatedQuestion) },
                    shape = RoundedCornerShape(50),
                    color = BlueHeroCard
                ) {
                    Text(
                        text = message.relatedQuestion,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionErrorScreen(
    message: String,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Percakapan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEF3C7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PriorityHigh,
                        contentDescription = null,
                        tint = Color(0xFFB45309),
                        modifier = Modifier.size(56.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "Pesan belum berhasil diproses",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onRetryClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Coba kirim lagi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                FilledTonalButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = BlueHeroCard,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Kembali",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
