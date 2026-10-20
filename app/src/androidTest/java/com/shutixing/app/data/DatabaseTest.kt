package com.shutixing.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shutixing.app.data.db.AppDatabase
import com.shutixing.app.data.db.AttemptEntity
import com.shutixing.app.data.db.BankEntity
import com.shutixing.app.data.db.QuestionEntity
import com.shutixing.app.data.db.ReviewStateEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun seed() = runBlocking {
        db.bankDao().insert(BankEntity("b1", "题库一", "a.pdf", 1L, 0))
        db.questionDao().insertAll(
            listOf(
                QuestionEntity("q1", "b1", "SINGLE", "题干1", "[]", "A", null, 0, "h1", false, 1L),
                QuestionEntity("q2", "b1", "SINGLE", "题干2", "[]", "A", null, 1, "h2", false, 1L),
            ),
        )
        db.reviewDao().insertAll(
            listOf(
                ReviewStateEntity("q1", 2, "2026-10-20", 0, 0, null),
                ReviewStateEntity("q2", 0, "2026-10-20", 0, 2, null),
            ),
        )
    }

    @Test
    fun dueQuestionsRequireAnAttempt() = runBlocking {
        seed()
        assertTrue(db.questionDao().getDueQuestions("2026-10-20").isEmpty())

        db.attemptDao().insert(
            AttemptEntity(
                questionId = "q1", localDate = "2026-10-20", answeredAt = 1L,
                isCorrect = true, selectedAnswer = "A", durationMs = 100, mode = "DAILY",
            ),
        )
        assertEquals(listOf("q1"), db.questionDao().getDueQuestions("2026-10-20").map { it.id })
    }

    @Test
    fun wrongBookContainsUnresolvedMistakes() = runBlocking {
        seed()
        assertEquals(listOf("q2"), db.questionDao().getWrongBook().map { it.id })
    }

    @Test
    fun newQuestionsExcludeAttempted() = runBlocking {
        seed()
        assertEquals(listOf("q1", "q2"), db.questionDao().getNewQuestions().map { it.id })
        db.attemptDao().insert(
            AttemptEntity(
                questionId = "q1", localDate = "2026-10-20", answeredAt = 1L,
                isCorrect = false, selectedAnswer = "B", durationMs = 100, mode = "DAILY",
            ),
        )
        assertEquals(listOf("q2"), db.questionDao().getNewQuestions().map { it.id })
    }

    @Test
    fun deletingBankCascadesToQuestions() = runBlocking {
        seed()
        db.bankDao().delete("b1")
        assertEquals(0, db.questionDao().countAll())
    }

    @Test
    fun dailyAggregatesGroupByDate() = runBlocking {
        seed()
        db.attemptDao().insert(
            AttemptEntity(questionId = "q1", localDate = "2026-10-20", answeredAt = 1L, isCorrect = true, selectedAnswer = "A", durationMs = 10, mode = "DAILY"),
        )
        db.attemptDao().insert(
            AttemptEntity(questionId = "q2", localDate = "2026-10-20", answeredAt = 2L, isCorrect = false, selectedAnswer = "B", durationMs = 10, mode = "DAILY"),
        )
        val stats = db.attemptDao().dailyStatsOnce()
        assertEquals(1, stats.size)
        assertEquals(2, stats[0].answered)
        assertEquals(1, stats[0].correct)
    }
}
