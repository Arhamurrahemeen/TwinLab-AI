@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.omnitex.twinlab.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.omnitex.twinlab.R

private fun variableFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Brand serif, matches the "TwinLab" wordmark — used for titles and the brand lockup. */
val TwinLabBrandFont = FontFamily(
    variableFont(R.font.lora_variable, FontWeight.Normal),
    variableFont(R.font.lora_variable, FontWeight.Medium),
    variableFont(R.font.lora_variable, FontWeight.SemiBold),
    variableFont(R.font.lora_variable, FontWeight.Bold),
)

/** Body/data sans — used for everything else: sensor readings, labels, alerts. */
val TwinLabBodyFont = FontFamily(
    variableFont(R.font.jakarta_variable, FontWeight.Normal),
    variableFont(R.font.jakarta_variable, FontWeight.Medium),
    variableFont(R.font.jakarta_variable, FontWeight.SemiBold),
    variableFont(R.font.jakarta_variable, FontWeight.Bold),
)

val TwinLabTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = TwinLabBrandFont),
        displayMedium = base.displayMedium.copy(fontFamily = TwinLabBrandFont),
        displaySmall = base.displaySmall.copy(fontFamily = TwinLabBrandFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = TwinLabBrandFont),
        headlineMedium = base.headlineMedium.copy(fontFamily = TwinLabBrandFont),
        headlineSmall = base.headlineSmall.copy(fontFamily = TwinLabBrandFont),
        titleLarge = base.titleLarge.copy(fontFamily = TwinLabBrandFont, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = TwinLabBrandFont, fontWeight = FontWeight.Medium),
        titleSmall = base.titleSmall.copy(fontFamily = TwinLabBrandFont, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontFamily = TwinLabBodyFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = TwinLabBodyFont),
        bodySmall = base.bodySmall.copy(fontFamily = TwinLabBodyFont),
        labelLarge = base.labelLarge.copy(fontFamily = TwinLabBodyFont, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = TwinLabBodyFont),
        labelSmall = base.labelSmall.copy(fontFamily = TwinLabBodyFont),
    )
}
