package com.kian.khup.output.ui.today.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kian.khup.core.data.db.entities.AnomalySuggestion
import com.kian.khup.output.ui.theme.Accent
import com.kian.khup.output.ui.theme.L2Surface
import com.kian.khup.output.ui.theme.L3FocusCard
import com.kian.khup.output.ui.theme.OnAccent
import com.kian.khup.output.ui.theme.PrimaryDim
import com.kian.khup.output.ui.theme.Spacing
import com.kian.khup.output.ui.theme.Success
import com.kian.khup.output.ui.theme.TextSecondary
import com.kian.khup.output.ui.today.TodayViewModel.SuggestionCardState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AnomalySuggestionCard(
    state: SuggestionCardState,
    onAccept: (Long) -> Unit,
    onPostpone: (Long) -> Unit,
    onReject: (Long) -> Unit,
    onDiscuss: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is SuggestionCardState.Loading -> Unit
        is SuggestionCardState.Empty -> Unit  // §4.4：Empty 整张卡不渲染
        is SuggestionCardState.Generating -> {
            L3FocusCard(modifier = modifier.fillMaxWidth()) {
                GeneratingStateBlock()
            }
        }
        is SuggestionCardState.RecentAccepted -> RecentAcceptedCard(
            suggestion = state.suggestion,
            acceptedAtMs = state.acceptedAtMs,
            modifier = modifier.fillMaxWidth(),
        )
        is SuggestionCardState.Pending -> PendingFocusCard(
            suggestion = state.suggestion,
            onAccept = onAccept,
            onPostpone = onPostpone,
            onReject = onReject,
            onDiscuss = onDiscuss,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PendingFocusCard(
    suggestion: AnomalySuggestion,
    onAccept: (Long) -> Unit,
    onPostpone: (Long) -> Unit,
    onReject: (Long) -> Unit,
    onDiscuss: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    L3FocusCard(
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = { onReject(suggestion.id) },
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = suggestion.actionText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = suggestion.whyText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(Spacing.lg))
            Button(
                onClick = { onDiscuss(suggestion.id) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
            ) {
                Text("和 AI 聊聊 →", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                TextButton(
                    onClick = { onAccept(suggestion.id) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("就这样做", color = PrimaryDim)
                }
                TextButton(
                    onClick = { onPostpone(suggestion.id) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("换一条", color = PrimaryDim)
                }
            }
        }
    }
}

@Composable
private fun RecentAcceptedCard(
    suggestion: AnomalySuggestion,
    acceptedAtMs: Long,
    modifier: Modifier = Modifier,
) {
    val timeText = remember(acceptedAtMs) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(acceptedAtMs))
    }
    L2Surface(modifier = modifier) {
        Column {
            Text(
                text = suggestion.actionText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.height(16.dp),
                )
                Spacer(Modifier.width(Spacing.xxs).height(0.dp))
                Text(
                    text = "已写入今日计划 · $timeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = Success,
                )
            }
        }
    }
}
