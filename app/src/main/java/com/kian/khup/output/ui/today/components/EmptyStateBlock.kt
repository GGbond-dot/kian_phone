package com.kian.khup.output.ui.today.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// TODO: extract to strings.xml
@Composable
fun EmptyStateBlock(modifier: Modifier = Modifier) {
    Text(
        text = "今天还没有可说的回归值信号。\n要不要写一段你现在的处境？",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 8.dp),
    )
}
