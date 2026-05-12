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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kian.khup.output.ui.theme.Accent
import com.kian.khup.output.ui.theme.OnAccent
import com.kian.khup.output.ui.theme.Spacing
import com.kian.khup.output.ui.theme.TextSecondary
import com.kian.khup.output.ui.today.TodayViewModel.NavigationEvent
import com.kian.khup.output.ui.today.components.AnomalySuggestionCard
import com.kian.khup.output.ui.today.components.MiniObservationCard
import com.kian.khup.output.ui.today.components.PlanFoldStripe
import com.kian.khup.output.ui.today.components.QuickCheckInCard
import com.kian.khup.output.ui.today.components.TodayDataFold
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    @Suppress("UNUSED_PARAMETER") onNavigateToSettings: () -> Unit,
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::discussToday,
                containerColor = Accent,
                contentColor = OnAccent,
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = "和 AI 聊聊")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            contentPadding = PaddingValues(top = Spacing.xl, bottom = Spacing.lg),
        ) {
            item {
                GreetingHeader()
                Spacer(Modifier.height(Spacing.xl))
            }
            item {
                MiniObservationCard(
                    observation = state.miniObservation,
                    narrationText = state.todayNarration,
                )
                Spacer(Modifier.height(Spacing.lg))
            }
            item {
                AnomalySuggestionCard(
                    state = state.suggestionCardState,
                    onAccept = viewModel::acceptSuggestion,
                    onPostpone = viewModel::postponeSuggestion,
                    onReject = viewModel::openRejectDialog,
                    onDiscuss = viewModel::discussSuggestion,
                )
            }
            item {
                PlanFoldStripe(
                    todayPlans = state.todayPlans,
                    onAddManual = onNavigateToDailyPlan,
                    onExpandFull = onNavigateToDailyPlan,
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
                TodayDataFold(
                    observation = state.miniObservation,
                    onNavigateToAppUsage = onNavigateToAppUsage,
                    onNavigateToNotifications = onNavigateToNotifications,
                    onNavigateToDetails = onNavigateToHistory,
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
private fun GreetingHeader() {
    val today = remember { LocalDate.now() }
    val hour = remember { LocalTime.now().hour }
    val greeting = when {
        hour in 5..10 -> "早上好"
        hour in 11..16 -> "下午好"
        hour in 17..22 -> "晚上好"
        else -> "还醒着？"
    }
    val dateText = remember(today) {
        today.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.SIMPLIFIED_CHINESE))
    }
    Column {
        Text(text = greeting, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(Spacing.xxs))
        Text(text = dateText, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
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
        title = { Text("这条不太合适？") },
        text = {
            Column {
                Text(
                    text = "说说为什么（选填）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 100) reason = it },
                    placeholder = { Text("比如：太累了不想出门") },
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
                Text("和 AI 聊聊 →")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("算了，跳过")
                }
                TextButton(onClick = { onConfirm(sanitized()) }) {
                    Text("确定")
                }
            }
        },
    )
}
