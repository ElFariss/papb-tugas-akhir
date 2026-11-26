package id.tunarasa.app.ui.about

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tunarasa.app.ui.theme.BlueHeroCard
import id.tunarasa.app.ui.theme.MintBadgeBg
import id.tunarasa.app.ui.theme.MintBadgeText

private data class MissionItem(val number: String, val title: String, val description: String)
private data class StatItem(val value: String, val label: String, val accentColor: Color)
private data class TechItem(val name: String, val description: String)
private data class TechCategory(val category: String, val items: List<TechItem>)

private val missionItems = listOf(
    MissionItem(
        number = "1",
        title = "Aksesibilitas Universal",
        description = "Memastikan setiap individu memiliki akses yang sama terhadap layanan publik digital."
    ),
    MissionItem(
        number = "2",
        title = "Teknologi Inklusif",
        description = "Mengembangkan solusi teknologi yang mendukung komunikasi tanpa hambatan."
    ),
    MissionItem(
        number = "3",
        title = "Pemberdayaan Komunitas",
        description = "Memberdayakan komunitas penyandang disabilitas melalui teknologi yang mudah digunakan."
    )
)

private val performanceStats = listOf(
    StatItem(">85%", "Akurasi Gestur", Color(0xFF15803D)),
    StatItem("<2 dtk", "Waktu Respons", Color(0xFF1D4ED8)),
    StatItem("26", "Huruf SIBI", Color(0xFFEA580C))
)

private val techCategories = listOf(
    TechCategory(
        category = "Aplikasi Mobile Android",
        items = listOf(
            TechItem("Kotlin & Jetpack Compose", "Antarmuka deklaratif native Android yang responsif dan ringan"),
            TechItem("Material 3 & TalkBack", "Desain kontras tinggi dengan dukungan pembaca layar bawaan"),
            TechItem("CameraX & DataStore", "Akses kamera stabil serta penyimpanan sesi lokal secara aman")
        )
    ),
    TechCategory(
        category = "AI & Machine Learning",
        items = listOf(
            TechItem("MediaPipe Hand Landmarker", "Pelacakan titik tangan real-time langsung di perangkat (on-device)"),
            TechItem("On-Device SIBI Classifier", "Pengenalan alfabet SIBI tanpa mengirim gambar kamera ke server"),
            TechItem("LLM & RAG Pipeline", "Sistem tanya jawab cerdas berbasis dokumen resmi institusi")
        )
    ),
    TechCategory(
        category = "Infrastruktur Backend",
        items = listOf(
            TechItem("Ktor Server (Kotlin)", "Layanan API asinkron berperforma tinggi untuk jalur publik dan admin"),
            TechItem("PostgreSQL", "Basis data relasional untuk riwayat percakapan, FAQ, dan catatan"),
            TechItem("Pinecone Vector DB", "Pencarian semantik berbasis namespace per institusi layanan")
        )
    ),
    TechCategory(
        category = "Fitur Aksesibilitas",
        items = listOf(
            TechItem("Dukungan SIBI", "Sistem Isyarat Bahasa Indonesia untuk komunikasi visual real-time"),
            TechItem("Suara ke Teks", "Transkripsi ucapan petugas loket secara langsung ke layar"),
            TechItem("Standar WCAG 2.1 AA", "Target sentuh besar, label semantik lengkap, dan alur tanpa suara")
        )
    )
)

@Composable
fun AboutScreen(
    onBack: () -> Unit
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
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Tentang TunaRasa",
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
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Hero Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1D4ED8), Color(0xFF1E3A8A))
                        )
                    )
                    .padding(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.16f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Platform Komunikasi Inklusif",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }

                    Text(
                        text = "Tentang TunaRasa",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.semantics { heading() }
                    )

                    Text(
                        text = "Platform komunikasi inklusif berbasis AI untuk menciptakan aksesibilitas yang setara bagi penyandang Tuli dan teman wicara di layanan publik.",
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = Color(0xFFDBEAFE)
                    )
                }
            }

            // 2. Performa Sistem
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Performa sistem",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    performanceStats.forEach { stat ->
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stat.value,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = stat.accentColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stat.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // 3. Misi Kami
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Misi kami",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() }
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        missionItems.forEach { mission ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mission.number,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mission.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = mission.description,
                                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Teknologi yang Digunakan
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Teknologi yang digunakan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() }
                )

                techCategories.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = group.category,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        group.items.forEach { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = BlueHeroCard)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Tim Pengembang & Lisensi
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Tunarasa Development Team",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = "Tim multidisiplin yang berkomitmen menciptakan layanan publik digital yang inklusif bagi semua.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TeamBadge(value = "2025", label = "Peluncuran", color = MaterialTheme.colorScheme.primary)
                        TeamBadge(value = "AGPL-3.0", label = "Lisensi", color = MintBadgeText)
                        TeamBadge(value = "Indonesia", label = "Dikembangkan", color = Color(0xFFDC2626))
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamBadge(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
