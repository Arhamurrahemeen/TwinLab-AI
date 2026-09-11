package com.omnitex.twinlab.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.omnitex.twinlab.ui.theme.TwinLabNavy
import com.omnitex.twinlab.ui.theme.TwinLabTeal

/** The "TwinLab" brand lockup — navy "Twin" + teal "Lab", matching the logo. */
@Composable
fun TwinLabWordmark() {
    Text(
        buildAnnotatedString {
            withStyle(style = androidx.compose.ui.text.SpanStyle(color = TwinLabNavy)) { append("Twin") }
            withStyle(style = androidx.compose.ui.text.SpanStyle(color = TwinLabTeal)) { append("Lab") }
        },
        style = MaterialTheme.typography.titleLarge,
    )
}
