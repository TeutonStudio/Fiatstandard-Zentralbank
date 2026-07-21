package de.teutonstudio.zentralbank.schnittstelle.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.teutonstudio.zentralbank.R

internal val MarkZeichenSchrift = FontFamily(Font(R.font.zentralbank_mark_symbol))

private val materialTypografie = Typography()

val Typography = Typography(
    displayLarge = materialTypografie.displayLarge.copy(fontFamily = MarkZeichenSchrift),
    displayMedium = materialTypografie.displayMedium.copy(fontFamily = MarkZeichenSchrift),
    displaySmall = materialTypografie.displaySmall.copy(fontFamily = MarkZeichenSchrift),
    headlineLarge = materialTypografie.headlineLarge.copy(fontFamily = MarkZeichenSchrift),
    headlineMedium = materialTypografie.headlineMedium.copy(fontFamily = MarkZeichenSchrift),
    headlineSmall = materialTypografie.headlineSmall.copy(fontFamily = MarkZeichenSchrift),
    titleLarge = materialTypografie.titleLarge.copy(fontFamily = MarkZeichenSchrift),
    titleMedium = materialTypografie.titleMedium.copy(fontFamily = MarkZeichenSchrift),
    titleSmall = materialTypografie.titleSmall.copy(fontFamily = MarkZeichenSchrift),
    bodyLarge = TextStyle(
        fontFamily = MarkZeichenSchrift,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = materialTypografie.bodyMedium.copy(fontFamily = MarkZeichenSchrift),
    bodySmall = materialTypografie.bodySmall.copy(fontFamily = MarkZeichenSchrift),
    labelLarge = materialTypografie.labelLarge.copy(fontFamily = MarkZeichenSchrift),
    labelMedium = materialTypografie.labelMedium.copy(fontFamily = MarkZeichenSchrift),
    labelSmall = materialTypografie.labelSmall.copy(fontFamily = MarkZeichenSchrift),
)
