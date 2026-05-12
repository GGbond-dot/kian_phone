package com.kian.khup.output.ui.history.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kian.khup.core.data.db.entities.AnomalySuggestion
import com.kian.khup.output.ui.history.components.SuggestionRow

// TODO: strings.xml
private val STATUS_ORDER = listOf("PENDING", "ACCEPTED", "POSTPONED", "REJECTED")
private val STATUS_LABELS = mapOf(
    "PENDING" to "待处理",
    "ACCEPTED" to "已接受",
    "POSTPONED" to "已换",
    "REJECTED" to "不适合",
)

@Composable
fun SuggestionsTab(
    suggestions: Map<String, List<AnomalySuggestion>>,
    linkedSessions: Map<Long, Long> = emptyMap(),
    onOpenChatSession: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "还没有任何建议记录。", // TODO: strings.xml
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(modifier = modifier) {
        STATUS_ORDER.forEach { status ->
            val list = suggestions[status] ?: return@forEach
            item(key = "header_$status") {
                Text(
                    text = "${STATUS_LABELS[status] ?: status} (${list.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(list, key = { it.id }) { suggestion ->
                val linkedSessionId = linkedSessions[suggestion.id]
                SuggestionRow(
                    suggestion = suggestion,
                    onClick = {},
                    linkedSessionId = linkedSessionId,
                    onOpenChatSession = onOpenChatSession,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}
