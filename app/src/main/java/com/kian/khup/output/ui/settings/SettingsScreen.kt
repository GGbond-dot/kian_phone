package com.kian.khup.output.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import com.kian.khup.collection.notification.NotificationPermissions

/**
 * 权限引导主页面。MIUI 上每个权限都需要用户手动到设置里开，
 * 这个页面负责检测当前状态、提供「去开启」按钮、给出 MIUI 二次确认提示。
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
    val usageEnabled = remember(refreshTick) { NotificationPermissions.hasUsageAccess(context) }
    val overlayEnabled = remember(refreshTick) { NotificationPermissions.hasOverlayPermission(context) }
    val autostartConfirmed = remember(refreshTick) { NotificationPermissions.isMiuiAutostartConfirmed(context) }
    val batteryConfirmed = remember(refreshTick) { NotificationPermissions.isMiuiBatteryConfirmed(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("权限设置", style = MaterialTheme.typography.headlineSmall)

        PermissionCard(
            title = "通知使用权（必需）",
            description = "授权后 KHUP 才能读取其他 App 的通知。MIUI 上开启后还会有二次确认弹窗，请记得勾选「允许读取通知」。",
            status = PermissionStatus.Checked(nlsEnabled),
            onClick = { NotificationPermissions.openNotificationListenerSettings(context) },
        )

        PermissionCard(
            title = "使用情况访问",
            description = "授权后 KHUP 才能统计每个 App 的前台时长和打开次数。",
            status = PermissionStatus.Checked(usageEnabled),
            onClick = { NotificationPermissions.openUsageAccessSettings(context) },
        )

        PermissionCard(
            title = "悬浮窗权限",
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
    }
}

private sealed interface PermissionStatus {
    data class Checked(val granted: Boolean) : PermissionStatus
    data class ManualCheck(val confirmed: Boolean) : PermissionStatus
}

private enum class ManualPermission {
    MiuiAutostart,
    MiuiBattery,
}

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
        is PermissionStatus.Checked -> if (status.granted) "✓ 已授权" else "✗ 未授权"
        is PermissionStatus.ManualCheck -> if (status.confirmed) "✓ 已确认" else "需手动确认"
    }
    val statusColor = when (status) {
        is PermissionStatus.Checked -> {
            if (status.granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        }
        is PermissionStatus.ManualCheck -> {
            if (status.confirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
        }
    }
    val buttonText = when {
        isGranted && status is PermissionStatus.ManualCheck -> "已确认"
        isGranted -> "已开启"
        status is PermissionStatus.ManualCheck -> "去确认"
        else -> "去开启"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                )
            }
            Text(description, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = onClick,
                enabled = !isGranted,
                modifier = Modifier.width(112.dp),
            ) {
                Text(buttonText)
            }
        }
    }
}
