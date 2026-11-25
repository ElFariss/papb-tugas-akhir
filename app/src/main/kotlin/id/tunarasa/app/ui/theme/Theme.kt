package id.tunarasa.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BluePrimary = Color(0xFF1751D0)       // Biru utama tombol & tautan
val BlueHeroCard = Color(0xFFEDF2FE)      // Biru lembut kartu hero & badge D
val MintBadgeBg = Color(0xFFE6F7F0)       // Hijau mint lembut badge aktif
val MintBadgeText = Color(0xFF15803D)     // Hijau teks badge aktif
val SurfaceWhite = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFF0F172A)
val TextSecondary = Color(0xFF475569)
val BorderLight = Color(0xFFE2E8F0)
val ErrorRed = Color(0xFFDC2626)

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = BlueHeroCard,
    onPrimaryContainer = TextPrimary,
    secondaryContainer = MintBadgeBg,
    onSecondaryContainer = MintBadgeText,
    background = SurfaceWhite,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF8FAFC),
    onSurfaceVariant = TextSecondary,
    outline = BorderLight,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun TunarasaTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false, // Dimatikan agar warna biru brand tidak tertimpa wallpaper HP
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
