package com.kian.khup.output.ui.history.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kian.khup.core.data.db.entities.AnomalySuggestion
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SuggestionRow(
    suggestion: AnomalySuggestion,
    onClick: () -> Unit,
    linkedSessionId: Long? = null,
    onOpenChatSession: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    suggestion.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    suggestion.actionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    relativeTime(suggestion.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AssistChip(
                onClick = {},
                label = { Text(statusLabel(suggestion.status), style = MaterialTheme.typography.labelSmall) },
            )
        }
        // 仅 REJECTED 后通过 [和 AI 聊聊] 产生的建议会有 linkedSessionId
        if (linkedSessionId != null) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { onOpenChatSession(linkedSessionId) }) {
                Text("💬 查看当时的讨论 →") // TODO: strings.xml
            }
        }
    }
}

private fun statusLabel(status: String) = when (status) { // TODO: strings.xml
    "ACCEPTED" -> "已接受"
    "PENDING" -> "待处理"
    "POSTPONED" -> "换了一条"
    "REJECTED" -> "不适合"
    else -> status
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
