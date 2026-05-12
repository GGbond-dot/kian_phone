package com.kian.khup.output.ui.history.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.kian.khup.core.data.db.entities.AnomalySuggestion
import com.kian.khup.output.ui.history.components.SuggestionRow
import com.kian.khup.output.ui.theme.Spacing

// TODO: strings.xml
private val STATUS_ORDER = listOf("ACCEPTED", "POSTPONED", "REJECTED", "PENDING")
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
    val expanded = remember { mutableStateMapOf("ACCEPTED" to true) }
    val hasAnySuggestion = STATUS_ORDER.any { !suggestions[it].isNullOrEmpty() }

    if (!hasAnySuggestion) {
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
            val list = suggestions[status].orEmpty()
            val isExpanded = expanded[status] == true
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded[status] = !isExpanded }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            )
            {
                Text(
                    text = "${STATUS_LABELS[status] ?: status} (${list.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Outlined.ExpandMore
                    } else {
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight
                    },
                    contentDescription = if (isExpanded) "收起" else "展开",
                )
            }
            if (isExpanded) {
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
}
