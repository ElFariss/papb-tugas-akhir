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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.tunarasa.app.data.AdminUserDto
import id.tunarasa.app.data.ApiClient
import id.tunarasa.app.data.ApiResult
import id.tunarasa.app.data.CreateInvitationRequest
import id.tunarasa.app.data.CreateInvitationResponse
import id.tunarasa.app.data.InstitutionResponse
import id.tunarasa.app.data.PlatformApi
import id.tunarasa.app.data.session.SessionStore
import id.tunarasa.app.data.UpdateUserRoleRequest
import id.tunarasa.app.data.UserResponse
import kotlinx.coroutines.launch

private enum class RoleFilterOption(val wire: String?, val label: String) {
    ALL(null, "Semua"),
    SUPER_ADMIN("super_admin", "Super Admin"),
    INSTITUTION_ADMIN("institution_admin", "Admin Instansi"),
    USER("user", "Pengguna"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    platformApi: PlatformApi? = null,
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val sessionStore = remember(context) {
        SessionStore(context.applicationContext)
    }

    val api = remember(platformApi, sessionStore) {
        platformApi ?: PlatformApi(
            ApiClient(
                tokenProvider = { sessionStore.getAdminToken() },
            )
        )
    }

    var currentUser by remember { mutableStateOf<UserResponse?>(null) }
    var users by remember { mutableStateOf<List<AdminUserDto>>(emptyList()) }
    var institutions by remember { mutableStateOf<List<InstitutionResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedRoleFilter by remember { mutableStateOf(RoleFilterOption.ALL) }

    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var isBannerError by remember { mutableStateOf(false) }

    // State Dialog Undang Admin
    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteEmail by remember { mutableStateOf("") }
    var inviteRole by remember { mutableStateOf("institution_admin") }
    var inviteInstitutionId by remember { mutableStateOf<Int?>(null) }
    var isSendingInvite by remember { mutableStateOf(false) }
    var createdInvitation by remember { mutableStateOf<CreateInvitationResponse?>(null) }

    // State Dialog Klaim Token Undangan
    var showAcceptTokenDialog by remember { mutableStateOf(false) }
    var redeemTokenInput by remember { mutableStateOf("") }
    var isRedeemingToken by remember { mutableStateOf(false) }

    // State Dialog Ubah Peran/Status Pengguna
    var editingUser by remember { mutableStateOf<AdminUserDto?>(null) }
    var editRoleName by remember { mutableStateOf("user") }
    var editInstitutionId by remember { mutableStateOf<Int?>(null) }
    var editStatus by remember { mutableStateOf("active") }
    var isUpdatingUser by remember { mutableStateOf(false) }

    val isSuperAdmin = (currentUser?.role ?: "super_admin") == "super_admin"

    val institutionMap = remember(institutions) {
        institutions.associateBy { it.id }
    }

    val filteredUsers = remember(users, searchQuery, selectedRoleFilter) {
        users.filter { user ->
            val matchesRole = selectedRoleFilter.wire == null || user.roleName == selectedRoleFilter.wire
            val query = searchQuery.trim().lowercase()
            val matchesQuery = query.isEmpty() ||
                user.email.lowercase().contains(query) ||
                (user.fullName?.lowercase()?.contains(query) == true)
            matchesRole && matchesQuery
        }
    }

    val superAdminCount = users.count { it.roleName == "super_admin" }
    val instAdminCount = users.count { it.roleName == "institution_admin" }
    val activeUserCount = users.count { it.status == "active" }

    fun loadData() {
        scope.launch {
            isLoading = true
            loadError = null

            when (val meRes = api.getCurrentUser()) {
                is ApiResult.Success -> currentUser = meRes.data
                is ApiResult.Error -> Unit
            }

            when (val instRes = api.getInstitutions()) {
                is ApiResult.Success -> {
                    institutions = instRes.data
                    if (inviteInstitutionId == null && instRes.data.isNotEmpty()) {
                        inviteInstitutionId = instRes.data.first().id
                    }
                }
                is ApiResult.Error -> Unit
            }

            when (val usersRes = api.getAdminUsers()) {
                is ApiResult.Success -> {
                    users = usersRes.data
                }
                is ApiResult.Error -> {
                    loadError = usersRes.message
                }
            }

            isLoading = false
        }
    }

    fun submitInvitation() {
        val trimmedEmail = inviteEmail.trim()
        if (trimmedEmail.isEmpty() || isSendingInvite) return

        scope.launch {
            isSendingInvite = true
            bannerMessage = null

            val targetInstId = if (inviteRole == "institution_admin") inviteInstitutionId else null
            val result = api.createInvitation(
                CreateInvitationRequest(
                    email = trimmedEmail,
                    roleName = inviteRole,
                    institutionId = targetInstId,
                )
            )

            when (result) {
                is ApiResult.Success -> {
                    createdInvitation = result.data
                    inviteEmail = ""
                    isBannerError = false
                    bannerMessage = "Undangan berhasil dibuat untuk $trimmedEmail."
                }
                is ApiResult.Error -> {
                    isBannerError = true
                    bannerMessage = "Gagal membuat undangan: ${result.message}"
                }
            }
            isSendingInvite = false
        }
    }

    fun submitAcceptToken() {
        val trimmedToken = redeemTokenInput.trim()
        if (trimmedToken.isEmpty() || isRedeemingToken) return

        scope.launch {
            isRedeemingToken = true
            bannerMessage = null

            when (val result = api.acceptInvitation(trimmedToken)) {
                is ApiResult.Success -> {
                    showAcceptTokenDialog = false
                    redeemTokenInput = ""
                    isBannerError = false
                    bannerMessage = result.data.message
                    loadData()
                }
                is ApiResult.Error -> {
                    isBannerError = true
                    bannerMessage = "Gagal memproses token: ${result.message}"
                }
            }
            isRedeemingToken = false
        }
    }

    fun submitUpdateUser() {
        val target = editingUser ?: return
        if (isUpdatingUser) return

        scope.launch {
            isUpdatingUser = true
            bannerMessage = null

            val targetInstId = if (editRoleName == "institution_admin") editInstitutionId else null
            val result = api.updateUserRole(
                userId = target.id,
                request = UpdateUserRoleRequest(
                    roleName = editRoleName,
                    institutionId = targetInstId,
                    status = editStatus,
                )
            )

            when (result) {
                is ApiResult.Success -> {
                    editingUser = null
                    isBannerError = false
                    bannerMessage = "Peran dan status ${target.email} berhasil diperbarui."
                    loadData()
                }
                is ApiResult.Error -> {
                    isBannerError = true
                    bannerMessage = "Gagal memperbarui pengguna: ${result.message}"
                }
            }
            isUpdatingUser = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    // Dialog Undang Admin Baru
    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSendingInvite) {
                    showInviteDialog = false
                    createdInvitation = null
                }
            },
            title = {
                Text(
                    text = if (createdInvitation == null) "Undang Administrator" else "Token Undangan Dibuat",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                if (createdInvitation != null) {
                    val inv = createdInvitation!!
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Bagikan token berikut kepada calon administrator untuk mengaktifkan aksesnya:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = inv.token,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(inv.token))
                                        isBannerError = false
                                        bannerMessage = "Token undangan disalin ke clipboard."
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = "Salin token undangan",
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Berlaku hingga: ${inv.expiresAt}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = inviteEmail,
                            onValueChange = { inviteEmail = it },
                            label = { Text("Email Calon Admin") },
                            placeholder = { Text("admin@instansi.go.id") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Text(
                            text = "Pilih Peran",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = inviteRole == "institution_admin",
                                onClick = { inviteRole = "institution_admin" },
                                label = { Text("Admin Instansi") },
                                modifier = Modifier.weight(1f),
                            )
                            FilterChip(
                                selected = inviteRole == "super_admin",
                                onClick = { inviteRole = "super_admin" },
                                label = { Text("Super Admin") },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        if (inviteRole == "institution_admin") {
                            Text(
                                text = "Institusi Penugasan",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )

                            if (institutions.isEmpty()) {
                                Text(
                                    text = "Belum ada institusi aktif yang tersedia.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                InstitutionSelectorBox(
                                    institutions = institutions,
                                    selectedInstitutionId = inviteInstitutionId,
                                    onSelect = { inviteInstitutionId = it },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (createdInvitation != null) {
                    Button(
                        onClick = {
                            showInviteDialog = false
                            createdInvitation = null
                        }
                    ) {
                        Text("Selesai")
                    }
                } else {
                    val canSubmit = inviteEmail.trim().isNotEmpty() &&
                        !isSendingInvite &&
                        (inviteRole != "institution_admin" || inviteInstitutionId != null)
                    Button(
                        onClick = { submitInvitation() },
                        enabled = canSubmit,
                    ) {
                        if (isSendingInvite) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Buat Undangan")
                    }
                }
            },
            dismissButton = {
                if (createdInvitation == null) {
                    TextButton(
                        onClick = { showInviteDialog = false },
                        enabled = !isSendingInvite,
                    ) {
                        Text("Batal")
                    }
                }
            },
        )
    }

    // Dialog Klaim Token Undangan
    if (showAcceptTokenDialog) {
        AlertDialog(
            onDismissRequest = { if (!isRedeemingToken) showAcceptTokenDialog = false },
            title = {
                Text(
                    text = "Klaim Token Undangan",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Masukkan token undangan yang diberikan oleh Super Admin untuk mengaktifkan peran admin pada akun Anda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = redeemTokenInput,
                        onValueChange = { redeemTokenInput = it },
                        label = { Text("Token Undangan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { submitAcceptToken() },
                    enabled = redeemTokenInput.trim().isNotEmpty() && !isRedeemingToken,
                ) {
                    if (isRedeemingToken) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Aktifkan")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAcceptTokenDialog = false },
                    enabled = !isRedeemingToken,
                ) {
                    Text("Batal")
                }
            },
        )
    }

    // Dialog Ubah Peran & Status Pengguna
    if (editingUser != null) {
        val targetUser = editingUser!!
        AlertDialog(
            onDismissRequest = { if (!isUpdatingUser) editingUser = null },
            title = {
                Text(
                    text = "Atur Akses Pengguna",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = targetUser.email,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Text(
                        text = "Peran Akun",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "user" to "Pengguna Biasa",
                            "institution_admin" to "Admin Instansi",
                            "super_admin" to "Super Admin",
                        ).forEach { (roleKey, roleLabel) ->
                            FilterChip(
                                selected = editRoleName == roleKey,
                                onClick = {
                                    editRoleName = roleKey
                                    if (roleKey == "institution_admin" && editInstitutionId == null) {
                                        editInstitutionId = institutions.firstOrNull()?.id
                                    }
                                },
                                label = { Text(roleLabel) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (editRoleName == "institution_admin" && institutions.isNotEmpty()) {
                        Text(
                            text = "Institusi",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        InstitutionSelectorBox(
                            institutions = institutions,
                            selectedInstitutionId = editInstitutionId,
                            onSelect = { editInstitutionId = it },
                        )
                    }

                    Text(
                        text = "Status Akun",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = editStatus == "active",
                            onClick = { editStatus = "active" },
                            label = { Text("Aktif") },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = editStatus != "active",
                            onClick = { editStatus = "inactive" },
                            label = { Text("Nonaktif") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { submitUpdateUser() },
                    enabled = !isUpdatingUser,
                ) {
                    if (isUpdatingUser) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { editingUser = null },
                    enabled = !isUpdatingUser,
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
                            text = "Manajemen Pengguna",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Administrator, peran & undangan",
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
                    IconButton(onClick = { showAcceptTokenDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = "Klaim token undangan",
                        )
                    }
                    IconButton(
                        onClick = { loadData() },
                        enabled = !isLoading,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Muat ulang daftar pengguna",
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

            // Ringkasan Statistik Pengguna (2x2)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        UserStatBox(
                            label = "Total Akun",
                            value = users.size.toString(),
                            accentColor = Color(0xFF2563EB),
                            modifier = Modifier.weight(1f),
                        )
                        UserStatBox(
                            label = "Akun Aktif",
                            value = activeUserCount.toString(),
                            accentColor = Color(0xFF16A34A),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        UserStatBox(
                            label = "Super Admin",
                            value = superAdminCount.toString(),
                            accentColor = Color(0xFFD97706),
                            modifier = Modifier.weight(1f),
                        )
                        UserStatBox(
                            label = "Admin Instansi",
                            value = instAdminCount.toString(),
                            accentColor = Color(0xFF7C3AED),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Tombol Aksi Utama (Undang Admin / Klaim Token)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (isSuperAdmin) {
                        Button(
                            onClick = {
                                createdInvitation = null
                                showInviteDialog = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Undang Admin")
                        }
                    }

                    OutlinedButton(
                        onClick = { showAcceptTokenDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = if (isSuperAdmin) Modifier else Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Klaim Token")
                    }
                }
            }

            // Pencarian & Filter Peran
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari nama atau email pengguna...") },
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RoleFilterOption.entries.forEach { option ->
                            FilterChip(
                                selected = selectedRoleFilter == option,
                                onClick = { selectedRoleFilter = option },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
            }

            // Header Daftar
            item {
                Text(
                    text = "Daftar Akun (${filteredUsers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
            }

            // Konten Daftar Pengguna
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (loadError != null && users.isEmpty()) {
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
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                text = "Gagal Memuat Data Pengguna",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = loadError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = { loadData() }) {
                                Text("Coba Lagi")
                            }
                        }
                    }
                }
            } else if (filteredUsers.isEmpty()) {
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
                                imageVector = Icons.Outlined.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                text = "Tidak ada pengguna yang cocok",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Ubah kata kunci pencarian atau pilih filter peran lainnya.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(filteredUsers, key = { it.id }) { user ->
                    val assignedInstitution = user.institutionId?.let { institutionMap[it]?.name }
                    AdminUserCard(
                        user = user,
                        institutionName = assignedInstitution,
                        canEdit = isSuperAdmin,
                        onEditClick = {
                            editingUser = user
                            editRoleName = user.roleName
                            editInstitutionId = user.institutionId ?: institutions.firstOrNull()?.id
                            editStatus = user.status
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun UserStatBox(
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
private fun AdminUserCard(
    user: AdminUserDto,
    institutionName: String?,
    canEdit: Boolean,
    onEditClick: () -> Unit,
) {
    val (roleLabel, roleBg, roleFg, roleIcon) = when (user.roleName) {
        "super_admin" -> Quadruple(
            "Super Admin",
            Color(0xFFFEF3C7),
            Color(0xFFB45309),
            Icons.Outlined.WorkspacePremium,
        )
        "institution_admin" -> Quadruple(
            "Admin Instansi",
            Color(0xFFDBEAFE),
            Color(0xFF1D4ED8),
            Icons.Outlined.Shield,
        )
        else -> Quadruple(
            "Pengguna",
            Color(0xFFF3F4F6),
            Color(0xFF4B5563),
            Icons.Outlined.Person,
        )
    }

    val isActive = user.status == "active"

    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = roleBg,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = roleIcon,
                            contentDescription = null,
                            tint = roleFg,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.fullName?.takeIf { it.isNotBlank() }
                            ?: user.email.substringBefore("@"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (canEdit) {
                    IconButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Ubah peran ${user.email}",
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = roleBg,
                ) {
                    Text(
                        text = roleLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = roleFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (isActive) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                ) {
                    Text(
                        text = if (isActive) "Aktif" else "Nonaktif",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) Color(0xFF15803D) else Color(0xFFB91C1C),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }

            if (!institutionName.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Business,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = institutionName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun InstitutionSelectorBox(
    institutions: List<InstitutionResponse>,
    selectedInstitutionId: Int?,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = institutions.find { it.id == selectedInstitutionId }?.name
        ?: "Pilih institusi"

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = selectedName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            institutions.forEach { inst ->
                DropdownMenuItem(
                    text = { Text(inst.name) },
                    onClick = {
                        onSelect(inst.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)
