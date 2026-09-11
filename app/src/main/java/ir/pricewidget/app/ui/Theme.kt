package ir.pricewidget.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import ir.pricewidget.app.R

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Vazirmatn),
        displayMedium = displayMedium.copy(fontFamily = Vazirmatn),
        displaySmall = displaySmall.copy(fontFamily = Vazirmatn),
        headlineLarge = headlineLarge.copy(fontFamily = Vazirmatn),
        headlineMedium = headlineMedium.copy(fontFamily = Vazirmatn),
        headlineSmall = headlineSmall.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
        titleSmall = titleSmall.copy(fontFamily = Vazirmatn),
        bodyLarge = bodyLarge.copy(fontFamily = Vazirmatn),
        bodyMedium = bodyMedium.copy(fontFamily = Vazirmatn),
        bodySmall = bodySmall.copy(fontFamily = Vazirmatn),
        labelLarge = labelLarge.copy(fontFamily = Vazirmatn),
        labelMedium = labelMedium.copy(fontFamily = Vazirmatn),
        labelSmall = labelSmall.copy(fontFamily = Vazirmatn)
    )
}

private val AppColors = darkColorScheme(
    primary = Color(0xFF32D74B),
    secondary = Color(0xFFFFC107),
    background = Color(0xFF121214),
    surface = Color(0xFF1C1C1E),
    error = Color(0xFFFF453A)
)

@Composable
fun PriceWidgetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        content = content
    )
}
