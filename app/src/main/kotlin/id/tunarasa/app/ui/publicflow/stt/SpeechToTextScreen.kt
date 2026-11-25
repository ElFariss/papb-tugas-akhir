package id.tunarasa.app.ui.publicflow.stt

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import id.tunarasa.ai.speech.SpeechError
import id.tunarasa.ai.speech.SpeechStatus
import id.tunarasa.app.ui.theme.BlueHeroCard
import id.tunarasa.app.ui.theme.MintBadgeBg
import id.tunarasa.app.ui.theme.MintBadgeText

@Composable
fun SpeechToTextScreen(
    onBackClick: () -> Unit,
    onUseTextClick: (String) -> Unit,
    vm: SpeechToTextViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by vm.engine.state.collectAsState()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            vm.engine.start()
        }
    }

    LaunchedEffect(Unit) {
        if (hasAudioPermission) {
            vm.engine.start()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            vm.stopListening()
        }
    }

    // Skala lingkaran luar mengikuti volume suara (rmsDb)
    val normalizedLevel = ((state.rmsDb + 2f) / 12f).coerceIn(0f, 1f)
    val pulseScale by animateFloatAsState(
        targetValue = if (state.isActive) 1f + (normalizedLevel * 0.18f) else 1f,
        label = "micPulse"
    )

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
                IconButton(onClick = {
                    vm.stopListening()
                    onBackClick()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Suara ke teks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Button(
                    onClick = {
                        vm.stopListening()
                        onUseTextClick(state.transcript.trim())
                    },
                    enabled = state.transcript.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Gunakan teks ini  →",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Bicara, kami tuliskan.",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() }
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Letakkan ponsel dekat pembicara.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tombol Mikrofon Besar di Tengah
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(164.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(BlueHeroCard),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .semantics {
                                contentDescription = if (state.isActive) "Hentikan rekaman suara" else "Mulai rekaman suara"
                            }
                            .clickable {
                                if (!hasAudioPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    vm.toggleListening()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (state.isActive) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // Status Mendengarkan & Kartu Hasil Transkripsi
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                val statusText = when {
                    !hasAudioPermission -> "● Izin mikrofon diperlukan"
                    state.status == SpeechStatus.LISTENING || state.status == SpeechStatus.STARTING -> "● Sedang mendengarkan"
                    state.status == SpeechStatus.STOPPING -> "● Memproses suara..."
                    state.error != null -> "● ${mapSpeechError(state.error)}"
                    else -> "● Ketuk mikrofon untuk bicara"
                }
                val isError = !hasAudioPermission || state.error != null

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isError) Color(0xFFFEE2E2) else MintBadgeBg)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isError) MaterialTheme.colorScheme.error else MintBadgeText
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Hasil transkripsi",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.transcript.isNotBlank()) {
                                IconButton(
                                    onClick = { vm.clearTranscript() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Hapus transkripsi",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = state.transcript.ifBlank { "Belum ada suara yang terekam..." },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (state.transcript.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (state.transcript.isNotBlank()) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun mapSpeechError(error: SpeechError?): String = when (error) {
    SpeechError.PERMISSION_DENIED -> "Izin mikrofon ditolak"
    SpeechError.NETWORK -> "Butuh koneksi untuk pengenalan suara"
    SpeechError.LANGUAGE_UNAVAILABLE -> "Bahasa Indonesia tidak tersedia di perangkat"
    SpeechError.NOT_AVAILABLE -> "Layanan suara tidak tersedia di HP ini"
    SpeechError.AUDIO -> "Gangguan pada mikrofon"
    SpeechError.SERVICE -> "Layanan pengenalan suara sibuk, coba lagi"
    null -> ""
}
