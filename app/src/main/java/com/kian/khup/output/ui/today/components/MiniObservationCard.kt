package com.kian.khup.output.ui.today.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kian.khup.output.ui.theme.Spacing
import com.kian.khup.output.ui.today.TodayViewModel.MiniObservation

/**
 * 今日观察：优先渲染 AI 生成的自然语言句（L1 无容器），缺失时回退到原 KV 行。
 */
@Composable
fun MiniObservationCard(
    observation: MiniObservation,
    narrationText: String?,
    onViewHistory: () -> Unit,
    onViewNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!narrationText.isNullOrBlank()) {
        Text(
            text = narrationText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text("今日观察", style = MaterialTheme.typography.titleSmall)
        ObservationRow("屏幕时间", formatDuration(observation.screenTimeMs))
        ObservationRow("状态变化", "${observation.anomalyCount} 次")
        ObservationRow("写过几段", "${observation.checkInCount} 次")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onViewNotifications) { Text("查看通知 →") }
            TextButton(onClick = onViewHistory) { Text("查看历史 →") }
        }
    }
}

@Composable
private fun ObservationRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDuration(ms: Long): String {
    val hours = ms / 3_600_000
    val minutes = (ms % 3_600_000) / 60_000
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
