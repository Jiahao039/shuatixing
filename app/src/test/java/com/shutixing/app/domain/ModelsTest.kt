package com.shutixing.app.domain

import com.shutixing.app.domain.model.ReviewState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ModelsTest {
    private val today = LocalDate.of(2026, 10, 20)

    private fun state(level: Int = 0, consecutive: Int = 0, wrong: Int = 0) = ReviewState(
        questionId = "q1",
        level = level,
        nextReviewDate = today,
        consecutiveCorrect = consecutive,
        wrongCount = wrong,
    )

    @Test
    fun `wrong question stays in wrong book until two consecutive correct`() {
        assertTrue(state(wrong = 1, consecutive = 0).isInWrongBook)
        assertTrue(state(wrong = 1, consecutive = 1).isInWrongBook)
        assertFalse(state(wrong = 1, consecutive = 2).isInWrongBook)
    }

    @Test
    fun `never wrong question is not in wrong book`() {
        assertFalse(state(wrong = 0, consecutive = 5).isInWrongBook)
    }

    @Test
    fun `level six is mastered`() {
        assertFalse(state(level = 5).isMastered)
        assertTrue(state(level = 6).isMastered)
    }
}
