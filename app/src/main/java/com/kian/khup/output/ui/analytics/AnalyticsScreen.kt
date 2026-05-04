package com.kian.khup.output.ui.analytics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AnalyticsScreen() {
    // Phase 3 接 UsageStats 之后填实：日/周/月维度，App 排行榜，趋势图
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("用机分析（Phase 3 上线）")
    }
}
