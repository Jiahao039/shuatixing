package com.shutixing.app.domain.model

import java.time.LocalDate

data class DailyStat(
    val date: LocalDate,
    val answered: Int,
    val correct: Int,
) {
    val accuracy: Float get() = if (answered == 0) 0f else correct.toFloat() / answered
}

data class BankMastery(
    val bankId: String,
    val bankName: String,
    val total: Int,
    val mastered: Int,
) {
    val mastery: Float get() = if (total == 0) 0f else mastered.toFloat() / total
}
