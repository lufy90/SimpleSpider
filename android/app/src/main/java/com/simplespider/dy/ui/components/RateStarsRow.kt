package com.simplespider.dy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun RateStarsRow(
    value: Int,
    modifier: Modifier = Modifier,
    maxStars: Int = 5,
    starSize: Dp = 26.dp,
    enabled: Boolean = true,
    onValueChange: (Int) -> Unit,
) {
    val clamped = value.coerceIn(0, maxStars)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (1..maxStars).forEach { star ->
            IconButton(
                onClick = {
                    if (!enabled) return@IconButton
                    onValueChange(if (star == clamped) 0 else star)
                },
                enabled = enabled,
                modifier = Modifier.size(starSize + 8.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                ),
            ) {
                Icon(
                    imageVector = if (star <= clamped) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = null,
                    modifier = Modifier.size(starSize),
                )
            }
        }
    }
}
