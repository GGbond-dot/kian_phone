package com.kian.khup.output.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kian.khup.output.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiApiScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.aiSettings.collectAsStateWithLifecycle()
    var baseUrl by remember(settings.apiBaseUrl) { mutableStateOf(settings.apiBaseUrl) }
    var model by remember(settings.apiModel) { mutableStateOf(settings.apiModel) }
    var key by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("API 配置") },
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
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                TextButton(
                    onClick = {
                        key = ""
                        viewModel.setApiKey("")
                    },
                ) {
                    Text("清除 API Key")
                }
                TextButton(
                    onClick = {
                        viewModel.setApiBaseUrl(baseUrl)
                        viewModel.setApiModel(model)
                        viewModel.setApiKey(key)
                    },
                ) {
                    Text("保存 API 配置")
                }
            }
        }
    }
}
