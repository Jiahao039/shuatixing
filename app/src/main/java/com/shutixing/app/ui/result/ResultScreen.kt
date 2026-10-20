package com.shutixing.app.ui.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shutixing.app.ui.components.GradientButton
import com.shutixing.app.ui.components.ProgressRing

@Composable
fun ResultScreen(
    totalAnswered: Int,
    correctCount: Int,
    onDone: () -> Unit,
    onRetryWrong: () -> Unit,
) {
    val accuracy = if (totalAnswered == 0) 0f else correctCount.toFloat() / totalAnswered
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 8 },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ProgressRing(
                    progress = accuracy,
                    diameter = 190.dp,
                    strokeWidth = 14.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${(accuracy * 100).toInt()}%",
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text(
                            "本轮正确率",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = if (accuracy >= 0.8f) "漂亮，继续保持" else "稳步前进，再练一轮",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "本轮共作答 $totalAnswered 题，答对 $correctCount 题",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
                if (totalAnswered > correctCount) {
                    GradientButton(
                        text = "复习本轮错题",
                        onClick = onRetryWrong,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("返回首页")
                }
            }
        }
    }
}
