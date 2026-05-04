package com.kian.khup.output.ui.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun MessagesScreen() {
    // Phase 2 接 LLM 之后填实：按分类 tab（验证码/工作/社交/推广/其他），可搜索
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("消息中心（Phase 2 上线）")
    }
}
