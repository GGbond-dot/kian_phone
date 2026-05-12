package com.kian.khup.output.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * L2 容器：仅背景色，无 elevation，圆角 16dp。
 * 用于：折叠区、检入框、AI 系统消息卡、回顾页 RecentAccepted 建议条。
 */
@Composable
fun L2Surface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

/**
 * L3 焦点容器：Surface + 1dp outline + 大圆角 20dp，全局唯一焦点。
 * 用于：首页 Pending 建议卡（页面唯一 L3）。
 */
@Composable
fun L3FocusCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Box(Modifier.padding(24.dp)) { content() }
    }
}
