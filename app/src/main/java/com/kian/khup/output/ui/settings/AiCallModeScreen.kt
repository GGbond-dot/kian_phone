package com.kian.khup.output.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kian.khup.core.ai.AiProviderMode
import com.kian.khup.output.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCallModeScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.aiSettings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("调用模式") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ProviderModeRow("本地优先", AiProviderMode.LocalFirst, settings.providerMode, viewModel::setProviderMode)
            ProviderModeRow("仅本地", AiProviderMode.LocalOnly, settings.providerMode, viewModel::setProviderMode)
            ProviderModeRow("仅 API", AiProviderMode.ApiOnly, settings.providerMode, viewModel::setProviderMode)
        }
    }
}

@Composable
private fun ProviderModeRow(
    label: String,
    mode: AiProviderMode,
    current: AiProviderMode,
    onModeChange: (AiProviderMode) -> Unit,
) {
    TextButton(onClick = { onModeChange(mode) }) {
        Text(
            text = if (mode == current) "✓ $label" else label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (mode == current) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
