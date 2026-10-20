package com.shutixing.app.ui.stats

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shutixing.app.domain.model.BankMastery
import com.shutixing.app.domain.model.DailyStat
import com.shutixing.app.ui.components.AppCard
import com.shutixing.app.ui.components.GradientHeroCard
import com.shutixing.app.ui.components.SectionHeader
import com.shutixing.app.ui.components.StatTile
import com.shutixing.app.ui.theme.DeepTeal
import com.shutixing.app.ui.theme.InkBlue
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    var rangeDays by remember { mutableIntStateOf(7) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text("学习统计", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "每一道题，都算数。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            GradientHeroCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "连续打卡",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${state.streak}",
                                style = MaterialTheme.typography.displaySmall,
                                color = Color.White,
                            )
                            Text(
                                " 天",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.78f),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "累计作答",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                        Text(
                            "${state.totalAnswered} 题",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "总正确率 ${(state.overallAccuracy * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                    }
                }
            }
        }

        item {
            AppCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatTile("题库总量", "${state.totalQuestions}", accent = InkBlue)
                    StatTile("已掌握", "${state.totalMastered}", accent = DeepTeal)
                    StatTile(
                        "掌握率",
                        "${((if (state.totalQuestions == 0) 0f else state.totalMastered.toFloat() / state.totalQuestions) * 100).toInt()}%",
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = "做题量",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = rangeDays == 7,
                            onClick = { rangeDays = 7 },
                            label = { Text("7 天") },
                            shape = RoundedCornerShape(999.dp),
                        )
                        FilterChip(
                            selected = rangeDays == 30,
                            onClick = { rangeDays = 30 },
                            label = { Text("30 天") },
                            shape = RoundedCornerShape(999.dp),
                        )
                    }
                },
            )
        }

        item {
            AppCard(Modifier.fillMaxWidth()) {
                BarChart(state.daily, rangeDays)
            }
        }

        item { SectionHeader(title = "正确率趋势") }

        item {
            AppCard(Modifier.fillMaxWidth()) {
                AccuracyLine(state.daily, rangeDays)
            }
        }

        item { SectionHeader(title = "本月打卡") }

        item {
            AppCard(Modifier.fillMaxWidth()) {
                MonthHeatmap(state.daily)
            }
        }

        if (state.banks.isNotEmpty()) {
            item { SectionHeader(title = "题库掌握度") }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        state.banks.forEach { bank ->
                            BankMasteryRow(bank)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarChart(daily: List<DailyStat>, days: Int) {
    val end = LocalDate.now()
    val series = (days - 1 downTo 0).map { offset -> end.minusDays(offset.toLong()) }
        .map { date -> date to (daily.firstOrNull { it.date == date }?.answered ?: 0) }
    val max = (series.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val labelFormatter = DateTimeFormatter.ofPattern("E", Locale.CHINA)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(if (days > 14) 2.dp else 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        series.forEach { (date, count) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fraction = count.toFloat() / max
                val height by animateDpAsState(
                    targetValue = (70 * fraction).dp.coerceAtLeast(3.dp),
                    animationSpec = tween(600),
                    label = "barHeight",
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(if (days > 14) 6.dp else 18.dp)
                        .height(height)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (count == 0) {
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                )
                            } else {
                                Brush.verticalGradient(listOf(InkBlue, DeepTeal))
                            },
                        ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (days <= 7) date.format(labelFormatter) else "${date.dayOfMonth}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AccuracyLine(daily: List<DailyStat>, days: Int) {
    val end = LocalDate.now()
    val values = (days - 1 downTo 0).map { offset ->
        daily.firstOrNull { it.date == end.minusDays(offset.toLong()) }?.accuracy ?: 0f
    }
    val lineColor = InkBlue
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (values.size < 2) return@Canvas
        val step = size.width / (values.size - 1)
        val points = values.mapIndexed { index, value ->
            Offset(index * step, size.height - value * size.height * 0.88f - 4f)
        }
        val path = Path().apply {
            points.forEachIndexed { index, point ->
                if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
        }
        drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        points.forEach { drawCircle(lineColor, radius = 3.5.dp.toPx(), center = it) }
    }
}

@Composable
private fun MonthHeatmap(daily: List<DailyStat>) {
    val today = LocalDate.now()
    val month = YearMonth.from(today)
    val counts = daily.associate { it.date to it.answered }
    val offset = month.atDay(1).dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val rows = ((offset + daysInMonth) + 6) / 7
    val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    Column {
        Row(Modifier.fillMaxWidth()) {
            weekLabels.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val day = row * 7 + column - offset + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .aspectRatio(1f),
                    ) {
                        if (day in 1..daysInMonth) {
                            val date = month.atDay(day)
                            val count = counts[date] ?: 0
                            val intensity = (count / 40f).coerceIn(0f, 1f)
                            val background = when {
                                count == 0 -> MaterialTheme.colorScheme.surfaceVariant
                                else -> InkBlue.copy(alpha = 0.18f + 0.72f * intensity)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(background),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$day",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (intensity > 0.55f) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BankMasteryRow(bank: BankMastery) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(
                bank.bankName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${bank.mastered} / ${bank.total}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { bank.mastery },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
        )
    }
}
