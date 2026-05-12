package com.kian.khup.output.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kian.khup.output.ui.theme.Spacing
import com.kian.khup.output.ui.theme.Success
import com.kian.khup.output.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiLocalModelScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val modelState by viewModel.aiModelState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("本地模型") },
                actions = {
                    IconButton(onClick = viewModel::refreshAiModelState) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新模型状态")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = if (modelState.isReady) "✓ 已就绪" else "⚠ 未找到",
                style = MaterialTheme.typography.titleMedium,
                color = if (modelState.isReady) Success else Warning,
            )
            modelState.foundPath?.let { path ->
                Text(path, style = MaterialTheme.typography.bodyMedium)
            }
            if (!modelState.isReady) {
                Text(
                    "把模型 push 到下列任一路径之后，点右上角刷新：",
                    style = MaterialTheme.typography.bodyMedium,
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
        }
    }
}
