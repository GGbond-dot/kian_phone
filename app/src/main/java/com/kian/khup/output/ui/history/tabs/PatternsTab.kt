package com.kian.khup.output.ui.history.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kian.khup.core.data.db.entities.AttentionAnomaly
import com.kian.khup.output.ui.history.components.EvidenceListSheet
import com.kian.khup.output.ui.history.components.PatternRow
import com.kian.khup.output.ui.theme.Spacing

@Composable
fun PatternsTab(
    patterns: List<AttentionAnomaly>,
    onAskAi: (AttentionAnomaly) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val selected = patterns.find { it.id == selectedId }

    if (patterns.isEmpty()) {
        Column(modifier = modifier.fillMaxWidth()) {
            Text(
                text = "没有检测到活跃的回归模式。", // TODO: strings.xml
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            patterns.forEach { anomaly ->
                PatternRow(
                    anomaly = anomaly,
                    onClick = { selectedId = anomaly.id },
                    onAskAi = { onAskAi(anomaly) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    selected?.let { anomaly ->
        EvidenceListSheet(
            anomaly = anomaly,
            onDismiss = { selectedId = null },
        )
    }
}
