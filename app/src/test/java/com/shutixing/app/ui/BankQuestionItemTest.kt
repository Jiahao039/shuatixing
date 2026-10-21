package com.shutixing.app.ui

import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.domain.model.ReviewState
import com.shutixing.app.ui.bankdetail.BankQuestionItem
import com.shutixing.app.ui.bankdetail.QuestionProgress
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BankQuestionItemTest {
    private val today = LocalDate.of(2026, 10, 21)
    private val question = Question(
        id = "q1",
        bankId = "b1",
        type = QuestionType.SINGLE,
        stem = "题干",
        answer = "A",
    )

    private fun item(
        level: Int = 0,
        consecutive: Int = 0,
        wrong: Int = 0,
        answered: Boolean = false,
    ) = BankQuestionItem(
        question = question,
        review = ReviewState(
            questionId = "q1",
            level = level,
            nextReviewDate = today,
            consecutiveCorrect = consecutive,
            wrongCount = wrong,
            lastAnsweredAt = if (answered) 1L else null,
        ),
    )

    @Test
    fun `missing review state is new`() {
        assertEquals(QuestionProgress.NEW, BankQuestionItem(question, null).progress)
    }

    @Test
    fun `unanswered question is new`() {
        assertEquals(QuestionProgress.NEW, item().progress)
    }

    @Test
    fun `answered question is learning`() {
        assertEquals(QuestionProgress.LEARNING, item(level = 1, answered = true).progress)
    }

    @Test
    fun `wrong question takes priority over learning`() {
        assertEquals(QuestionProgress.WRONG, item(level = 1, wrong = 2, answered = true).progress)
    }

    @Test
    fun `mastered question is mastered after two correct`() {
        assertEquals(
            QuestionProgress.MASTERED,
            item(level = 6, consecutive = 2, wrong = 1, answered = true).progress,
        )
    }
}
