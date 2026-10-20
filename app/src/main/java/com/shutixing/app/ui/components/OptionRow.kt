package com.shutixing.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shutixing.app.ui.theme.AppMotion
import com.shutixing.app.ui.theme.CorrectGreen
import com.shutixing.app.ui.theme.WrongRed

enum class OptionVisualState { IDLE, SELECTED, CORRECT, WRONG, DISABLED }

@Composable
fun OptionRow(
    key: String,
    text: String,
    state: OptionVisualState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val targetBorder = when (state) {
        OptionVisualState.SELECTED -> colors.primary
        OptionVisualState.CORRECT -> CorrectGreen
        OptionVisualState.WRONG -> WrongRed
        else -> colors.outlineVariant
    }
    val targetBackground = when (state) {
        OptionVisualState.SELECTED -> colors.primary.copy(alpha = 0.08f)
        OptionVisualState.CORRECT -> CorrectGreen.copy(alpha = 0.10f)
        OptionVisualState.WRONG -> WrongRed.copy(alpha = 0.10f)
        else -> colors.surface
    }
    val border by animateColorAsState(targetBorder, tween(AppMotion.QUICK), label = "optionBorder")
    val background by animateColorAsState(targetBackground, tween(AppMotion.QUICK), label = "optionBackground")
    val scale by animateFloatAsState(
        if (state == OptionVisualState.SELECTED) 1.01f else 1f,
        AppMotion.spring,
        label = "optionScale",
    )
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state) {
        if (state == OptionVisualState.WRONG) {
            repeat(3) {
                shake.animateTo(7f, tween(45))
                shake.animateTo(-7f, tween(45))
            }
            shake.animateTo(0f, tween(45))
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .offset(x = shake.value.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .border(1.5.dp, border, RoundedCornerShape(18.dp))
            .clickable(enabled = state != OptionVisualState.DISABLED, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val badgeColor = when (state) {
            OptionVisualState.SELECTED -> colors.primary
            OptionVisualState.CORRECT -> CorrectGreen
            OptionVisualState.WRONG -> WrongRed
            else -> colors.surfaceVariant
        }
        val badgeTextColor = when (state) {
            OptionVisualState.IDLE, OptionVisualState.DISABLED -> colors.onSurfaceVariant
            else -> Color.White
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(key, style = MaterialTheme.typography.labelLarge, color = badgeTextColor)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        when (state) {
            OptionVisualState.CORRECT -> Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = "正确",
                tint = CorrectGreen,
            )
            OptionVisualState.WRONG -> Icon(
                Icons.Rounded.Cancel,
                contentDescription = "错误",
                tint = WrongRed,
            )
            else -> Unit
        }
    }
}
