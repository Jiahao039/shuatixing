package com.shutixing.app.domain.stats

import com.shutixing.app.domain.model.DailyStat
import java.time.LocalDate

object StatsCalculator {
    fun streak(activeDates: Collection<LocalDate>, today: LocalDate): Int {
        val set = activeDates.toSet()
        var cursor = when {
            today in set -> today
            today.minusDays(1) in set -> today.minusDays(1)
            else -> return 0
        }
        var count = 0
        while (cursor in set) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    fun accuracy(stats: List<DailyStat>): Float {
        val answered = stats.sumOf { it.answered }
        if (answered == 0) return 0f
        return stats.sumOf { it.correct }.toFloat() / answered
    }
}
