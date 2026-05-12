package com.kian.khup.output.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kian.khup.output.ui.history.tabs.PatternsTab
import com.kian.khup.output.ui.history.tabs.SuggestionsTab
import com.kian.khup.output.ui.history.tabs.TrendsTab
import com.kian.khup.output.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateToAi: () -> Unit = {},
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val onOpenChatSession: (Long) -> Unit = { sessionId ->
        viewModel.requestOpenChatSession(sessionId)
        onNavigateToAi()
    }
    val patterns by viewModel.patternsState.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestionsState.collectAsStateWithLifecycle()
    val trends by viewModel.trendsState.collectAsStateWithLifecycle()
    val storyNarration by viewModel.storyNarrationState.collectAsStateWithLifecycle()
    val periodDays by viewModel.periodDaysState.collectAsStateWithLifecycle()
    val linkedSessions by viewModel.linkedSessionsState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("回顾", style = MaterialTheme.typography.titleLarge) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            contentPadding = PaddingValues(
                top = Spacing.lg,
                bottom = Spacing.xl,
            ),
        ) {
            item {
                SectionTitle("这 $periodDays 天的故事")
                Text(
                    text = storyNarration ?: buildStoryFallback(trends),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            item {
                SectionTitle("趋势")
                TrendsTab(
                    trends = trends,
                    periodDays = periodDays,
                    onPeriodChange = viewModel::setPeriodDays,
                )
            }

            item {
                SectionTitle("反复出现的模式")
                PatternsTab(
                    patterns = patterns,
                    onAskAi = { anomaly ->
                        viewModel.discussFromReview(anomaly)
                        onNavigateToAi()
                    },
                )
            }

            item {
                SectionTitle("这 $periodDays 天给过的建议")
                SuggestionsTab(
                    suggestions = suggestions,
                    linkedSessions = linkedSessions,
                    onOpenChatSession = onOpenChatSession,
                    onAskAi = { suggestion ->
                        viewModel.discussFromReview(suggestion)
                        onNavigateToAi()
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun buildStoryFallback(trends: HistoryViewModel.TrendsData): String =
    "这段时间你写过 ${trends.checkInCount} 段，接受了 ${trends.acceptedCount} / 共 ${trends.totalFeedbackCount} 条建议。"
