package com.shutixing.app.ui.wrongbook

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.ui.components.AppCard
import com.shutixing.app.ui.components.EmptyState
import com.shutixing.app.ui.components.GradientButton
import com.shutixing.app.ui.components.QuestionTypeBadge
import java.time.format.DateTimeFormatter

@Composable
fun WrongBookScreen(
    viewModel: WrongBookViewModel,
    onStartSession: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("错题本", style = MaterialTheme.typography.headlineLarge)
            Text(
                "${state.items.size} 道题等待巩固",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopicFilter("全部", state.filter == null) { viewModel.setFilter(null) }
                TopicFilter("单选", state.filter == QuestionType.SINGLE) { viewModel.setFilter(QuestionType.SINGLE) }
                TopicFilter("多选", state.filter == QuestionType.MULTIPLE) { viewModel.setFilter(QuestionType.MULTIPLE) }
                TopicFilter("判断", state.filter == QuestionType.JUDGE) { viewModel.setFilter(QuestionType.JUDGE) }
            }
        }

        if (state.items.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.TaskAlt,
                title = "错题本是空的",
                message = "刷题时答错的题目会自动收进来，连续答对两次后移出。",
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.filtered, key = { it.question.id }) { item ->
                    AppCard(Modifier.fillMaxWidth()) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                QuestionTypeBadge(item.question.type)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "错 ${item.review.wrongCount} 次",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                item.question.stem,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "下次复习 " + item.review.nextReviewDate.format(
                                    DateTimeFormatter.ofPattern("M月d日"),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Box(Modifier.padding(20.dp)) {
            GradientButton(
                text = "开始错题专项",
                onClick = onStartSession,
                enabled = state.items.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TopicFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(999.dp),
        border = null,
    )
}
