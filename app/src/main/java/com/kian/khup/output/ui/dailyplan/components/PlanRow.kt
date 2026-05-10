package com.kian.khup.output.ui.dailyplan.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kian.khup.core.data.db.entities.DailyPlan

@Composable
fun PlanRow(
    plan: DailyPlan,
    onToggleDone: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentAlpha = if (plan.isDone) 0.5f else 1f
    val titleDecoration = if (plan.isDone) TextDecoration.LineThrough else TextDecoration.None

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 4.dp),
    ) {
        Checkbox(
            checked = plan.isDone,
            onCheckedChange = { onToggleDone() },
        )
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = plan.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = titleDecoration,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!plan.note.isNullOrBlank()) {
                Text(
                    text = plan.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
