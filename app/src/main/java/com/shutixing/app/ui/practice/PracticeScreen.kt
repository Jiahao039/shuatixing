package com.shutixing.app.ui.practice

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shutixing.app.domain.model.Option
import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.ui.components.AppCard
import com.shutixing.app.ui.components.EmptyState
import com.shutixing.app.ui.components.GradientButton
import com.shutixing.app.ui.components.OptionRow
import com.shutixing.app.ui.components.OptionVisualState
import com.shutixing.app.ui.components.PillChip
import com.shutixing.app.ui.components.QuestionTypeBadge
import com.shutixing.app.ui.result.ResultScreen
import com.shutixing.app.ui.theme.AmberGradientEnd
import com.shutixing.app.ui.theme.AmberGradientStart
import com.shutixing.app.ui.theme.CorrectGreen
import com.shutixing.app.ui.theme.DeepTeal
import com.shutixing.app.ui.theme.InkBlue
import com.shutixing.app.ui.theme.WrongRed

@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel,
    mode: PracticeMode,
    onExit: () -> Unit,
    onOpenImport: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(mode) { viewModel.start(mode) }

    when {
        state.loading -> LoadingBox("正在组卷…")
        state.error != null -> CenteredError(state.error.orEmpty(), onExit)
        state.empty -> EmptyState(
            icon = Icons.Rounded.Inbox,
            title = "今天没有要刷的题",
            message = if (state.mode == PracticeMode.DAILY) {
                "导入一份 PDF 题库，或调整每日题量后再来。"
            } else {
                "错题本还是空的，继续保持。"
            },
            actionLabel = if (state.mode == PracticeMode.DAILY) "去导入题库" else null,
            onAction = if (state.mode == PracticeMode.DAILY) onOpenImport else null,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        )
        state.finished -> ResultScreen(
            totalAnswered = state.totalAnswered,
            correctCount = state.correctCount,
            onDone = onExit,
            onRetryWrong = { viewModel.start(PracticeMode.WRONG_BOOK) },
        )
        else -> PracticeContent(
            state = state,
            onSelect = viewModel::select,
            onSubmit = viewModel::submit,
            onNext = viewModel::next,
            onExit = onExit,
        )
    }
}

@Composable
private fun PracticeContent(
    state: PracticeUiState,
    onSelect: (String) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
) {
    var showExitDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    BackHandler { showExitDialog = true }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { showExitDialog = true }) {
                Icon(Icons.Rounded.Close, contentDescription = "退出")
            }
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (state.inRetest) "错题重测" else "今日刷题",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${state.answeredMain} / ${state.mainTotal}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                )
            }
        }

        AnimatedVisibility(visible = state.inRetest, enter = fadeIn(tween(200)) + expandVertically(tween(220))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Replay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "答错的题会一直重测，直到答对为止 · 剩余 ${state.retestRemaining}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        AnimatedContent(
            targetState = state.current,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 24 })
                    .togetherWith(fadeOut(tween(120)))
            },
            label = "question",
        ) { question ->
            if (question != null) {
                QuestionBody(
                    question = question,
                    state = state,
                    onSelect = { key ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(key)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            when {
                state.submitted -> GradientButton(
                    text = "下一题",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNext()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    brush = Brush.horizontalGradient(listOf(InkBlue, DeepTeal)),
                )
                state.current?.type == QuestionType.MULTIPLE -> GradientButton(
                    text = "提交答案",
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSubmit()
                    },
                    enabled = state.selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    brush = Brush.horizontalGradient(listOf(AmberGradientStart, AmberGradientEnd)),
                )
                else -> AutoSubmitHint()
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("退出本轮？") },
            text = { Text("进度已自动保存，下次进入可以继续。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        onExit()
                    },
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("继续刷题") }
            },
        )
    }
}

@Composable
private fun QuestionBody(
    question: Question,
    state: PracticeUiState,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = displayOptions(question)
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QuestionTypeBadge(question.type)
                if (state.inRetest) {
                    Spacer(Modifier.width(8.dp))
                    PillChip(
                        text = "重测",
                        container = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                        contentColor = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = question.stem,
                style = MaterialTheme.typography.titleLarge.copy(lineHeight = 32.sp),
            )
        }
        items(options, key = { it.key }) { option ->
            OptionRow(
                key = displayKey(question, option.key),
                text = option.text,
                state = optionVisualState(question, option, state),
                onClick = { onSelect(option.key) },
            )
        }
        item {
            AnimatedVisibility(
                visible = state.submitted,
                enter = fadeIn(tween(220)) + expandVertically(tween(260)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
            ) {
                FeedbackCard(question, state)
            }
        }
    }
}

@Composable
private fun FeedbackCard(question: Question, state: PracticeUiState) {
    val correct = state.lastCorrect == true
    AppCard(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (correct) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                    contentDescription = null,
                    tint = if (correct) CorrectGreen else WrongRed,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (correct) "回答正确" else "回答错误",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (correct) CorrectGreen else WrongRed,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "正确答案：${displayAnswer(question)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            question.explanation?.takeIf { it.isNotBlank() }?.let { explanation ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AutoSubmitHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.TouchApp,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "选择后自动判分",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingBox(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CenteredError(message: String, onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("加载失败", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        GradientButton(text = "返回", onClick = onExit)
    }
}

private fun displayOptions(question: Question): List<Option> =
    if (question.type == QuestionType.JUDGE) {
        listOf(Option("T", "正确"), Option("F", "错误"))
    } else {
        question.options
    }

private fun displayKey(question: Question, key: String): String =
    if (question.type == QuestionType.JUDGE) {
        if (key == "T") "对" else "错"
    } else {
        key
    }

private fun displayAnswer(question: Question): String =
    if (question.type == QuestionType.JUDGE) {
        if (question.answer == "T") "正确" else "错误"
    } else {
        question.answer.toList().joinToString("、")
    }

private fun optionVisualState(
    question: Question,
    option: Option,
    state: PracticeUiState,
): OptionVisualState {
    val isSelected = option.key in state.selected
    val isCorrectOption = question.answer.contains(option.key)
    return when {
        state.submitted && isCorrectOption -> OptionVisualState.CORRECT
        state.submitted && isSelected -> OptionVisualState.WRONG
        state.submitted -> OptionVisualState.DISABLED
        isSelected -> OptionVisualState.SELECTED
        else -> OptionVisualState.IDLE
    }
}
