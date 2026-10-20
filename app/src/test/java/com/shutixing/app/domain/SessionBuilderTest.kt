package com.shutixing.app.domain

import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.domain.model.QuestionWithState
import com.shutixing.app.domain.model.ReviewState
import com.shutixing.app.domain.session.SessionBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SessionBuilderTest {
    private val today = LocalDate.of(2026, 10, 20)

    private fun question(id: String, bank: String = "b1", order: Int = 0) = Question(
        id = id,
        bankId = bank,
        type = QuestionType.SINGLE,
        stem = "stem $id",
        options = listOf(com.shutixing.app.domain.model.Option("A", "a")),
        answer = "A",
        orderIndex = order,
    )

    private fun withState(
        id: String,
        wrong: Int = 0,
        next: LocalDate = today,
        consecutive: Int = 0,
    ) = QuestionWithState(
        question = question(id),
        review = ReviewState(
            questionId = id,
            nextReviewDate = next,
            wrongCount = wrong,
            consecutiveCorrect = consecutive,
        ),
    )

    @Test
    fun `wrong book questions come first then due reviews then new questions`() {
        val queue = SessionBuilder.build(
            dueWrong = listOf(withState("wrong1", wrong = 2)),
            dueReview = listOf(withState("due1"), withState("due2", next = today.minusDays(1))),
            newQuestions = listOf(question("new1"), question("new2")),
            target = 10,
            today = today,
        )
        assertEquals(listOf("wrong1", "due2", "due1", "new1", "new2"), queue.map { it.id })
    }

    @Test
    fun `target truncates the queue`() {
        val queue = SessionBuilder.build(
            dueWrong = emptyList(),
            dueReview = listOf(withState("due1")),
            newQuestions = listOf(question("new1"), question("new2")),
            target = 2,
            today = today,
        )
        assertEquals(listOf("due1", "new1"), queue.map { it.id })
    }

    @Test
    fun `no duplicates when a question appears in two pools`() {
        val q = withState("q1", wrong = 1)
        val queue = SessionBuilder.build(
            dueWrong = listOf(q),
            dueReview = listOf(q),
            newQuestions = emptyList(),
            target = 10,
            today = today,
        )
        assertEquals(listOf("q1"), queue.map { it.id })
    }

    @Test
    fun `zero target returns empty queue`() {
        val queue = SessionBuilder.build(
            dueWrong = listOf(withState("wrong1")),
            dueReview = listOf(withState("due1")),
            newQuestions = listOf(question("new1")),
            target = 0,
            today = today,
        )
        assertTrue(queue.isEmpty())
    }
}
