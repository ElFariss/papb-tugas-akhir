# BE-1 Implementation & Handover Guide (Platform Core, Auth/RBAC, Institutions, & RAG)

Panduan teknis dan dokumentasi lengkap hasil pengerjaan **BE-1 (Backend Engineer 1)** pada proyek **Tunarasa**.

---

## 📌 Ringkasan Eksekutif BE-1

Modul BE-1 bertanggung jawab atas infrastruktur dasar platform backend, autentikasi berbasis Supabase JWT, Role-Based Access Control (RBAC), manajemen institusi, konfigurasi global aplikasi, integrasi pengiriman email undangan admin via Resend, manajemen berkas RAG, pemicu *ingestion* AI-2, serta middleware keamanan dan pemantauan performa (Rate Limiting, Health Monitoring, Prometheus Metrics).

Semua pekerjaan telah **100% selesai**, terintegrasi, dan lulus pengujian unit test (*5/5 test suites passed*).

---

## 🏗️ 1. Arsitektur & Teknologi Backend

Backend Tunarasa dibangun menggunakan Kotlin Multi-Module dengan **JVM Target 17/21** dan ekosistem berikut:

| Komponen | Teknologi | Keterangan |
|---|---|---|
| **Framework Backend** | **Ktor 3.1.1 (Netty Engine)** | Native Kotlin Coroutines, ringan, cepat, dan asynchronous. |
| **Database ORM** | **Exposed 0.58.0 (DSL & DAO)** | Kotlin SQL ORM resmi dari JetBrains. |
| **Database Migration** | **Flyway 11.3.1** | Pengelola skema database otomatis berbasis file DDL `V1xx__*.sql`. |
| **Connection Pooling** | **HikariCP 6.2.1** | High-performance JDBC connection pool. |
| **Database Production/Dev** | **PostgreSQL 42.7.5 / H2 In-Memory** | Default H2 in-memory untuk dev/test tanpa dependensi DB eksternal. |
| **Cache & KV Store** | **Redis Client (Jedis 5.2.0)** | Cache token, session, dan Rate Limiting window counter. |
| **Autentikasi** | **Supabase JWT Verifier** | Verifikasi HMAC256 signature token JWT Supabase. |
| **Email Service** | **Resend API (Ktor Client CIO)** | Pengiriman email HTML undangan admin secara otomatis. |
| **Monitoring & Metrics** | **Micrometer & Prometheus** | Scraper metrik performa Ktor di `/metrics`. |

---

## 🗄️ 2. Skema Database (Flyway Migration `V101__create_platform_tables.sql`)

BE-1 mengelola 8 tabel utama di modul `platform-core`:

1. **`roles`**: Menyiapkan hak akses pengguna:
   - `1`: `super_admin` (Akses penuh ke seluruh sistem)
   - `2`: `institution_admin` (Akses terbatas ke institusi miliknya)
   - `3`: `user` (Pengguna publik)
2. **`institutions`**: Menyimpan data lembaga/instansi publik (`id`, `name`, `slug` unik, `status`).
3. **`users`**: Menyimpan identitas lokal pengguna (`id` UUID dari Supabase Auth, `email`, `role_id`, `institution_id`, `full_name`, `status`).
4. **`admin_invitations`**: Menyimpan token undangan admin institusi, tanggal ekspirasi (48 jam), dan status (`pending`, `accepted`, `expired`).
5. **`user_sync_log`**: Log riwayat sinkronisasi akun dari Supabase Auth.
6. **`app_settings`**: Konfigurasi global sistem (`key`, `value`, `description`), diawali dengan `service_mode = full_llm_bot`.
7. **`institution_settings`**: Pasangan kunci-nilai konfigurasi spesifik per institusi (misal jam operasional, prompt khusus).
8. **`rag_files`**: Metadata dokumen RAG (`id`, `institution_id`, `file_name`, `file_path`, `file_size`, `processing_status`, `error_message`, `uploaded_by`). Status mencakup: `pending`, `processing`, `completed`, `failed`.

---

## 🔌 3. Ringkasan API Endpoints Tanggung Jawab BE-1

### A. Health Monitoring & Prometheus Metrics
- `GET /api/v1/health` — Status kesehatan DB & Redis (HTTP 200/503).
- `GET /api/v1/health/liveness` — Kubernetes/Docker Liveness Probe.
- `GET /api/v1/health/readiness` — Kubernetes/Docker Readiness Probe.
- `GET /metrics` — Prometheus metrics scraper.

### B. Configuration & Public Routes
- `GET /api/v1/service-config` — Membaca mode layanan AI global (`service_mode`).
- `GET /api/v1/institutions` — Daftar institusi aktif.
- `GET /api/v1/institutions/{id}` — Detail institusi tertentu.

### C. Authentication & User Sync (`Authorization: Bearer <token>`)
- `GET /api/v1/auth/me` — Membaca profil pengguna aktif, peran, dan institusinya.
- `POST /api/v1/auth/sync` — Sinkronisasi pengguna Supabase ke DB lokal.

### D. Institution Management & RAG Files
- `POST /api/v1/institutions` — Membuat institusi baru & auto-slug (`super_admin`).
- `PUT /api/v1/institutions/{id}` — Mengubah detail institusi (`super_admin` / `institution_admin`).
- `GET /api/v1/institutions/{id}/settings` — Membaca pengaturan spesifik institusi.
- `PUT /api/v1/institutions/{id}/settings` — Memperbarui pengaturan spesifik institusi.
- `GET /api/v1/institutions/{id}/rag-files` — Daftar dokumen RAG institusi.
- `POST /api/v1/institutions/{id}/rag-files` — Upload multipart berkas RAG (max 10MB, format `.pdf`, `.txt`, `.md`, `.docx`) & memicu *ingestion* AI-2.
- `DELETE /api/v1/institutions/{id}/rag-files/{fileId}` — Menghapus dokumen RAG & file fisik.

### E. Admin Settings, Users, & Invitations
- `GET /api/v1/admin/settings` & `PUT /api/v1/admin/settings` — Pengaturan global aplikasi (`super_admin`).
- `GET /api/v1/admin/users` — Daftar pengguna (Filtered per institusi jika `institution_admin`).
- `PATCH /api/v1/admin/users/{id}` — Mengubah peran/status/institusi pengguna (`super_admin`).
- `POST /api/v1/admin/invitations` — Membuat undangan admin & mengirim email via Resend API.
- `POST /api/v1/admin/invitations/accept` — Klaim token undangan admin.

---

## 🛠️ 4. Variabel Lingkungan (Environment Variables)

Gunakan berkas `.env` atau konfigurasi server environment berikut:

```env
# Server Config
PORT=8080
HOST=0.0.0.0
ALLOWED_ORIGINS=https://tunarasa.id,https://admin.tunarasa.id
APP_BASE_URL=https://tunarasa.id

# Database Config (Default H2 In-Memory jika tidak diisi)
JDBC_URL=jdbc:postgresql://localhost:5432/tunarasa
DB_DRIVER=org.postgresql.Driver
DB_USER=postgres
DB_PASSWORD=postgres
DB_MAX_POOL_SIZE=10

# Redis Config (Default localhost:6379 jika tidak diisi)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Supabase Auth JWT Secret (Wajib diisi di Production)
SUPABASE_JWT_SECRET=rahasia_jwt_supabase_minimal_32_karakter

# Resend Email Integration (Opsional di Dev)
RESEND_API_KEY=re_123456789_abcdefg
RESEND_FROM_EMAIL=Tunarasa <no-reply@tunarasa.id>
```

---

## 💻 5. Panduan Menjalankan & Pengujian

### A. Menjalankan Backend Server:
```bash
./gradlew :backend:server:run
```

### B. Menjalankan Unit Test BE-1:
```bash
./gradlew :backend:platform-core:test
```

---

## 🤝 6. Panduan Serah Terima (Handover) Kepada Anggota Tim

### A. Untuk Tim FE (Frontend / Mobile Android Engineer):
1. **Panggil `/api/v1/service-config`** saat aplikasi diinisialisasi untuk mengetahui mode AI (`full_llm_bot` vs `bot_with_admin_validation`).
2. **Panggil `POST /api/v1/auth/sync`** setelah pengguna berhasil login via Supabase Auth di aplikasi/mobile dengan header `Authorization: Bearer <supabase_access_token>`.
3. Gunakan `GET /api/v1/auth/me` untuk mendapatkan profil, peran (`role`), dan `institutionId` pengguna.
4. Gunakan `POST /api/v1/institutions/{id}/rag-files` dengan `MultipartFormData` untuk fitur unggah dokumen portal admin.

### B. Untuk Tim BE-2 (Realtime Chat Engineer):
1. **Gunakan `DatabaseFactory.dbQuery { ... }`** untuk seluruh transaksi database agar non-blocking.
2. **Gunakan `RedisClient`** untuk penyimpanan session temporary atau caching.
3. **Gunakan `call.userPrincipal()`** untuk mendapatkan konteks pengguna yang telah terautentikasi oleh Supabase JWT.
4. **Gunakan `authorizeRoles("super_admin", "institution_admin")`** pada *route group* di modul `realtime-chat` untuk mengamankan antrean admin.
5. **Aturan Migrasi Database:** Buat file DDL migrasi Anda di `backend/realtime-chat/src/main/resources/db/migration/` dengan awalan nama `V2xx__*.sql`.

### C. Untuk Tim AI-2 (RAG & LLM Engineer):
1. **Implementasikan `RagLlmService`** di modul `:backend:rag-llm` pada kelas `DefaultRagLlmService`.
2. **Fungsi `ingestDocument(ragFileId: Int, institutionSlug: String)`**:
   - Dipanggil otomatis oleh BE-1 secara asinkron saat dokumen RAG diunggah.
   - Baca metadata file dari tabel `rag_files` berdasarkan `ragFileId` untuk mendapatkan `file_path`.
   - Gunakan `institutionSlug` sebagai namespace Pinecone dengan format: `institution_<slug>` (contoh: `institution_disdukcapil-kota-malang`).
   - Kembalikan `IngestionResult(ragFileId, RagProcessingStatus.COMPLETED, chunkCount)`.
