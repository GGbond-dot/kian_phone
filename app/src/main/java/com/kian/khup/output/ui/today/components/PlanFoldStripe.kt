package com.kian.khup.output.ui.today.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kian.khup.core.data.db.entities.DailyPlan
import com.kian.khup.output.ui.theme.Spacing

/**
 * 首页"今天 N 件事 · 完成 M 件"折叠条。永远显示（即使 0 项）。
 * 点击整行 / 展开图标 → 跳到 DailyPlanScreen 二级页；+ 图标 → 直接添加。
 */
@Composable
fun PlanFoldStripe(
    todayPlans: List<DailyPlan>,
    onAddManual: () -> Unit,
    onExpandFull: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val doneCount = todayPlans.count { it.isDone }
    val totalCount = todayPlans.size
    val label = if (totalCount == 0) {
        "今天还没有计划"
    } else {
        "今天 $totalCount 件事 · 完成 $doneCount 件"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onExpandFull() }
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        IconButton(
            onClick = onAddManual,
            modifier = Modifier.padding(start = Spacing.xs),
        ) { Icon(Icons.Outlined.Add, contentDescription = "添加") }
        IconButton(onClick = onExpandFull) {
            Icon(Icons.Outlined.ExpandMore, contentDescription = "展开")
        }
    }
}
