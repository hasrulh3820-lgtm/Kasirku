package id.kasirku.umkm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFF1B6B3A)
val BrandDark = Color(0xFF0F4A27)
val BrandSoft = Color(0xFFDDEFE2)
val Warn = Color(0xFFE07B00)
val WarnSoft = Color(0xFFFFF1DC)
val Danger = Color(0xFFC62828)
val Profit = Color(0xFF1B7F3B)
val Loss = Color(0xFFC62828)

private val Colors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = BrandSoft,
    onPrimaryContainer = BrandDark,
    secondary = Color(0xFF4E6354),
    secondaryContainer = Color(0xFFD9E7DC),
    tertiary = Warn,
    tertiaryContainer = WarnSoft,
    error = Danger,
    background = Color(0xFFF6F8F4),
    surface = Color(0xFFF6F8F4),
    surfaceVariant = Color(0xFFE6EBE4),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEEF2EC),
)

/** Tema terang saja: dipakai di toko yang terang, dan warna uang (hijau/merah) tetap jelas. */
@Composable
fun KasirKuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
