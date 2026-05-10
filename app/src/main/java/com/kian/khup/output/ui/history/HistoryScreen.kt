package com.kian.khup.output.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kian.khup.output.ui.history.tabs.PatternsTab
import com.kian.khup.output.ui.history.tabs.SuggestionsTab
import com.kian.khup.output.ui.history.tabs.TrendsTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val patterns by viewModel.patternsState.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestionsState.collectAsStateWithLifecycle()
    val trends by viewModel.trendsState.collectAsStateWithLifecycle()
    val periodDays by viewModel.periodDaysState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // TODO: strings.xml
    val tabs = listOf("模式", "建议", "趋势")

    Scaffold(
        topBar = { TopAppBar(title = { Text("历史", style = MaterialTheme.typography.titleLarge) }) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label) },
                    )
                }
            }
            when (selectedTab) {
                0 -> PatternsTab(patterns = patterns, modifier = Modifier.fillMaxSize())
                1 -> SuggestionsTab(suggestions = suggestions, modifier = Modifier.fillMaxSize())
                2 -> TrendsTab(
                    trends = trends,
                    periodDays = periodDays,
                    onPeriodChange = viewModel::setPeriodDays,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
