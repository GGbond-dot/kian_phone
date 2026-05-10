package com.kian.khup.output.ui.today.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kian.khup.output.ui.today.TodayViewModel.MiniObservation

@Composable
fun MiniObservationCard(
    observation: MiniObservation,
    onViewHistory: () -> Unit,
    onViewNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("今日观察", style = MaterialTheme.typography.titleSmall) // TODO: strings.xml
            ObservationRow("屏幕时间", formatDuration(observation.screenTimeMs)) // TODO
            ObservationRow("注意力异常", "${observation.anomalyCount} 次") // TODO
            ObservationRow("检入次数", "${observation.checkInCount} 次") // TODO
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onViewNotifications) {
                    Text("查看通知 →") // TODO: strings.xml
                }
                TextButton(onClick = onViewHistory) {
                    Text("查看历史 →") // TODO: strings.xml
                }
            }
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
