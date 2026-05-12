package com.kian.khup.output.ui.usage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kian.khup.output.ui.usage.AppUsageViewModel.AppUsageUiState
import com.kian.khup.output.ui.usage.AppUsageViewModel.Period
import com.kian.khup.output.ui.usage.components.AppUsageRow
import com.kian.khup.output.ui.usage.components.UsageSummaryHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUsageScreen(
    onBack: () -> Unit,
    viewModel: AppUsageViewModel = hiltViewModel(),
) {
    val period by viewModel.period.collectAsStateWithLifecycle()
    val usageData by viewModel.usageData.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用使用时间") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PeriodTabRow(selected = period, onSelect = viewModel::selectPeriod)
            when (val state = usageData) {
                is AppUsageUiState.Loading -> Unit
                is AppUsageUiState.Ready -> {
                    val maxMs = state.apps.maxOfOrNull { it.totalMs } ?: 0L
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        item { UsageSummaryHeader(totalMs = state.totalMs) }
                        items(state.apps) { app ->
                            AppUsageRow(
                                packageName = app.packageName,
                                totalMs = app.totalMs,
                                maxMs = maxMs,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val PERIODS = listOf(Period.TODAY to "今日", Period.WEEK to "7天", Period.MONTH to "30天")

@Composable
private fun PeriodTabRow(selected: Period, onSelect: (Period) -> Unit) {
    TabRow(selectedTabIndex = PERIODS.indexOfFirst { it.first == selected }) {
        PERIODS.forEach { (period, label) ->
            Tab(
                selected = period == selected,
                onClick = { onSelect(period) },
                text = { Text(label, style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}
