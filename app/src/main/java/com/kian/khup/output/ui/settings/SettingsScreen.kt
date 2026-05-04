package com.kian.khup.output.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val nlsEnabled = remember(refreshTick) { NotificationPermissions.isNotificationListenerEnabled(context) }
    val usageEnabled = remember(refreshTick) { NotificationPermissions.hasUsageAccess(context) }
    val overlayEnabled = remember(refreshTick) { NotificationPermissions.hasOverlayPermission(context) }

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
            granted = nlsEnabled,
            onClick = { NotificationPermissions.openNotificationListenerSettings(context) },
        )

        PermissionCard(
            title = "用机时长统计（Phase 3）",
            description = "授权后 KHUP 才能统计每个 App 的前台时长和打开次数。",
            granted = usageEnabled,
            onClick = { NotificationPermissions.openUsageAccessSettings(context) },
        )

        PermissionCard(
            title = "悬浮窗（Phase 4）",
            description = "拦截抖音/小红书等算法推送时弹出强制等待卡片所必需。MIUI 默认禁用，请手动打开。",
            granted = overlayEnabled,
            onClick = { NotificationPermissions.openOverlaySettings(context) },
        )

        Text(
            "MIUI 保活提示",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "为防止系统杀掉通知监听服务，请到「设置 → 应用管理 → KHUP → 省电策略」选择「无限制」，并打开「自启动」。",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (granted) "✓ 已授权" else "✗ 未授权",
                style = MaterialTheme.typography.labelLarge,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Button(onClick = onClick, enabled = !granted) {
                Text(if (granted) "已开启" else "去开启")
            }
        }
    }
}
