package com.kian.khup.output.ui.history.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kian.khup.core.data.db.entities.AnomalySuggestion
import com.kian.khup.output.ui.history.components.SuggestionRow
import com.kian.khup.output.ui.theme.Spacing

// TODO: strings.xml
private val STATUS_ORDER = listOf("PENDING", "ACCEPTED", "POSTPONED", "REJECTED")
private val STATUS_LABELS = mapOf(
    "PENDING" to "待处理",
    "ACCEPTED" to "已接受",
    "POSTPONED" to "换了一条",
    "REJECTED" to "不适合",
)

@Composable
fun SuggestionsTab(
    suggestions: Map<String, List<AnomalySuggestion>>,
    linkedSessions: Map<Long, Long> = emptyMap(),
    onOpenChatSession: (Long) -> Unit = {},
    onAskAi: (AnomalySuggestion) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) {
        Column(modifier = modifier.fillMaxWidth()) {
            Text(
                text = "还没有任何建议记录。", // TODO: strings.xml
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        STATUS_ORDER.forEach { status ->
            val list = suggestions[status] ?: return@forEach
            Text(
                text = "${STATUS_LABELS[status] ?: status} (${list.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            list.forEach { suggestion ->
                val linkedSessionId = linkedSessions[suggestion.id]
                SuggestionRow(
                    suggestion = suggestion,
                    onClick = {},
                    linkedSessionId = linkedSessionId,
                    onOpenChatSession = onOpenChatSession,
                    onAskAi = { onAskAi(suggestion) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}
