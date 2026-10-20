package com.shutixing.app.data.repository

import androidx.room.withTransaction
import com.shutixing.app.data.db.AppDatabase
import com.shutixing.app.data.db.BankEntity
import com.shutixing.app.data.db.QuestionEntity
import com.shutixing.app.data.db.ReviewStateEntity
import com.shutixing.app.data.db.toDomain
import com.shutixing.app.data.db.toEntity
import com.shutixing.app.domain.model.Bank
import com.shutixing.app.domain.model.Question
import com.shutixing.app.parser.StemHasher
import com.shutixing.app.parser.model.ParsedQuestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

class BankRepository(private val db: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true }

    fun observeBanks(): Flow<List<Bank>> =
        db.bankDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeQuestions(bankId: String): Flow<List<Question>> =
        db.questionDao().observeByBank(bankId).map { rows -> rows.map { it.toDomain() } }

    suspend fun allBanks(): List<Bank> = db.bankDao().getAll().map { it.toDomain() }

    suspend fun getBank(id: String): Bank? = db.bankDao().get(id)?.toDomain()

    suspend fun countQuestions(bankId: String): Int = db.questionDao().countByBank(bankId)

    suspend fun countMastered(bankId: String): Int = db.questionDao().masteredByBank(bankId)

    suspend fun createBank(
        name: String,
        sourceFileName: String?,
        questions: List<ParsedQuestion>,
    ): String = db.withTransaction {
        val bankId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val sortOrder = (db.bankDao().getAll().maxOfOrNull { it.sortOrder } ?: -1) + 1
        db.bankDao().insert(BankEntity(bankId, name, sourceFileName, now, sortOrder))
        insertQuestions(bankId, questions, now, startIndex = 0)
        bankId
    }

    suspend fun appendQuestions(bankId: String, questions: List<ParsedQuestion>) = db.withTransaction {
        val start = db.questionDao().countByBank(bankId)
        insertQuestions(bankId, questions, System.currentTimeMillis(), startIndex = start)
    }

    private suspend fun insertQuestions(
        bankId: String,
        parsed: List<ParsedQuestion>,
        now: Long,
        startIndex: Int,
    ) {
        val today = LocalDate.now().toString()
        val entities = parsed.mapIndexed { index, item ->
            QuestionEntity(
                id = UUID.randomUUID().toString(),
                bankId = bankId,
                type = item.type.name,
                stem = item.stem,
                optionsJson = json.encodeToString(item.options),
                answer = item.answer,
                explanation = item.explanation,
                orderIndex = startIndex + index,
                stemHash = StemHasher.hash(item.stem, item.options),
                isFavorite = false,
                createdAt = now,
            )
        }
        if (entities.isEmpty()) return
        db.questionDao().insertAll(entities)
        db.reviewDao().insertAll(
            entities.map { ReviewStateEntity(it.id, 0, today, 0, 0, null) },
        )
    }

    suspend fun deleteBank(id: String) = db.bankDao().delete(id)

    suspend fun renameBank(id: String, name: String) {
        val bank = db.bankDao().get(id) ?: return
        db.bankDao().update(bank.copy(name = name))
    }

    suspend fun getQuestion(id: String): Question? = db.questionDao().get(id)?.toDomain()

    suspend fun updateQuestion(question: Question) = db.questionDao().update(question.toEntity())

    suspend fun deleteQuestion(id: String) = db.questionDao().delete(id)

    suspend fun setFavorite(id: String, favorite: Boolean) {
        val entity = db.questionDao().get(id) ?: return
        db.questionDao().update(entity.copy(isFavorite = favorite))
    }

    suspend fun addQuestion(bankId: String, parsed: ParsedQuestion): Question = db.withTransaction {
        val order = db.questionDao().countByBank(bankId)
        val now = System.currentTimeMillis()
        val entity = QuestionEntity(
            id = UUID.randomUUID().toString(),
            bankId = bankId,
            type = parsed.type.name,
            stem = parsed.stem,
            optionsJson = json.encodeToString(parsed.options),
            answer = parsed.answer,
            explanation = parsed.explanation,
            orderIndex = order,
            stemHash = StemHasher.hash(parsed.stem, parsed.options),
            isFavorite = false,
            createdAt = now,
        )
        db.questionDao().insertAll(listOf(entity))
        db.reviewDao().insert(
            ReviewStateEntity(entity.id, 0, LocalDate.now().toString(), 0, 0, null),
        )
        entity.toDomain()
    }

    suspend fun exportData(bankId: String): Pair<Bank, List<Question>>? {
        val bank = db.bankDao().get(bankId)?.toDomain() ?: return null
        return bank to db.questionDao().getByBank(bankId).map { it.toDomain() }
    }

    suspend fun findByHash(hash: String): Int = db.questionDao().countByHash(hash)

    suspend fun allHashes(): Set<String> = db.questionDao().allHashes().toSet()
}
