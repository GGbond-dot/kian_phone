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
        text = "今天还没攒够数据。写一段你现在在做什么，我来帮你看看。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 8.dp),
    )
}
