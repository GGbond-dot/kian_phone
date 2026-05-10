package com.kian.khup.output.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KHUP", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onNavigateToDailyPlan) {
                        Icon(Icons.Outlined.ChecklistRtl, contentDescription = "今日计划")
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
        )
    }
}

@Composable
private fun RejectDialog(
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("为什么不适合？") }, // TODO: strings.xml
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { if (it.length <= 100) reason = it },
                placeholder = { Text("可以跳过（选填）") }, // TODO: strings.xml
                maxLines = 3,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim().ifBlank { null }) }) {
                Text("确认") // TODO: strings.xml
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") } // TODO: strings.xml
        },
    )
}
