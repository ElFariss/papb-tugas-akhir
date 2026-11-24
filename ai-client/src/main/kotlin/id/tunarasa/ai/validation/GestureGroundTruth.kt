package id.tunarasa.ai.validation

/** A phrase users are expected to spell, and how confident the recogniser must be for it. */
data class GestureGroundTruth(
    val id: String,
    val expectedText: String,
    val type: String,
    val confidenceThreshold: Double,
    val variations: List<String>,
)

/** Ported from `backend/app/services/gesture_validation_service.py`. */
object GroundTruthDataset {
    val ALL: List<GestureGroundTruth> = listOf(
        GestureGroundTruth("admin_001", "cara membuat ktp baru", "administrative", 0.8, listOf("bagaimana membuat ktp", "prosedur ktp baru", "buat kartu identitas")),
        GestureGroundTruth("admin_002", "perpanjang sim", "administrative", 0.8, listOf("cara perpanjang sim", "renewal sim", "extend driving license")),
        GestureGroundTruth("admin_003", "buat akta kelahiran", "administrative", 0.8, listOf("cara buat akta lahir", "birth certificate", "dokumen kelahiran")),
        GestureGroundTruth("admin_004", "cara daftar bpjs", "administrative", 0.8, listOf("pendaftaran bpjs", "register health insurance", "asuransi kesehatan")),
        GestureGroundTruth("admin_005", "buat paspor baru", "administrative", 0.8, listOf("cara bikin paspor", "passport application", "dokumen perjalanan")),
        GestureGroundTruth("health_001", "dimana rumah sakit terdekat", "health", 0.8, listOf("hospital terdekat", "cari rs", "nearest hospital")),
        GestureGroundTruth("health_002", "cara daftar puskesmas", "health", 0.8, listOf("register puskesmas", "community health center", "fasilitas kesehatan")),
        GestureGroundTruth("health_003", "beli obat dimana", "health", 0.8, listOf("apotek terdekat", "pharmacy location", "cari obat")),
        GestureGroundTruth("health_004", "jadwal dokter", "health", 0.8, listOf("schedule doctor", "appointment dokter", "konsultasi medis")),
        GestureGroundTruth("edu_001", "cara daftar sekolah", "education", 0.8, listOf("pendaftaran sekolah", "school registration", "masuk sekolah")),
        GestureGroundTruth("edu_002", "beasiswa tersedia", "education", 0.8, listOf("scholarship available", "bantuan pendidikan", "dana kuliah")),
        GestureGroundTruth("edu_003", "jadwal ujian", "education", 0.8, listOf("exam schedule", "test schedule", "waktu ujian")),
        GestureGroundTruth("trans_001", "jadwal bus", "transportation", 0.8, listOf("schedule bus", "waktu bus", "transportasi umum")),
        GestureGroundTruth("trans_002", "tiket kereta api", "transportation", 0.8, listOf("train ticket", "beli tiket ka", "perjalanan kereta")),
        GestureGroundTruth("trans_003", "tarif ojek online", "transportation", 0.8, listOf("harga gojek", "biaya grab", "online transport")),
        GestureGroundTruth("emerg_001", "panggil ambulans", "emergency", 0.9, listOf("call ambulance", "darurat medis", "emergency medical")),
        GestureGroundTruth("emerg_002", "lapor polisi", "emergency", 0.9, listOf("call police", "emergency police", "keamanan")),
        GestureGroundTruth("emerg_003", "panggil pemadam", "emergency", 0.9, listOf("call fire department", "damkar", "kebakaran")),
        GestureGroundTruth("comm_001", "tolong saya", "communication", 0.9, listOf("help me", "bantuan", "need assistance")),
        GestureGroundTruth("comm_002", "saya tidak bisa dengar", "communication", 0.9, listOf("i cant hear", "deaf", "tuli", "gangguan pendengaran")),
        GestureGroundTruth("comm_003", "terima kasih", "communication", 0.8, listOf("thank you", "thanks", "makasih")),
        GestureGroundTruth("comm_004", "permisi", "communication", 0.8, listOf("excuse me", "sorry", "maaf")),
    )
}
