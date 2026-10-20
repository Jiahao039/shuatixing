package com.shutixing.app.domain

import com.shutixing.app.domain.model.ReviewState
import com.shutixing.app.domain.review.Scheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SchedulerTest {
    private val today = LocalDate.of(2026, 10, 20)
    private val now = 1_761_000_000_000L

    private fun state(level: Int = 0, consecutive: Int = 0, wrong: Int = 0) = ReviewState(
        questionId = "q1",
        level = level,
        nextReviewDate = today,
        consecutiveCorrect = consecutive,
        wrongCount = wrong,
    )

    @Test
    fun `first correct answer moves to level one and schedules tomorrow`() {
        val next = Scheduler.onAnswer(state(), correct = true, today = today, answeredAt = now)
        assertEquals(1, next.level)
        assertEquals(1, next.consecutiveCorrect)
        assertEquals(today.plusDays(1), next.nextReviewDate)
        assertEquals(now, next.lastAnsweredAt)
    }

    @Test
    fun `correct answers follow the interval table`() {
        assertEquals(today.plusDays(2), Scheduler.onAnswer(state(level = 1), true, today, now).nextReviewDate)
        assertEquals(today.plusDays(4), Scheduler.onAnswer(state(level = 2), true, today, now).nextReviewDate)
        assertEquals(today.plusDays(7), Scheduler.onAnswer(state(level = 3), true, today, now).nextReviewDate)
        assertEquals(today.plusDays(15), Scheduler.onAnswer(state(level = 4), true, today, now).nextReviewDate)
        assertEquals(today.plusDays(30), Scheduler.onAnswer(state(level = 5), true, today, now).nextReviewDate)
    }

    @Test
    fun `mastered question stays at level six`() {
        val next = Scheduler.onAnswer(state(level = 6, consecutive = 5), true, today, now)
        assertEquals(6, next.level)
        assertEquals(today.plusDays(30), next.nextReviewDate)
        assertTrue(next.isMastered)
    }

    @Test
    fun `wrong answer resets level and schedules tomorrow`() {
        val next = Scheduler.onAnswer(state(level = 4, consecutive = 3, wrong = 1), false, today, now)
        assertEquals(0, next.level)
        assertEquals(0, next.consecutiveCorrect)
        assertEquals(2, next.wrongCount)
        assertEquals(today.plusDays(1), next.nextReviewDate)
    }

    @Test
    fun `cross month scheduling uses real calendar`() {
        val oct31 = LocalDate.of(2026, 10, 31)
        val next = Scheduler.onAnswer(state(level = 1), true, oct31, now)
        assertEquals(LocalDate.of(2026, 11, 2), next.nextReviewDate)
    }
}
