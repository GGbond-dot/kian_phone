package com.kian.khup.output.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kian.khup.core.ai.AiProviderMode

@Composable
fun AiChatScreen(viewModel: AiChatViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("kian-ai-chat", style = MaterialTheme.typography.headlineSmall)
                val localReady = uiState.modelState.isReady
                val apiReady = uiState.settings.hasApiConfig
                val (subtitle, ok) = when (uiState.settings.providerMode) {
                    AiProviderMode.ApiOnly ->
                        (if (apiReady) "API 通道:已配置" else "API 通道:未配置(去 Settings 配置)") to apiReady
                    AiProviderMode.LocalOnly ->
                        (if (localReady) "本地模型:就绪" else "本地模型:未找到(去 Settings 配置)") to localReady
                    AiProviderMode.LocalFirst -> when {
                        localReady && apiReady -> "本地就绪 · API 兜底" to true
                        localReady -> "本地就绪" to true
                        apiReady -> "本地未就绪 · 走 API" to true
                        else -> "未配置(去 Settings 配置)" to false
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Row {
                IconButton(onClick = viewModel::refreshModelState) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新模型状态")
                }
                IconButton(
                    onClick = viewModel::runSmokeTest,
                    enabled = !uiState.isGenerating,
                ) {
                    Icon(Icons.Outlined.Science, contentDescription = "运行自检")
                }
                IconButton(
                    onClick = viewModel::clear,
                    enabled = uiState.messages.isNotEmpty() && !uiState.isGenerating,
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = "清空对话")
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (uiState.messages.isEmpty()) {
                item {
                    EmptyChatCard()
                }
            }
            items(uiState.messages) { message ->
                ChatMessageCard(message)
            }
            if (uiState.isGenerating) {
                item {
                    Text(
                        "生成中...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        uiState.error?.let { error ->
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (uiState.isGenerating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                enabled = !uiState.isGenerating,
                minLines = 1,
                maxLines = 4,
                placeholder = { Text("问 kian-ai-chat...") },
            )
            IconButton(
                onClick = {
                    viewModel.send(input)
                    input = ""
                },
                enabled = input.isNotBlank() && !uiState.isGenerating,
            ) {
                Icon(Icons.Outlined.Send, contentDescription = "发送")
            }
        }
    }
}

@Composable
private fun EmptyChatCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("先试一句简单问题。", style = MaterialTheme.typography.titleMedium)
            Text(
                "API 配置和模型路径在 Settings 页里调整。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChatMessageCard(message: ChatMessage) {
    val isUser = message.role == ChatRole.User
    val title = if (isUser) "你" else "AI"
    val containerColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(message.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
