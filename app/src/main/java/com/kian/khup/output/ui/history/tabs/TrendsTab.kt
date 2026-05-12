package com.kian.khup.output.ui.history.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kian.khup.output.ui.history.HistoryViewModel.TrendsData

@Composable
fun TrendsTab(
    trends: TrendsData,
    periodDays: Int,
    onPeriodChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        PeriodToggle(periodDays = periodDays, onPeriodChange = onPeriodChange)

        SummaryRow("${periodDays}天写过几段", trends.checkInCount.toString()) // TODO: strings.xml
        SummaryRow(
            label = "已接受比例",
            value = if (trends.totalFeedbackCount > 0)
                "${trends.acceptedCount * 100 / trends.totalFeedbackCount}%"
            else "—",
        )
        SummaryRow("接受了 / 共", "${trends.acceptedCount} / ${trends.totalFeedbackCount}") // TODO

        if (trends.screenTimeByDay.isNotEmpty()) {
            Text(
                text = "每日屏幕时间（分钟）", // TODO: strings.xml
                style = MaterialTheme.typography.titleSmall,
            )
            LineChart(
                data = trends.screenTimeByDay.map { it.foregroundMs / 60_000f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            )
        }
    }
}

@Composable
private fun PeriodToggle(
    periodDays: Int,
    onPeriodChange: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = periodDays == 7,
            onClick = { onPeriodChange(7) },
            label = { Text("7天") }, // TODO: strings.xml
        )
        FilterChip(
            selected = periodDays == 30,
            onClick = { onPeriodChange(30) },
            label = { Text("30天") }, // TODO: strings.xml
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun LineChart(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF6650A4),
) {
    if (data.size < 2) return
    val maxVal = data.max().coerceAtLeast(1f)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stepX = w / (data.size - 1)
        val path = Path()
        data.forEachIndexed { i, v ->
            val x = i * stepX
            val y = h - (v / maxVal) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 3.dp.toPx()))
        data.forEachIndexed { i, v ->
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = Offset(i * stepX, h - (v / maxVal) * h),
            )
        }
    }
}
