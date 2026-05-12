package com.kian.khup.output.ui.history.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kian.khup.core.data.db.entities.AttentionAnomaly
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PatternRow(
    anomaly: AttentionAnomaly,
    onClick: () -> Unit,
    onAskAi: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(anomaly.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = buildString {
                    append("出现 ${anomaly.frequency} 次") // TODO: strings.xml
                    anomaly.lastSeenAt?.let { append("  ${relativeTime(it)}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onAskAi) {
            Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "问 AI")
        }
    }
}

private fun relativeTime(ms: Long): String {
    val daysDiff = (System.currentTimeMillis() - ms) / 86_400_000
    return when {
        daysDiff == 0L -> "今天"
        daysDiff == 1L -> "昨天"
        daysDiff < 7 -> "${daysDiff}天前"
        else -> DateTimeFormatter.ofPattern("MM-dd")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(ms))
    }
}
