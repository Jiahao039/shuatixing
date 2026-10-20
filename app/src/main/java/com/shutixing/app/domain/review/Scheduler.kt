package com.shutixing.app.domain.review

import com.shutixing.app.domain.model.ReviewLevel
import com.shutixing.app.domain.model.ReviewState
import java.time.LocalDate

object Scheduler {
    /** 索引 0..5 对应等级 1..6 的间隔天数。 */
    val INTERVALS = intArrayOf(1, 2, 4, 7, 15, 30)

    fun intervalDaysForLevel(level: Int): Int =
        INTERVALS[(level - 1).coerceIn(0, INTERVALS.size - 1)]

    fun onAnswer(
        state: ReviewState,
        correct: Boolean,
        today: LocalDate,
        answeredAt: Long,
    ): ReviewState = if (correct) {
        val newLevel = (state.level + 1).coerceAtMost(ReviewLevel.MASTERED)
        state.copy(
            level = newLevel,
            consecutiveCorrect = state.consecutiveCorrect + 1,
            nextReviewDate = today.plusDays(intervalDaysForLevel(newLevel).toLong()),
            lastAnsweredAt = answeredAt,
        )
    } else {
        state.copy(
            level = 0,
            consecutiveCorrect = 0,
            wrongCount = state.wrongCount + 1,
            nextReviewDate = today.plusDays(1),
            lastAnsweredAt = answeredAt,
        )
    }
}
