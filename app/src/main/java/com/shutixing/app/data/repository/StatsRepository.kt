package com.shutixing.app.data.repository

import com.shutixing.app.data.db.AppDatabase
import com.shutixing.app.domain.model.BankMastery
import com.shutixing.app.domain.model.DailyStat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class StatsRepository(private val db: AppDatabase) {
    fun observeDailyStats(): Flow<List<DailyStat>> =
        db.attemptDao().observeDailyStats().map { rows ->
            rows.map { DailyStat(LocalDate.parse(it.localDate), it.answered, it.correct) }
        }

    suspend fun activeDates(): List<LocalDate> =
        db.attemptDao().activeDates().map { LocalDate.parse(it) }

    suspend fun todayAnswered(today: LocalDate): Int = db.attemptDao().countByDate(today.toString())

    suspend fun todayCorrect(today: LocalDate): Int = db.attemptDao().correctByDate(today.toString())

    suspend fun totalQuestions(): Int = db.questionDao().countAll()

    suspend fun totalMastered(): Int =
        db.bankDao().getAll().sumOf { db.questionDao().masteredByBank(it.id) }

    suspend fun bankMastery(): List<BankMastery> = db.bankDao().getAll().map { bank ->
        BankMastery(
            bankId = bank.id,
            bankName = bank.name,
            total = db.questionDao().countByBank(bank.id),
            mastered = db.questionDao().masteredByBank(bank.id),
            attempted = db.questionDao().attemptedByBank(bank.id),
        )
    }
}
