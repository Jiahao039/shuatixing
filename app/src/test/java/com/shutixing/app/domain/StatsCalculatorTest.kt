package com.shutixing.app.domain

import com.shutixing.app.domain.model.DailyStat
import com.shutixing.app.domain.stats.StatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsCalculatorTest {
    private val today = LocalDate.of(2026, 10, 20)

    @Test
    fun `streak counts consecutive days ending today`() {
        val dates = listOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(3, StatsCalculator.streak(dates, today))
    }

    @Test
    fun `streak may end yesterday`() {
        val dates = listOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, StatsCalculator.streak(dates, today))
    }

    @Test
    fun `streak stops at a gap`() {
        val dates = listOf(today, today.minusDays(2), today.minusDays(3))
        assertEquals(1, StatsCalculator.streak(dates, today))
    }

    @Test
    fun `no activity means zero streak`() {
        assertEquals(0, StatsCalculator.streak(emptyList(), today))
    }

    @Test
    fun `accuracy handles empty and non-empty`() {
        assertEquals(0f, StatsCalculator.accuracy(emptyList()), 0.0001f)
        val stats = listOf(DailyStat(today, answered = 10, correct = 7))
        assertEquals(0.7f, StatsCalculator.accuracy(stats), 0.0001f)
    }

    @Test
    fun `accuracy aggregates across days`() {
        val stats = listOf(
            DailyStat(today, answered = 10, correct = 8),
            DailyStat(today.minusDays(1), answered = 10, correct = 6),
        )
        assertEquals(0.7f, StatsCalculator.accuracy(stats), 0.0001f)
    }
}
