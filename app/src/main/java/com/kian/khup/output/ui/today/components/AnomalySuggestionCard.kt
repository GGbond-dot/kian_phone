package com.kian.khup.output.ui.today.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kian.khup.core.data.db.entities.AnomalySuggestion
import com.kian.khup.output.ui.today.TodayViewModel.SuggestionCardState

@Composable
fun AnomalySuggestionCard(
    state: SuggestionCardState,
    onAccept: (Long) -> Unit,
    onPostpone: (Long) -> Unit,
    onReject: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "今日异常值", // TODO: strings.xml
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            when (state) {
                is SuggestionCardState.Loading -> {}
                is SuggestionCardState.Empty -> EmptyStateBlock()
                is SuggestionCardState.Generating -> GeneratingStateBlock()
                is SuggestionCardState.RecentAccepted -> RecentAcceptedContent(state.suggestion)
                is SuggestionCardState.Pending -> PendingContent(
                    suggestion = state.suggestion,
                    onAccept = onAccept,
                    onPostpone = onPostpone,
                    onReject = onReject,
                )
            }
        }
    }
}

@Composable
private fun PendingContent(
    suggestion: AnomalySuggestion,
    onAccept: (Long) -> Unit,
    onPostpone: (Long) -> Unit,
    onReject: (Long) -> Unit,
) {
    Text(suggestion.title, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    Text(suggestion.actionText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(8.dp))
    Text(
        text = "为什么这条", // TODO: strings.xml
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = suggestion.whyText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "成本：${suggestion.costLevel} · 收益：${suggestion.expectedUpside}", // TODO: strings.xml
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onAccept(suggestion.id) },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text("接受") } // TODO: strings.xml
        OutlinedButton(
            onClick = { onPostpone(suggestion.id) },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text("换一个") } // TODO: strings.xml
        OutlinedButton(
            onClick = { onReject(suggestion.id) },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) { Text("不适合") } // TODO: strings.xml
    }
}

@Composable
private fun RecentAcceptedContent(suggestion: AnomalySuggestion) {
    Column(modifier = Modifier.alpha(0.5f)) {
        Text(suggestion.title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "已采纳", // TODO: strings.xml
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
