package app.quranichorizons.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

object QhColors {
    val NavyDeep = Color(0xFF060D25)
    val Navy = Color(0xFF0B1A45)
    val NavyCard = Color(0xFF111F52)
    val Gold = Color(0xFFD9A84F)
    val GoldSoft = Color(0xFFE9CD8E)
    val Ink = Color(0xFFEAF0FF)
    val Muted = Color(0xFF9FB0D9)
}

private val scheme = darkColorScheme(
    primary = QhColors.Gold,
    onPrimary = QhColors.NavyDeep,
    background = QhColors.NavyDeep,
    onBackground = QhColors.Ink,
    surface = QhColors.NavyCard,
    onSurface = QhColors.Ink,
    secondary = QhColors.GoldSoft,
    onSecondary = QhColors.NavyDeep,
    surfaceVariant = QhColors.Navy,
    onSurfaceVariant = QhColors.Muted,
)

/** نص الآية: خط Serif النظام. الأفضل لاحقًا تضمين خط مصحف مرخّص (انظر docs/EXTERNAL_DEPENDENCIES.md). */
val AyahStyle = TextStyle(fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 52.sp, color = QhColors.Ink)

@Composable
fun QhTheme(content: @Composable () -> Unit) {
    // الواجهة عربية RTL دائمًا، والمظهر ليلي دائمًا (هوية التطبيق)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
