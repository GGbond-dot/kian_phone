package com.kian.khup.output.ui.usage.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kian.khup.common.util.formatDuration

@Composable
fun AppUsageRow(
    packageName: String,
    totalMs: Long,
    maxMs: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appLabel = remember(packageName) {
        runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
                .loadLabel(context.packageManager).toString()
        }.getOrElse { packageName }
    }
    val fraction = if (maxMs > 0) (totalMs.toFloat() / maxMs).coerceIn(0f, 1f) else 0f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = appLabel, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = formatDuration(totalMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
