package com.example.locationer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.example.locationer.FontFamilyOption

private val BaseTypography = Typography()

fun locationerTypography(option: FontFamilyOption): Typography {
    val family = when (option) {
        FontFamilyOption.DEFAULT -> FontFamily.Default
        FontFamilyOption.SERIF -> FontFamily.Serif
        FontFamilyOption.MONOSPACE -> FontFamily.Monospace
    }
    return Typography(
        displayLarge = BaseTypography.displayLarge.copy(fontFamily = family, letterSpacing = 0.sp),
        displayMedium = BaseTypography.displayMedium.copy(fontFamily = family, letterSpacing = 0.sp),
        displaySmall = BaseTypography.displaySmall.copy(fontFamily = family, letterSpacing = 0.sp),
        headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = family, letterSpacing = 0.sp),
        headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = family, letterSpacing = 0.sp),
        headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = family, letterSpacing = 0.sp),
        titleLarge = BaseTypography.titleLarge.copy(fontFamily = family, letterSpacing = 0.sp),
        titleMedium = BaseTypography.titleMedium.copy(fontFamily = family, letterSpacing = 0.sp),
        titleSmall = BaseTypography.titleSmall.copy(fontFamily = family, letterSpacing = 0.sp),
        bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = family, letterSpacing = 0.sp),
        bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = family, letterSpacing = 0.sp),
        bodySmall = BaseTypography.bodySmall.copy(fontFamily = family, letterSpacing = 0.sp),
        labelLarge = BaseTypography.labelLarge.copy(fontFamily = family, letterSpacing = 0.sp),
        labelMedium = BaseTypography.labelMedium.copy(fontFamily = family, letterSpacing = 0.sp),
        labelSmall = BaseTypography.labelSmall.copy(fontFamily = family, letterSpacing = 0.sp),
    )
}
