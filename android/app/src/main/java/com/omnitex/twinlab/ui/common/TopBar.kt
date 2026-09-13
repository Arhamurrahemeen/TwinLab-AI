package com.omnitex.twinlab.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.omnitex.twinlab.ui.theme.TwinLabNavy
import com.omnitex.twinlab.ui.theme.TwinLabTeal

/** Shared app bar: consistent icon tint + a soft drop shadow instead of a flat divider line. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwinLabTopBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            navigationIconContentColor = TwinLabNavy,
            actionIconContentColor = TwinLabTeal,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.shadow(elevation = 3.dp, shape = RectangleShape, clip = false),
    )
}
