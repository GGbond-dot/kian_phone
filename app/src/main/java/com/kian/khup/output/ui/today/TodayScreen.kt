package com.kian.khup.output.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AvTimer
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kian.khup.output.ui.today.TodayViewModel.NavigationEvent
import com.kian.khup.output.ui.today.components.AnomalySuggestionCard
import com.kian.khup.output.ui.today.components.MiniObservationCard
import com.kian.khup.output.ui.today.components.QuickCheckInCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToDailyPlan: () -> Unit,
    onNavigateToAppUsage: () -> Unit,
    onNavigateToAi: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.navigationEvent) {
        when (state.navigationEvent) {
            is NavigationEvent.GoToAi -> {
                onNavigateToAi()
                viewModel.clearNavigationEvent()
            }
            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KHUP", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onNavigateToDailyPlan) {
                        Icon(Icons.Outlined.ChecklistRtl, contentDescription = "今日计划")
                    }
                    IconButton(onClick = onNavigateToAppUsage) {
                        Icon(Icons.Outlined.AvTimer, contentDescription = "应用使用时间")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                AnomalySuggestionCard(
                    state = state.suggestionCardState,
                    onAccept = viewModel::acceptSuggestion,
                    onPostpone = viewModel::postponeSuggestion,
                    onReject = viewModel::openRejectDialog,
                )
            }
            item {
                QuickCheckInCard(
                    text = state.checkInText,
                    onTextChange = viewModel::onCheckInTextChange,
                    onSubmit = viewModel::submitCheckIn,
                    isSubmitting = state.isSubmitting,
                )
            }
            item {
                MiniObservationCard(
                    observation = state.miniObservation,
                    onViewHistory = onNavigateToHistory,
                    onViewNotifications = onNavigateToNotifications,
                )
            }
        }
    }

    state.rejectDialogState?.let {
        RejectDialog(
            onDismiss = viewModel::closeRejectDialog,
            onConfirm = viewModel::confirmReject,
            onConfirmAndChat = viewModel::confirmRejectAndChat,
        )
    }
}

@Composable
private fun RejectDialog(
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
    onConfirmAndChat: (String?) -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    val sanitized: () -> String? = { reason.trim().ifBlank { null } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("这条不太合适？") }, // TODO: strings.xml
        text = {
            Column {
                Text(
                    text = "说说为什么（选填）", // TODO: strings.xml
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 100) reason = it },
                    placeholder = { Text("比如：太累了不想出门") }, // TODO: strings.xml
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        // 三个出口：
        //   算了，跳过 → 维持 PENDING（dismiss）
        //   确定      → 写 REJECTED，卡片消失
        //   和 AI 聊聊 → 写 REJECTED + 跳到 AI tab + 自动发送预填上下文
        confirmButton = {
            TextButton(onClick = { onConfirmAndChat(sanitized()) }) {
                Text("和 AI 聊聊 →") // TODO: strings.xml
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("算了，跳过") // TODO: strings.xml
                }
                TextButton(onClick = { onConfirm(sanitized()) }) {
                    Text("确定") // TODO: strings.xml
                }
            }
        },
    )
}
