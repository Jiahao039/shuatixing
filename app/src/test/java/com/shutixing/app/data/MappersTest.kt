package com.shutixing.app.data

import com.shutixing.app.data.db.toDomain
import com.shutixing.app.data.db.toEntity
import com.shutixing.app.domain.model.Attempt
import com.shutixing.app.domain.model.AttemptMode
import com.shutixing.app.domain.model.Bank
import com.shutixing.app.domain.model.Option
import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.domain.model.ReviewState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MappersTest {
    @Test
    fun `question survives entity round trip`() {
        val question = Question(
            id = "q1", bankId = "b1", type = QuestionType.MULTIPLE,
            stem = "题干", options = listOf(Option("A", "甲"), Option("B", "乙")),
            answer = "AB", explanation = "解析", orderIndex = 3,
            stemHash = "hash", isFavorite = true, createdAt = 9L,
        )
        assertEquals(question, question.toEntity().toDomain())
    }

    @Test
    fun `review state survives entity round trip`() {
        val state = ReviewState(
            questionId = "q1", level = 3,
            nextReviewDate = LocalDate.of(2026, 10, 23),
            consecutiveCorrect = 2, wrongCount = 1, lastAnsweredAt = 5L,
        )
        assertEquals(state, state.toEntity().toDomain())
    }

    @Test
    fun `attempt survives entity round trip`() {
        val attempt = Attempt(
            questionId = "q1", localDate = LocalDate.of(2026, 10, 20),
            answeredAt = 7L, isCorrect = true, selectedAnswer = "A",
            durationMs = 1500L, mode = AttemptMode.RETEST,
        )
        assertEquals(attempt, attempt.toEntity().toDomain())
    }

    @Test
    fun `bank survives entity round trip`() {
        val bank = Bank("b1", "题库", "a.pdf", 1L, 2)
        assertEquals(bank, bank.toEntity().toDomain())
    }
}
