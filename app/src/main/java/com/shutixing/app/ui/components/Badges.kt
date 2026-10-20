package com.shutixing.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.ui.theme.Amber
import com.shutixing.app.ui.theme.DeepTeal
import com.shutixing.app.ui.theme.InkBlue

@Composable
fun QuestionTypeBadge(type: QuestionType) {
    val (label, color) = when (type) {
        QuestionType.SINGLE -> "单选" to InkBlue
        QuestionType.MULTIPLE -> "多选" to DeepTeal
        QuestionType.JUDGE -> "判断" to Amber
    }
    PillChip(
        text = label,
        container = color.copy(alpha = 0.12f),
        contentColor = color,
    )
}

@Composable
fun CountBadge(count: Int, color: Color = MaterialTheme.colorScheme.primary) {
    PillChip(
        text = "$count",
        container = color.copy(alpha = 0.12f),
        contentColor = color,
    )
}
