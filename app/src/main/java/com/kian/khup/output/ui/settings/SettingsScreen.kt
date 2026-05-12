package com.kian.khup.output.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kian.khup.collection.notification.NotificationPermissions
import com.kian.khup.core.ai.AiProviderMode
import com.kian.khup.core.ai.AiSettings
import com.kian.khup.core.ai.LlmModelState
import com.kian.khup.core.data.repository.InterventionSettings
import com.kian.khup.output.ui.theme.Spacing
import com.kian.khup.output.ui.theme.Success
import com.kian.khup.output.ui.theme.Warning
import kotlinx.coroutines.delay

/**
 * 权限引导主页面。MIUI 上每个权限都需要用户手动到设置里开，
 * 这个页面负责检测当前状态、提供「去开启」按钮、给出 MIUI 二次确认提示。
 */
@Composable
fun SettingsScreen(
    onNavigateToAiCallMode: () -> Unit = {},
    onNavigateToAiApi: () -> Unit = {},
    onNavigateToAiLocalModel: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val interventionSettings by viewModel.interventionSettings.collectAsStateWithLifecycle()
    val aiSettings by viewModel.aiSettings.collectAsStateWithLifecycle()
    val aiModelState by viewModel.aiModelState.collectAsStateWithLifecycle()
    val clearState by viewModel.clearState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()

    LaunchedEffect(clearState) {
        if (clearState is SettingsViewModel.ClearState.Done ||
            clearState is SettingsViewModel.ClearState.Error
        ) {
            delay(2000)
            viewModel.resetClearState()
        }
    }

    LaunchedEffect(exportState) {
        if (exportState is SettingsViewModel.ExportState.Done ||
            exportState is SettingsViewModel.ExportState.Error
        ) {
            delay(3000)
            viewModel.resetExportState()
        }
    }

    // 用户从设置页回来时刷新权限状态
    var refreshTick by remember { mutableStateOf(0) }
    var pendingManualConfirmation by remember { mutableStateOf<ManualPermission?>(null) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                when (pendingManualConfirmation) {
                    ManualPermission.MiuiAutostart -> NotificationPermissions.setMiuiAutostartConfirmed(context)
                    ManualPermission.MiuiBattery -> NotificationPermissions.setMiuiBatteryConfirmed(context)
                    null -> Unit
                }
                pendingManualConfirmation = null
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val nlsEnabled = remember(refreshTick) { NotificationPermissions.isNotificationListenerEnabled(context) }
    val postNotificationsEnabled = remember(refreshTick) {
        NotificationPermissions.hasPostNotificationsPermission(context)
    }
    val usageEnabled = remember(refreshTick) { NotificationPermissions.hasUsageAccess(context) }
    val overlayEnabled = remember(refreshTick) { NotificationPermissions.hasOverlayPermission(context) }
    val autostartConfirmed = remember(refreshTick) { NotificationPermissions.isMiuiAutostartConfirmed(context) }
    val batteryConfirmed = remember(refreshTick) { NotificationPermissions.isMiuiBatteryConfirmed(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)

        SectionHeader("让 KHUP 工作")
        PermissionCard(
            title = "系统通知",
            description = "授权后 KHUP 才能发出抖音/小红书超时提醒。没有这个权限时，只会在首页记录干预，不会弹系统通知。",
            status = PermissionStatus.Checked(postNotificationsEnabled),
            onClick = { NotificationPermissions.openAppNotificationSettings(context) },
        )

        PermissionCard(
            title = "读取通知",
            description = "授权后 KHUP 才能读取其他 App 的通知。MIUI 上开启后还会有二次确认弹窗，请记得勾选「允许读取通知」。",
            status = PermissionStatus.Checked(nlsEnabled),
            onClick = { NotificationPermissions.openNotificationListenerSettings(context) },
        )

        PermissionCard(
            title = "应用使用统计",
            description = "授权后 KHUP 才能统计每个 App 的前台时长和打开次数。",
            status = PermissionStatus.Checked(usageEnabled),
            onClick = { NotificationPermissions.openUsageAccessSettings(context) },
        )

        PermissionCard(
            title = "悬浮窗（拦截）",
            description = "拦截抖音/小红书等算法推送时弹出强制等待卡片所必需。MIUI 默认禁用，请手动打开。",
            status = PermissionStatus.Checked(overlayEnabled),
            onClick = { NotificationPermissions.openOverlaySettings(context) },
        )

        PermissionCard(
            title = "MIUI 自启动",
            description = "打开后，重启手机或系统清理后台后，KHUP 更容易恢复通知监听。",
            status = PermissionStatus.ManualCheck(autostartConfirmed),
            onClick = {
                pendingManualConfirmation = ManualPermission.MiuiAutostart
                NotificationPermissions.openMiuiAutostartSettings(context)
            },
        )

        PermissionCard(
            title = "MIUI 省电策略",
            description = "进入 KHUP 应用详情后，点「省电策略」，设置为「无限制」。否则 HyperOS 可能在息屏后杀掉通知监听服务。",
            status = PermissionStatus.ManualCheck(batteryConfirmed),
            onClick = {
                pendingManualConfirmation = ManualPermission.MiuiBattery
                NotificationPermissions.openMiuiBatterySettings(context)
            },
        )

        SectionHeader("AI 设置")
        AiSettingsRows(
            settings = aiSettings,
            modelState = aiModelState,
            onCallModeClick = onNavigateToAiCallMode,
            onApiClick = onNavigateToAiApi,
            onLocalModelClick = onNavigateToAiLocalModel,
        )

        SectionHeader("干预阈值")
        InterventionSettingsCard(
            settings = interventionSettings,
            onDouyinChange = viewModel::setDouyinLimit,
            onXiaohongshuChange = viewModel::setXiaohongshuLimit,
        )

        SectionHeader("数据与隐私")
        DataCard(
            clearState = clearState,
            exportState = exportState,
            onClearConfirm = viewModel::clearAllData,
            onExportRequest = viewModel::exportData,
        )
    }
}

@Composable
private fun AiSettingsRows(
    settings: AiSettings,
    modelState: LlmModelState,
    onCallModeClick: () -> Unit,
    onApiClick: () -> Unit,
    onLocalModelClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        SettingsRow("调用模式", providerModeLabel(settings.providerMode), onCallModeClick)
        SettingsRow("API 配置", if (settings.hasApiConfig) "✓ 已配置" else "⚠ 未配置", onApiClick)
        SettingsRow("本地模型", if (modelState.isReady) "✓ 已就绪" else "⚠ 未找到", onLocalModelClick)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun AiChannelCard(
    settings: AiSettings,
    modelState: LlmModelState,
    onProviderModeChange: (AiProviderMode) -> Unit,
    onSaveApi: (String, String, String) -> Unit,
    onRefreshModel: () -> Unit,
) {
    var baseUrl by remember(settings.apiBaseUrl) { mutableStateOf(settings.apiBaseUrl) }
    var model by remember(settings.apiModel) { mutableStateOf(settings.apiModel) }
    var key by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("AI 通道", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onRefreshModel) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新模型状态")
                }
            }

            Text("本地模型", style = MaterialTheme.typography.titleSmall)
            Text(
                if (modelState.isReady) "✓ 已找到:${modelState.foundPath}" else "✗ 未找到",
                style = MaterialTheme.typography.bodySmall,
                color = if (modelState.isReady) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            if (!modelState.isReady) {
                Text(
                    "把模型 push 到下列任一路径之后,点上方刷新:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                modelState.checkedPaths.forEach { path ->
                    Text(
                        "· $path",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text("调用模式", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ProviderModeButton("本地优先", AiProviderMode.LocalFirst, settings.providerMode, onProviderModeChange)
                ProviderModeButton("仅本地", AiProviderMode.LocalOnly, settings.providerMode, onProviderModeChange)
                ProviderModeButton("仅 API", AiProviderMode.ApiOnly, settings.providerMode, onProviderModeChange)
            }

            Text(
                if (settings.hasApiConfig) "✓ API 已配置" else "API 未配置",
                style = MaterialTheme.typography.labelMedium,
                color = if (settings.hasApiConfig) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("API Base URL") },
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Model") },
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("API Key") },
            )
            TextButton(
                onClick = { onSaveApi(baseUrl, model, key) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("保存 API 配置")
            }
        }
    }
}

@Composable
private fun ProviderModeButton(
    label: String,
    mode: AiProviderMode,
    current: AiProviderMode,
    onModeChange: (AiProviderMode) -> Unit,
) {
    TextButton(onClick = { onModeChange(mode) }) {
        Text(
            label,
            fontWeight = if (mode == current) FontWeight.Bold else FontWeight.Normal,
            color = if (mode == current) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private sealed interface PermissionStatus {
    data class Checked(val granted: Boolean) : PermissionStatus
    data class ManualCheck(val confirmed: Boolean) : PermissionStatus
}

@Composable
private fun InterventionSettingsCard(
    settings: InterventionSettings,
    onDouyinChange: (Int) -> Unit,
    onXiaohongshuChange: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("干预阈值", style = MaterialTheme.typography.titleMedium)
            Text(
                "达到当天累计时长后，KHUP 会提醒一次并写入干预记录。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ThresholdRow(
                title = "抖音",
                minutes = settings.douyinLimitMinutes,
                onChange = onDouyinChange,
            )
            ThresholdRow(
                title = "小红书",
                minutes = settings.xiaohongshuLimitMinutes,
                onChange = onXiaohongshuChange,
            )
        }
    }
}

@Composable
private fun ThresholdRow(
    title: String,
    minutes: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${minutes} 分钟",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onChange(minutes - 5) },
                enabled = minutes > 5,
            ) {
                Icon(Icons.Outlined.Remove, contentDescription = "减少$title 阈值")
            }
            IconButton(
                onClick = { onChange(minutes + 5) },
                enabled = minutes < 240,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "增加$title 阈值")
            }
        }
    }
}

@Composable
private fun DataCard(
    clearState: SettingsViewModel.ClearState,
    exportState: SettingsViewModel.ExportState,
    onClearConfirm: () -> Unit,
    onExportRequest: () -> Unit,
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("清空全部历史数据？") },
            text = {
                Text(
                    "将删除所有通知记录、使用时长、建议历史、对话记录和每日计划。\n\n" +
                    "AI 设置和权限配置不受影响。此操作不可撤销。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showConfirmDialog = false; onClearConfirm() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("确认清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (clearState is SettingsViewModel.ClearState.InProgress) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在清空...") },
            text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
            confirmButton = {},
        )
    }

    LaunchedEffect(clearState) {
        when (clearState) {
            is SettingsViewModel.ClearState.Done ->
                android.widget.Toast.makeText(context, "历史数据已清空", android.widget.Toast.LENGTH_SHORT).show()
            is SettingsViewModel.ClearState.Error ->
                android.widget.Toast.makeText(context, "清空失败：${clearState.message}", android.widget.Toast.LENGTH_LONG).show()
            else -> Unit
        }
    }

    LaunchedEffect(exportState) {
        when (exportState) {
            is SettingsViewModel.ExportState.Done ->
                android.widget.Toast.makeText(context, "已导出到：${exportState.filePath}", android.widget.Toast.LENGTH_LONG).show()
            is SettingsViewModel.ExportState.Error ->
                android.widget.Toast.makeText(context, exportState.message, android.widget.Toast.LENGTH_LONG).show()
            else -> Unit
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingsRow("导出全部数据", onClick = onExportRequest)
            SettingsRow("清空历史数据") { showConfirmDialog = true }
            SettingsRow("数据保留策略") {
                android.widget.Toast.makeText(context, "当前保留近期数据，旧数据会按任务自动清理", android.widget.Toast.LENGTH_SHORT).show()
            }
            SettingsRow("隐私说明") {
                android.widget.Toast.makeText(context, "你的数据从不离开这台手机，除非你主动启用 API 对话", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, value: String? = null, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = value?.let { "$it  →" } ?: "→",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun providerModeLabel(mode: AiProviderMode): String = when (mode) {
    AiProviderMode.LocalFirst -> "本地优先"
    AiProviderMode.LocalOnly -> "仅本地"
    AiProviderMode.ApiOnly -> "仅 API"
}

private enum class ManualPermission {
    MiuiAutostart,
    MiuiBattery,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PermissionCard(
    title: String,
    description: String,
    status: PermissionStatus,
    onClick: () -> Unit,
) {
    val isGranted = when (status) {
        is PermissionStatus.Checked -> status.granted
        is PermissionStatus.ManualCheck -> status.confirmed
    }
    val statusText = when (status) {
        is PermissionStatus.Checked -> if (status.granted) "✓ 已开" else "⚠ 未开"
        is PermissionStatus.ManualCheck -> if (status.confirmed) "✓ 已确认" else "⚠ 需手动确认"
    }
    val statusColor = when (status) {
        is PermissionStatus.Checked -> {
            if (status.granted) Success else Warning
        }
        is PermissionStatus.ManualCheck -> {
            if (status.confirmed) Success else Warning
        }
    }
    val buttonText = when {
        isGranted && status is PermissionStatus.ManualCheck -> "已确认"
        isGranted -> "已开启"
        status is PermissionStatus.ManualCheck -> "去确认"
        else -> "去开启"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (!isGranted) onClick() },
                onLongClick = { if (isGranted) onClick() },
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                )
            }
            if (!isGranted) {
                Text(description, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.width(112.dp),
                ) {
                    Text(buttonText)
                }
            }
        }
    }
}
