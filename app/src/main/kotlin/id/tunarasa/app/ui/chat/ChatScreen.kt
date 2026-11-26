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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tunarasa.app.ui.theme.BlueHeroCard
import id.tunarasa.app.ui.theme.MintBadgeBg
import id.tunarasa.app.ui.theme.MintBadgeText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class UiChatMessage(
    val id: String,
    val isUser: Boolean,
    val title: String? = null,
    val content: String,
    val sourceLabel: String? = null,
    val relatedQuestion: String? = null
)

@Composable
fun ChatScreen(
    institutionSlug: String = "dukcapil",
    initialQuestion: String = "",
    inputMethod: String = "text",
    onBackClick: () -> Unit = {},
    onSwitchToSibi: () -> Unit = {},
    onSwitchToSpeech: () -> Unit = {},
    onFinishConversation: (String) -> Unit = {}
) {
    val institutionTitle = remember(institutionSlug) {
        institutionSlug.replaceFirstChar { it.uppercase() }
    }

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
    var isConnectionError by remember { mutableStateOf(false) }
    var pendingFailedMessage by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    fun sendQuestion(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank() || isProcessing) return

        // Simulasi uji error koneksi jika user mengetik kata kunci "offline"
        if (cleanText.equals("offline", ignoreCase = true)) {
            pendingFailedMessage = cleanText
            isConnectionError = true
            return
        }

        messages.add(
            UiChatMessage(
                id = "user-${System.currentTimeMillis()}",
                isUser = true,
                content = cleanText
            )
        )
        inputText = ""
        isProcessing = true

        coroutineScope.launch {
            delay(700) // Simulasi waktu respons RAG/LLM sebelum BE aktif
            val reply = buildMockAssistantReply(cleanText, institutionTitle)
            messages.add(reply)
            isProcessing = false
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Kirim otomatis jika ada pertanyaan bawaan dari layar FAQ / SIBI / Suara
    LaunchedEffect(initialQuestion) {
        if (initialQuestion.isNotBlank() && messages.size == 1) {
            sendQuestion(initialQuestion)
        }
    }

    if (isConnectionError) {
        ConnectionErrorScreen(
            onBackClick = { isConnectionError = false },
            onRetryClick = {
                isConnectionError = false
                if (pendingFailedMessage.isNotBlank()) {
                    val retryText = pendingFailedMessage
                    pendingFailedMessage = ""
                    sendQuestion(retryText)
                }
            }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        color = MaterialTheme.colorScheme.onBackground
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
                            onClick = {
                                id.tunarasa.app.data.session.ActiveConversationCache.update(institutionTitle, messages)
                                onFinishConversation("note-${System.currentTimeMillis()}")
                            },
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
                            enabled = inputText.isNotBlank() && !isProcessing,
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

            if (isProcessing) {
                item {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Asisten sedang mengetik...",
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
                    text = "Koneksi terputus",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Pesan belum terkirim. Pastikan jaringan internet tersedia. Teks yang diketik tetap tersimpan.",
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

private fun buildMockAssistantReply(question: String, institution: String): UiChatMessage {
    val q = question.lowercase()
    return when {
        q.contains("ktp") -> UiChatMessage(
            id = "bot-${System.currentTimeMillis()}",
            isUser = false,
            title = "Syarat pembuatan KTP",
            content = "Untuk pengajuan KTP-el baru, siapkan dokumen kependudukan sesuai ketentuan $institution. Periksa persyaratan resmi sebelum datang.",
            sourceLabel = "Sumber dokumen institusi",
            relatedQuestion = "Bagaimana jadwal pelayanan?"
        )
        q.contains("kk") || q.contains("kartu keluarga") -> UiChatMessage(
            id = "bot-${System.currentTimeMillis()}",
            isUser = false,
            title = "Penerbitan Kartu Keluarga",
            content = "Siapkan fotokopi buku nikah atau kutipan akta perkawinan, KK lama, serta surat pengantar sesuai domisili.",
            sourceLabel = "Sumber dokumen institusi",
            relatedQuestion = "Berapa lama proses penerbitan KK?"
        )
        else -> UiChatMessage(
            id = "bot-${System.currentTimeMillis()}",
            isUser = false,
            title = "Informasi layanan $institution",
            content = "Pertanyaan Anda mengenai \"$question\" telah dicatat. Silakan bawa identitas diri (KTP/KK) saat berkunjung ke loket pelayanan.",
            sourceLabel = "Sumber dokumen institusi",
            relatedQuestion = "Syarat pembuatan KTP elektronik"
        )
    }
}
