package com.shutixing.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BankDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bank: BankEntity)

    @Update
    suspend fun update(bank: BankEntity)

    @Query("DELETE FROM banks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM banks ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<BankEntity>>

    @Query("SELECT * FROM banks ORDER BY sortOrder, createdAt")
    suspend fun getAll(): List<BankEntity>

    @Query("SELECT * FROM banks WHERE id = :id")
    suspend fun get(id: String): BankEntity?
}

@Dao
interface QuestionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuestionEntity>)

    @Update
    suspend fun update(question: QuestionEntity)

    @Query("DELETE FROM questions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun get(id: String): QuestionEntity?

    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE bankId = :bankId ORDER BY orderIndex")
    fun observeByBank(bankId: String): Flow<List<QuestionEntity>>

    @Query(
        """SELECT r.* FROM review_states r
           JOIN questions q ON q.id = r.questionId
           WHERE q.bankId = :bankId""",
    )
    fun observeReviewsByBank(bankId: String): Flow<List<ReviewStateEntity>>

    @Query("SELECT * FROM questions WHERE bankId = :bankId ORDER BY orderIndex")
    suspend fun getByBank(bankId: String): List<QuestionEntity>

    @Query(
        """SELECT q.* FROM questions q
           JOIN banks b ON b.id = q.bankId
           WHERE NOT EXISTS (SELECT 1 FROM attempts a WHERE a.questionId = q.id)
           ORDER BY b.sortOrder, b.createdAt, q.orderIndex""",
    )
    suspend fun getNewQuestions(): List<QuestionEntity>

    @Query(
        """SELECT q.* FROM questions q
           JOIN banks b ON b.id = q.bankId
           JOIN review_states r ON r.questionId = q.id
           WHERE r.nextReviewDate <= :today
             AND EXISTS (SELECT 1 FROM attempts a WHERE a.questionId = q.id)
           ORDER BY b.sortOrder, b.createdAt, q.orderIndex""",
    )
    suspend fun getDueQuestions(today: String): List<QuestionEntity>

    @Query(
        """SELECT q.* FROM questions q
           JOIN banks b ON b.id = q.bankId
           JOIN review_states r ON r.questionId = q.id
           WHERE r.wrongCount > 0 AND r.consecutiveCorrect < 2
           ORDER BY r.nextReviewDate, b.sortOrder, b.createdAt, q.orderIndex""",
    )
    fun observeWrongBook(): Flow<List<QuestionEntity>>

    @Query(
        """SELECT q.* FROM questions q
           JOIN banks b ON b.id = q.bankId
           JOIN review_states r ON r.questionId = q.id
           WHERE r.wrongCount > 0 AND r.consecutiveCorrect < 2
           ORDER BY r.nextReviewDate, b.sortOrder, b.createdAt, q.orderIndex""",
    )
    suspend fun getWrongBook(): List<QuestionEntity>

    @Query("SELECT COUNT(*) FROM questions WHERE bankId = :bankId")
    suspend fun countByBank(bankId: String): Int

    @Query(
        """SELECT COUNT(DISTINCT q.id) FROM questions q
           JOIN attempts a ON a.questionId = q.id
           WHERE q.bankId = :bankId""",
    )
    suspend fun attemptedByBank(bankId: String): Int

    @Query(
        """SELECT COUNT(*) FROM questions q
           JOIN review_states r ON r.questionId = q.id
           WHERE q.bankId = :bankId AND r.level >= 6""",
    )
    suspend fun masteredByBank(bankId: String): Int

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM questions WHERE stemHash = :hash")
    suspend fun countByHash(hash: String): Int

    @Query("SELECT stemHash FROM questions")
    suspend fun allHashes(): List<String>

    @Query("SELECT * FROM questions WHERE stem LIKE '%' || :query || '%' ORDER BY bankId, orderIndex")
    fun search(query: String): Flow<List<QuestionEntity>>

    @Query("DELETE FROM questions WHERE bankId = :bankId")
    suspend fun deleteByBank(bankId: String)
}

@Dao
interface ReviewDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(states: List<ReviewStateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: ReviewStateEntity)

    @Update
    suspend fun update(state: ReviewStateEntity)

    @Query("SELECT * FROM review_states WHERE questionId = :questionId")
    suspend fun get(questionId: String): ReviewStateEntity?

    @Query("SELECT * FROM review_states WHERE questionId IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ReviewStateEntity>

    @Query("SELECT * FROM review_states")
    suspend fun getAll(): List<ReviewStateEntity>
}

@Dao
interface AttemptDao {
    @Insert
    suspend fun insert(attempt: AttemptEntity): Long

    @Query("SELECT * FROM attempts WHERE localDate = :date")
    suspend fun byDate(date: String): List<AttemptEntity>

    @Query(
        """SELECT localDate, COUNT(*) AS answered,
                  SUM(CASE WHEN isCorrect THEN 1 ELSE 0 END) AS correct
           FROM attempts GROUP BY localDate ORDER BY localDate""",
    )
    fun observeDailyStats(): Flow<List<DailyAggregate>>

    @Query(
        """SELECT localDate, COUNT(*) AS answered,
                  SUM(CASE WHEN isCorrect THEN 1 ELSE 0 END) AS correct
           FROM attempts GROUP BY localDate ORDER BY localDate""",
    )
    suspend fun dailyStatsOnce(): List<DailyAggregate>

    @Query("SELECT DISTINCT localDate FROM attempts")
    suspend fun activeDates(): List<String>

    @Query("SELECT COUNT(*) FROM attempts WHERE localDate = :date")
    suspend fun countByDate(date: String): Int

    @Query("SELECT COUNT(*) FROM attempts WHERE localDate = :date AND isCorrect = 1")
    suspend fun correctByDate(date: String): Int

    @Query("DELETE FROM attempts")
    suspend fun clear()
}
