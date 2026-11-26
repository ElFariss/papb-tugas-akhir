package id.tunarasa.app.ui.admin

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import id.tunarasa.app.data.InstitutionResponse
import id.tunarasa.app.data.PlatformApi
import id.tunarasa.app.data.RagFileDto
import id.tunarasa.app.data.UserResponse
import id.tunarasa.app.data.session.SessionStore
import id.tunarasa.contracts.RagProcessingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_RAG_FILE_BYTES = 10L * 1024L * 1024L // 10 MB

private data class PickedRagDocument(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val bytes: ByteArray,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstitutionAdminScreen(
    platformApi: PlatformApi? = null,
    onBackClick: () -> Unit = {},
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
    var institutions by remember { mutableStateOf<List<InstitutionResponse>>(emptyList()) }
    var selectedInstitutionId by remember { mutableStateOf<Int?>(null) }
    var ragFiles by remember { mutableStateOf<List<RagFileDto>>(emptyList()) }

    var searchQuery by remember { mutableStateOf("") }
    var isLoadingInstitutions by remember { mutableStateOf(true) }
    var isLoadingRagFiles by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var isBannerError by remember { mutableStateOf(false) }

    // State Dialog Tambah / Edit Institusi
    var showInstitutionDialog by remember { mutableStateOf(false) }
    var editingInstitution by remember { mutableStateOf<InstitutionResponse?>(null) }
    var instNameInput by remember { mutableStateOf("") }
    var instSlugInput by remember { mutableStateOf("") }
    var initialRagDoc by remember { mutableStateOf<PickedRagDocument?>(null) }
    var isSavingInstitution by remember { mutableStateOf(false) }

    // State Dialog Unggah / Ganti File RAG
    var showRagUploadDialog by remember { mutableStateOf(false) }
    var replacingRagFile by remember { mutableStateOf<RagFileDto?>(null) }
    var pickedRagDoc by remember { mutableStateOf<PickedRagDocument?>(null) }
    var isUploadingRag by remember { mutableStateOf(false) }

    // State Dialog Konfirmasi Hapus File RAG
    var deletingRagFile by remember { mutableStateOf<RagFileDto?>(null) }
    var isDeletingRag by remember { mutableStateOf(false) }

    val isSuperAdmin = (currentUser?.role ?: "super_admin") == "super_admin"

    val selectedInstitution = remember(institutions, selectedInstitutionId) {
        institutions.find { it.id == selectedInstitutionId }
    }

    val filteredInstitutions = remember(institutions, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            institutions
        } else {
            institutions.filter { inst ->
                inst.name.lowercase().contains(query) ||
                    inst.slug.lowercase().contains(query)
            }
        }
    }

    // Launcher dokumen untuk file RAG awal saat membuat institusi
    val initialRagPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                when (val readResult = readPickedDocument(context, uri)) {
                    is DocumentReadResult.Success -> {
                        initialRagDoc = readResult.document
                    }
                    is DocumentReadResult.Error -> {
                        isBannerError = true
                        bannerMessage = readResult.reason
                    }
                }
            }
        }
    }

    // Launcher dokumen untuk unggah / ganti file RAG pada institusi terpilih
    val ragFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                when (val readResult = readPickedDocument(context, uri)) {
                    is DocumentReadResult.Success -> {
                        pickedRagDoc = readResult.document
                    }
                    is DocumentReadResult.Error -> {
                        isBannerError = true
                        bannerMessage = readResult.reason
                    }
                }
            }
        }
    }

    fun loadRagFilesForInstitution(institutionId: Int) {
        scope.launch {
            isLoadingRagFiles = true
            when (val res = api.getRagFiles(institutionId)) {
                is ApiResult.Success -> {
                    ragFiles = res.data
                }
                is ApiResult.Error -> {
                    ragFiles = emptyList()
                    isBannerError = true
                    bannerMessage = "Gagal memuat file RAG: ${res.message}"
                }
            }
            isLoadingRagFiles = false
        }
    }

    fun loadInstitutionsData() {
        scope.launch {
            isLoadingInstitutions = true
            loadError = null

            when (val meRes = api.getCurrentUser()) {
                is ApiResult.Success -> currentUser = meRes.data
                is ApiResult.Error -> Unit
            }

            when (val instRes = api.getInstitutions()) {
                is ApiResult.Success -> {
                    institutions = instRes.data
                    val targetId = selectedInstitutionId?.takeIf { id ->
                        instRes.data.any { it.id == id }
                    } ?: instRes.data.firstOrNull()?.id

                    selectedInstitutionId = targetId
                    if (targetId != null) {
                        loadRagFilesForInstitution(targetId)
                    } else {
                        ragFiles = emptyList()
                    }
                }
                is ApiResult.Error -> {
                    loadError = instRes.message
                }
            }

            isLoadingInstitutions = false
        }
    }

    fun openCreateInstitutionDialog() {
        editingInstitution = null
        instNameInput = ""
        instSlugInput = ""
        initialRagDoc = null
        showInstitutionDialog = true
    }

    fun openEditInstitutionDialog(institution: InstitutionResponse) {
        editingInstitution = institution
        instNameInput = institution.name
        instSlugInput = institution.slug
        initialRagDoc = null
        showInstitutionDialog = true
    }

    fun submitInstitutionForm() {
        val trimmedName = instNameInput.trim()
        val trimmedSlug = instSlugInput.trim()
        if (trimmedName.isEmpty() || trimmedSlug.isEmpty() || isSavingInstitution) return

        scope.launch {
            isSavingInstitution = true
            bannerMessage = null

            val existing = editingInstitution
            if (existing == null) {
                val createRes = api.createInstitution(
                    name = trimmedName,
                    slug = trimmedSlug,
                )

                when (createRes) {
                    is ApiResult.Success -> {
                        val created = createRes.data
                        val attachedDoc = initialRagDoc
                        if (attachedDoc != null) {
                            val uploadRes = api.uploadRagFile(
                                institutionId = created.id,
                                fileName = attachedDoc.fileName,
                                mimeType = attachedDoc.mimeType,
                                fileBytes = attachedDoc.bytes,
                            )
                            when (uploadRes) {
                                is ApiResult.Success -> {
                                    isBannerError = false
                                    bannerMessage = "Institusi \"${created.name}\" & file RAG berhasil dibuat."
                                }
                                is ApiResult.Error -> {
                                    isBannerError = true
                                    bannerMessage = "Institusi dibuat, tetapi unggah RAG gagal: ${uploadRes.message}"
                                }
                            }
                        } else {
                            isBannerError = false
                            bannerMessage = "Institusi \"${created.name}\" berhasil dibuat."
                        }
                        selectedInstitutionId = created.id
                        showInstitutionDialog = false
                        loadInstitutionsData()
                    }
                    is ApiResult.Error -> {
                        isBannerError = true
                        bannerMessage = "Gagal membuat institusi: ${createRes.message}"
                    }
                }
            } else {
                val updateRes = api.updateInstitution(
                    institutionId = existing.id,
                    name = trimmedName,
                )

                when (updateRes) {
                    is ApiResult.Success -> {
                        isBannerError = false
                        bannerMessage = "Detail institusi \"$trimmedName\" diperbarui."
                        showInstitutionDialog = false
                        loadInstitutionsData()
                    }
                    is ApiResult.Error -> {
                        isBannerError = true
                        bannerMessage = "Gagal memperbarui institusi: ${updateRes.message}"
                    }
                }
            }

            isSavingInstitution = false
        }
    }

    fun submitRagFileUpload() {
        val targetInstitution = selectedInstitution ?: return
        val doc = pickedRagDoc ?: return
        if (isUploadingRag) return

        scope.launch {
            isUploadingRag = true
            bannerMessage = null

            val uploadRes = api.uploadRagFile(
                institutionId = targetInstitution.id,
                fileName = doc.fileName,
                mimeType = doc.mimeType,
                fileBytes = doc.bytes,
            )

            when (uploadRes) {
                is ApiResult.Success -> {
                    val oldFile = replacingRagFile
                    if (oldFile != null) {
                        api.deleteRagFile(targetInstitution.id, oldFile.id)
                        isBannerError = false
                        bannerMessage = "File RAG diganti dengan \"${doc.fileName}\"."
                    } else {
                        isBannerError = false
                        bannerMessage = "File \"${doc.fileName}\" berhasil diunggah ke RAG."
                    }
                    showRagUploadDialog = false
                    replacingRagFile = null
                    pickedRagDoc = null
                    loadRagFilesForInstitution(targetInstitution.id)
                }
                is ApiResult.Error -> {
                    isBannerError = true
                    bannerMessage = "Gagal mengunggah file RAG: ${uploadRes.message}"
                }
            }

            isUploadingRag = false
        }
    }

    fun confirmDeleteRagFile() {
        val targetInstitution = selectedInstitution ?: return
        val targetFile = deletingRagFile ?: return
        if (isDeletingRag) return

        scope.launch {
            isDeletingRag = true
            when (val res = api.deleteRagFile(targetInstitution.id, targetFile.id)) {
                is ApiResult.Success -> {
                    isBannerError = false
                    bannerMessage = "File RAG \"${targetFile.fileName}\" dihapus."
                    deletingRagFile = null
                    loadRagFilesForInstitution(targetInstitution.id)
                }
                is ApiResult.Error -> {
                    isBannerError = true
                    bannerMessage = "Gagal menghapus file RAG: ${res.message}"
                }
            }
            isDeletingRag = false
        }
    }

    LaunchedEffect(Unit) {
        loadInstitutionsData()
    }

    // Dialog Tambah / Edit Institusi
    if (showInstitutionDialog) {
        val isEditing = editingInstitution != null
        AlertDialog(
            onDismissRequest = { if (!isSavingInstitution) showInstitutionDialog = false },
            title = {
                Text(
                    text = if (isEditing) "Edit Institusi" else "Tambah Institusi Baru",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = instNameInput,
                        onValueChange = { newValue ->
                            instNameInput = newValue
                            if (!isEditing) {
                                instSlugInput = slugifyInstitutionName(newValue)
                            }
                        },
                        label = { Text("Nama Institusi") },
                        placeholder = { Text("cth. Disdukcapil Kota Surabaya") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (!isEditing) {
                        OutlinedTextField(
                            value = instSlugInput,
                            onValueChange = { instSlugInput = slugifyInstitutionName(it) },
                            label = { Text("Slug URL") },
                            placeholder = { Text("cth. disdukcapil-surabaya") },
                            supportingText = {
                                Text("Rute publik: /komunikasi/${instSlugInput.ifBlank { "slug" }}")
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        HorizontalDivider()
                        Text(
                            text = "Dokumen Knowledge Base RAG (Opsional)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )

                        OutlinedButton(
                            onClick = {
                                initialRagPickerLauncher.launch(
                                    arrayOf("application/pdf", "text/plain")
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = initialRagDoc?.fileName ?: "Pilih File PDF / TXT (Maks 10MB)",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (initialRagDoc != null) {
                            Text(
                                text = "Ukuran: ${formatFileSizeKb(initialRagDoc!!.sizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF15803D),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { submitInstitutionForm() },
                    enabled = instNameInput.trim().isNotEmpty() &&
                        instSlugInput.trim().isNotEmpty() &&
                        !isSavingInstitution,
                ) {
                    if (isSavingInstitution) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isEditing) "Simpan" else "Buat Institusi")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showInstitutionDialog = false },
                    enabled = !isSavingInstitution,
                ) {
                    Text("Batal")
                }
            },
        )
    }

    // Dialog Unggah / Ganti File RAG
    if (showRagUploadDialog && selectedInstitution != null) {
        val isReplacing = replacingRagFile != null
        AlertDialog(
            onDismissRequest = { if (!isUploadingRag) showRagUploadDialog = false },
            title = {
                Text(
                    text = if (isReplacing) "Ganti File RAG" else "Unggah File RAG",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (isReplacing) {
                            "Ganti dokumen \"${replacingRagFile?.fileName}\" pada ${selectedInstitution.name} dengan versi terbaru."
                        } else {
                            "Tambahkan dokumen panduan (PDF/TXT, maks 10 MB) untuk ${selectedInstitution.name}."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedButton(
                        onClick = {
                            ragFilePickerLauncher.launch(
                                arrayOf("application/pdf", "text/plain")
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = pickedRagDoc?.fileName ?: "Pilih Dokumen PDF / TXT",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (pickedRagDoc != null) {
                        Text(
                            text = "Terpilih: ${pickedRagDoc!!.fileName} (${formatFileSizeKb(pickedRagDoc!!.sizeBytes)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF15803D),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { submitRagFileUpload() },
                    enabled = pickedRagDoc != null && !isUploadingRag,
                ) {
                    if (isUploadingRag) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isReplacing) "Ganti File" else "Unggah")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRagUploadDialog = false },
                    enabled = !isUploadingRag,
                ) {
                    Text("Batal")
                }
            },
        )
    }

    // Dialog Konfirmasi Hapus File RAG
    if (deletingRagFile != null) {
        val targetFile = deletingRagFile!!
        AlertDialog(
            onDismissRequest = { if (!isDeletingRag) deletingRagFile = null },
            title = { Text("Hapus File RAG?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Dokumen \"${targetFile.fileName}\" akan dihapus dari knowledge base institusi ini.")
            },
            confirmButton = {
                Button(
                    onClick = { confirmDeleteRagFile() },
                    enabled = !isDeletingRag,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(if (isDeletingRag) "Menghapus..." else "Hapus")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingRagFile = null },
                    enabled = !isDeletingRag,
                ) {
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
                            text = "Institusi & File RAG",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Kelola instansi & knowledge base AI",
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
                        onClick = { loadInstitutionsData() },
                        enabled = !isLoadingInstitutions,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Muat ulang institusi",
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
            // Banner Notifikasi
            if (!bannerMessage.isNullOrBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isBannerError) Color(0xFFFEE2E2) else Color(0xFFDCFCE7),
                        contentColor = if (isBannerError) Color(0xFF991B1B) else Color(0xFF166534),
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

            // Statistik Ringkas (3 Kartu)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InstitutionStatChip(
                        label = "Institusi",
                        value = institutions.size.toString(),
                        accentColor = Color(0xFF2563EB),
                        modifier = Modifier.weight(1f),
                    )
                    InstitutionStatChip(
                        label = "File RAG",
                        value = ragFiles.size.toString(),
                        accentColor = Color(0xFFD97706),
                        modifier = Modifier.weight(1f),
                    )
                    InstitutionStatChip(
                        label = "RAG Selesai",
                        value = ragFiles.count {
                            it.processingStatus.equals(RagProcessingStatus.COMPLETED.wire, ignoreCase = true)
                        }.toString(),
                        accentColor = Color(0xFF16A34A),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Pencarian & Tombol Tambah Institusi
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Daftar Institusi (${filteredInstitutions.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() },
                        )

                        if (isSuperAdmin) {
                            Button(
                                onClick = { openCreateInstitutionDialog() },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tambah")
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari nama atau slug institusi...") },
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
                }
            }

            // Daftar Institusi
            if (isLoadingInstitutions) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (loadError != null && institutions.isEmpty()) {
                item {
                    ElevatedCard(
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                text = "Gagal Memuat Institusi",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = loadError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = { loadInstitutionsData() }) {
                                Text("Coba Lagi")
                            }
                        }
                    }
                }
            } else if (filteredInstitutions.isEmpty()) {
                item {
                    ElevatedCard(
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Business,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                text = "Belum ada institusi ditemukan",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Tambahkan institusi baru untuk mulai mengelola dokumen RAG.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(filteredInstitutions, key = { it.id }) { inst ->
                    val isSelected = inst.id == selectedInstitutionId
                    InstitutionAdminCard(
                        institution = inst,
                        isSelected = isSelected,
                        canEdit = isSuperAdmin,
                        onSelect = {
                            if (selectedInstitutionId != inst.id) {
                                selectedInstitutionId = inst.id
                                loadRagFilesForInstitution(inst.id)
                            }
                        },
                        onEditClick = { openEditInstitutionDialog(inst) },
                    )
                }
            }

            // Bagian File RAG untuk Institusi Terpilih
            if (selectedInstitution != null) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "File RAG Knowledge Base",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                text = selectedInstitution.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                replacingRagFile = null
                                pickedRagDoc = null
                                showRagUploadDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.UploadFile,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Unggah File")
                        }
                    }
                }

                if (isLoadingRagFiles) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (ragFiles.isEmpty()) {
                    item {
                        ElevatedCard(
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp),
                                )
                                Text(
                                    text = "Belum ada dokumen RAG",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Unggah file PDF atau TXT agar chatbot dapat menjawab pertanyaan seputar institusi ini.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(ragFiles, key = { it.id }) { file ->
                        RagFileItemCard(
                            ragFile = file,
                            onReplaceClick = {
                                replacingRagFile = file
                                pickedRagDoc = null
                                showRagUploadDialog = true
                            },
                            onDeleteClick = {
                                deletingRagFile = file
                            },
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun InstitutionStatChip(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InstitutionAdminCard(
    institution: InstitutionResponse,
    isSelected: Boolean,
    canEdit: Boolean,
    onSelect: () -> Unit,
    onEditClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        shadowElevation = if (isSelected) 2.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Business,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = institution.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "/${institution.slug}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0xFFDBEAFE),
                ) {
                    Text(
                        text = "ID #${institution.id}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1D4ED8),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }

                if (canEdit) {
                    IconButton(
                        onClick = onEditClick,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit ${institution.name}",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (isSelected) {
                Text(
                    text = "Terpilih • Menampilkan dokumen RAG di bawah",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RagFileItemCard(
    ragFile: RagFileDto,
    onReplaceClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val parsedStatus = remember(ragFile.processingStatus) {
        RagProcessingStatus.entries.find {
            it.wire.equals(ragFile.processingStatus, ignoreCase = true)
        } ?: RagProcessingStatus.PENDING
    }

    val (statusLabel, statusBg, statusFg) = when (parsedStatus) {
        RagProcessingStatus.PENDING -> Triple("Menunggu", Color(0xFFFEF3C7), Color(0xFFB45309))
        RagProcessingStatus.PROCESSING -> Triple("Memproses", Color(0xFFDBEAFE), Color(0xFF1D4ED8))
        RagProcessingStatus.COMPLETED -> Triple("Selesai", Color(0xFFDCFCE7), Color(0xFF15803D))
        RagProcessingStatus.FAILED -> Triple("Gagal", Color(0xFFFEE2E2), Color(0xFFB91C1C))
    }

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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(22.dp),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ragFile.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = ragFile.fileName.substringAfterLast('.', "FILE").uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (ragFile.fileSize != null && ragFile.fileSize > 0) {
                            Text(
                                text = "• ${formatFileSizeKb(ragFile.fileSize)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = statusBg,
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onReplaceClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ganti", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(
                    onClick = onDeleteClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hapus", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private sealed interface DocumentReadResult {
    data class Success(val document: PickedRagDocument) : DocumentReadResult
    data class Error(val reason: String) : DocumentReadResult
}

private suspend fun readPickedDocument(
    context: Context,
    uri: Uri,
): DocumentReadResult = withContext(Dispatchers.IO) {
    try {
        val resolver = context.contentResolver
        var displayName = "dokumen_rag.pdf"
        var reportedSize = -1L

        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx != -1) {
                    displayName = cursor.getString(nameIdx) ?: displayName
                }
                if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) {
                    reportedSize = cursor.getLong(sizeIdx)
                }
            }
        }

        val mimeType = resolver.getType(uri)
            ?: if (displayName.endsWith(".txt", ignoreCase = true)) "text/plain" else "application/pdf"

        val isAllowedType = mimeType == "application/pdf" ||
            mimeType == "text/plain" ||
            displayName.endsWith(".pdf", ignoreCase = true) ||
            displayName.endsWith(".txt", ignoreCase = true)

        if (!isAllowedType) {
            return@withContext DocumentReadResult.Error(
                "Tipe file tidak valid. Hanya dokumen PDF dan TXT yang diizinkan."
            )
        }

        if (reportedSize > MAX_RAG_FILE_BYTES) {
            return@withContext DocumentReadResult.Error(
                "Ukuran file melebihi batas maksimal 10 MB."
            )
        }

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@withContext DocumentReadResult.Error("Tidak dapat membaca isi file terpilih.")

        if (bytes.size.toLong() > MAX_RAG_FILE_BYTES) {
            return@withContext DocumentReadResult.Error(
                "Ukuran file melebihi batas maksimal 10 MB."
            )
        }

        DocumentReadResult.Success(
            PickedRagDocument(
                fileName = displayName,
                mimeType = mimeType,
                sizeBytes = bytes.size.toLong(),
                bytes = bytes,
            )
        )
    } catch (e: Exception) {
        DocumentReadResult.Error("Gagal memproses file: ${e.message ?: "kesalahan tidak diketahui"}")
    }
}

private fun slugifyInstitutionName(raw: String): String =
    raw.lowercase()
        .replace(Regex("[^a-z0-9\\s-]"), "")
        .replace(Regex("\\s+"), "-")
        .replace(Regex("-+"), "-")
        .trim('-')

private fun formatFileSizeKb(bytes: Long): String {
    val kb = (bytes / 1024L).coerceAtLeast(1L)
    return "$kb KB"
}
