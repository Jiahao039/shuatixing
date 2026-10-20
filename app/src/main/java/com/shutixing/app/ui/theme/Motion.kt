package com.shutixing.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object AppMotion {
    const val QUICK = 150
    const val MEDIUM = 260
    const val SLOW = 700

    val spring = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
    val settle = tween<Float>(durationMillis = SLOW, easing = FastOutSlowInEasing)
}
