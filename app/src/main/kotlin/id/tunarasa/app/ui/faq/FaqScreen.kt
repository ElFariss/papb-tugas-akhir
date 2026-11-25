package id.tunarasa.app.ui.faq

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
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

data class FaqItem(
    val id: String,
    val question: String,
    val quickAnswer: String? = null,
    val isPopular: Boolean = false
)

// Data dimigrasi dari frontend/src/app/layanan/page.tsx
// Nantinya akan diganti/ditambah dari endpoint BE-2 (/api/v1/qa/*) via :contracts
private fun getDefaultFaqsForInstitution(slug: String): List<FaqItem> {
    return when (slug.lowercase()) {
        "dukcapil" -> listOf(
            FaqItem(
                id = "kk-baru",
                question = "Pembentukan kartu keluarga baru",
                quickAnswer = "Layanan ini diberikan kepada warga yang baru membentuk keluarga dan perlu membuat Kartu Keluarga (KK) baru. Persyaratannya termasuk fotokopi buku nikah atau kutipan akta perkawinan serta KK lama.",
                isPopular = true
            ),
            FaqItem(
                id = "penerbitan-kk",
                question = "Penerbitan kartu keluarga"
            ),
            FaqItem(
                id = "prosedur-ktp",
                question = "Prosedur penerbitan KTP-El baru"
            ),
            FaqItem(
                id = "syarat-ktp",
                question = "Syarat pembuatan KTP-El baru"
            ),
            FaqItem(
                id = "akta-kelahiran",
                question = "Pengajuan pembuatan akta kelahiran"
            )
        )
        else -> listOf(
            FaqItem(
                id = "umum-1",
                question = "Jam operasional dan jadwal pelayanan",
                isPopular = true
            ),
            FaqItem(
                id = "umum-2",
                question = "Dokumen persyaratan yang perlu dibawa"
            ),
            FaqItem(
                id = "umum-3",
                question = "Alur pendaftaran antrean layanan"
            )
        )
    }
}

@Composable
fun FaqScreen(
    institutionSlug: String = "dukcapil",
    institutionName: String = "Dinas Kependudukan",
    onBackClick: () -> Unit = {},
    onSelectQuestion: (String) -> Unit = {},
    onAskOtherClick: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var expandedFaqId by remember { mutableStateOf<String?>(null) }

    val allFaqs = remember(institutionSlug) {
        getDefaultFaqsForInstitution(institutionSlug)
    }

    val filteredFaqs = remember(searchQuery, allFaqs) {
        if (searchQuery.isBlank()) allFaqs
        else allFaqs.filter {
            it.question.contains(searchQuery, ignoreCase = true) ||
                (it.quickAnswer?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    val displayTitle = institutionName.ifBlank {
        institutionSlug.replaceFirstChar { it.uppercase() }
    }

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
                        contentDescription = "Kembali ke pilihan institusi",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = displayTitle,
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
                    onClick = onAskOtherClick,
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
                        text = "Tanyakan pertanyaan lain  →",
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
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Apa yang ingin\nkamu ketahui?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari topik layanan") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Pertanyaan populer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredFaqs, key = { it.id }) { faq ->
                    FaqQuestionCard(
                        faq = faq,
                        isExpanded = expandedFaqId == faq.id,
                        onToggleExpand = {
                            expandedFaqId = if (expandedFaqId == faq.id) null else faq.id
                        },
                        onSelectQuestion = { onSelectQuestion(faq.question) }
                    )
                }

                if (filteredFaqs.isEmpty()) {
                    item {
                        Text(
                            text = "Topik tidak ditemukan. Tekan tombol di bawah untuk menanyakan langsung ke asisten.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FaqQuestionCard(
    faq: FaqItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectQuestion: () -> Unit
) {
    Card(
        onClick = onSelectQuestion,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (faq.isPopular) {
                        Text(
                            text = "★ Terpopuler",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = faq.question,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Tanyakan ${faq.question}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Jika pertanyaan punya ringkasan jawaban dari Tunarasa Bot (seperti di layanan/page.tsx)
            if (faq.quickAnswer != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleExpand() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isExpanded) "Sembunyikan ringkasan" else "Lihat ringkasan jawaban",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = isExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BlueHeroCard)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = faq.quickAnswer,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}
