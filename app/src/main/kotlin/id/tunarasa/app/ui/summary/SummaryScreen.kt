package id.tunarasa.app.ui.summary

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tunarasa.app.data.session.ActiveConversationCache
import id.tunarasa.app.ui.theme.BlueHeroCard
import id.tunarasa.app.ui.theme.MintBadgeBg
import id.tunarasa.app.ui.theme.MintBadgeText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class UiSummaryNote(
    val noteId: String,
    val institutionName: String,
    val createdAt: String,
    val expiresAt: String,
    val accessToken: String,
    val content: String
)

@Composable
fun SummaryScreen(
    token: String,
    onBackToHome: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var noteState by remember { mutableStateOf<UiSummaryNote?>(null) }

    LaunchedEffect(token) {
        isLoading = true
        delay(450) // Simulasi pengambilan GET /api/v1/summary/note/{token}
        noteState = buildSummaryFromSession(token)
        isLoading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    IconButton(onClick = onBackToHome) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali ke beranda",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "Ringkasan percakapan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MintBadgeBg)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MintBadgeText,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Berhasil dimuat",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MintBadgeText
                        )
                    }
                }
            }
        },
        bottomBar = {
            val currentNote = noteState
            if (!isLoading && currentNote != null) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val pdfFile = SummaryPdfHelper.createSummaryPdfFile(
                                        context = context,
                                        token = currentNote.accessToken,
                                        institutionName = currentNote.institutionName,
                                        createdAt = currentNote.createdAt,
                                        content = currentNote.content
                                    )
                                    SummaryPdfHelper.sharePdf(context, pdfFile)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Bagikan", fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    val pdfFile = SummaryPdfHelper.createSummaryPdfFile(
                                        context = context,
                                        token = currentNote.accessToken,
                                        institutionName = currentNote.institutionName,
                                        createdAt = currentNote.createdAt,
                                        content = currentNote.content
                                    )
                                    val saved = SummaryPdfHelper.saveToDownloads(context, pdfFile)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (saved) "PDF tersimpan di folder Downloads"
                                            else "Gagal menyimpan PDF"
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Unduh PDF", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        TextButton(
                            onClick = onBackToHome,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Selesai & kembali ke beranda")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Memuat ringkasan percakapan...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        val note = noteState ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Catatan layanan\n${note.institutionName}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )

            // Metadata Kartu (ID Catatan & Waktu Dibuat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BlueHeroCard)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "ID Catatan",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "#${note.noteId}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MintBadgeBg)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MintBadgeText,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Dibuat",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MintBadgeText
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = note.createdAt,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            // Isi Ringkasan Percakapan
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Isi ringkasan",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Informasi Token & Masa Berlaku 7 Hari (sesuai kontrak BE-2)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BlueHeroCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Token akses: ${note.accessToken}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Token Ringkasan", note.accessToken))
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Token disalin ke papan klip")
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Salin token ringkasan",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Berlaku hingga ${note.expiresAt} (7 hari). Simpan token atau unduh PDF untuk ditunjukkan ke petugas loket.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun buildSummaryFromSession(token: String): UiSummaryNote {
    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
    val now = Date()
    val expiryCal = Calendar.getInstance().apply {
        time = now
        add(Calendar.DAY_OF_YEAR, 7)
    }

    val cachedMessages = ActiveConversationCache.messages.filter { it.id != "welcome-msg" }
    val summaryBody = if (cachedMessages.isNotEmpty()) {
        cachedMessages.joinToString("\n\n") { msg ->
            if (msg.isUser) "Pertanyaan Pengguna:\n${msg.content}"
            else "Jawaban Asisten:\n${msg.content}"
        }
    } else {
        "Sesi komunikasi dengan ${ActiveConversationCache.institutionName} telah selesai. Unduh dokumen PDF untuk menyimpan catatan sesi Anda."
    }

    return UiSummaryNote(
        noteId = token.takeLast(6).uppercase(),
        institutionName = ActiveConversationCache.institutionName,
        createdAt = formatter.format(now),
        expiresAt = formatter.format(expiryCal.time),
        accessToken = token,
        content = summaryBody
    )
}
