package com.shutixing.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shutixing.app.ui.components.AppCard
import com.shutixing.app.ui.components.GradientButton
import com.shutixing.app.ui.components.GradientHeroCard
import com.shutixing.app.ui.components.ProgressRing
import com.shutixing.app.ui.components.QuickStatRow
import com.shutixing.app.ui.components.SectionHeader
import com.shutixing.app.ui.theme.AmberGradientEnd
import com.shutixing.app.ui.theme.AmberGradientStart
import com.shutixing.app.ui.theme.DeepTeal
import com.shutixing.app.ui.theme.InkBlue
import com.shutixing.app.ui.theme.ShuaTiXingTheme
import com.shutixing.app.ui.theme.WrongRed
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartDaily: () -> Unit,
    onContinueSession: () -> Unit,
    onOpenWrongBook: () -> Unit,
    onOpenBanks: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    HomeContent(
        state = state,
        onStartDaily = onStartDaily,
        onContinueSession = onContinueSession,
        onOpenWrongBook = onOpenWrongBook,
        onOpenBanks = onOpenBanks,
        onOpenImport = onOpenImport,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onStartDaily: () -> Unit,
    onContinueSession: () -> Unit,
    onOpenWrongBook: () -> Unit,
    onOpenBanks: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(), style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formattedDate(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "设置")
                }
            }
        }

        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 8 },
            ) {
                HeroSection(state, onStartDaily, onContinueSession)
            }
        }

        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(450, delayMillis = 80)) +
                    slideInVertically(tween(450, delayMillis = 80)) { it / 8 },
            ) {
                AppCard(Modifier.fillMaxWidth()) {
                    QuickStatRow(
                        accuracy = state.todayAccuracy,
                        hasAnswers = state.todayAnswered > 0,
                        wrongCount = state.wrongCount,
                        mastered = state.mastered,
                    )
                }
            }
        }

        item {
            SectionHeader(title = "快捷入口")
        }

        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(450, delayMillis = 160)) +
                    slideInVertically(tween(450, delayMillis = 160)) { it / 8 },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickActionCard(
                        label = "错题本",
                        caption = "${state.wrongCount} 题待复习",
                        icon = Icons.Rounded.ErrorOutline,
                        accent = WrongRed,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenWrongBook,
                    )
                    QuickActionCard(
                        label = "题库",
                        caption = "${state.total} 题",
                        icon = Icons.Rounded.MenuBook,
                        accent = InkBlue,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenBanks,
                    )
                    QuickActionCard(
                        label = "导入",
                        caption = "PDF 生成题库",
                        icon = Icons.Rounded.FileUpload,
                        accent = DeepTeal,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenImport,
                    )
                }
            }
        }

        item {
            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("每日一点点", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "坚持刷题，让记忆按节奏生长。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSection(
    state: HomeUiState,
    onStartDaily: () -> Unit,
    onContinueSession: () -> Unit,
) {
    val remaining = (state.dailyTarget - state.todayAnswered).coerceAtLeast(0)
    GradientHeroCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "今日目标",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${state.todayAnswered}",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                        )
                        Text(
                            " / ${state.dailyTarget} 题",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.78f),
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color(0xFFFFC44D),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "连续 ${state.streak} 天",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = state.progress,
                    diameter = 116.dp,
                    strokeWidth = 10.dp,
                    trackColor = Color.White.copy(alpha = 0.18f),
                    brush = Brush.linearGradient(listOf(Color(0xFFFFD36E), Color(0xFFFFA94D))),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                        )
                        Text(
                            "完成",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (remaining > 0) "还差 $remaining 题" else "今日任务已完成",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (state.hasSession) "上次进度已保存" else "按计划推进，稳扎稳打",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                    Spacer(Modifier.height(14.dp))
                    GradientButton(
                        text = if (state.hasSession) "继续本轮" else "开始刷题",
                        onClick = if (state.hasSession) onContinueSession else onStartDaily,
                        brush = Brush.horizontalGradient(listOf(AmberGradientStart, AmberGradientEnd)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    label: String,
    caption: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AppCard(modifier = modifier.clickable(onClick = onClick)) {
        Column {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "早上好"
    in 12..17 -> "下午好"
    else -> "晚上好"
}

private fun formattedDate(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomePreview() {
    ShuaTiXingTheme {
        HomeContent(
            state = HomeUiState(
                loading = false,
                dailyTarget = 30,
                todayAnswered = 12,
                todayCorrect = 9,
                streak = 6,
                wrongCount = 8,
                mastered = 120,
                total = 300,
                hasSession = false,
            ),
            onStartDaily = {},
            onContinueSession = {},
            onOpenWrongBook = {},
            onOpenBanks = {},
            onOpenImport = {},
            onOpenSettings = {},
        )
    }
}
