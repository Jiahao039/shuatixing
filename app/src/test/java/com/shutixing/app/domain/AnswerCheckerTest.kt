package com.shutixing.app.domain

import com.shutixing.app.domain.answer.AnswerChecker
import com.shutixing.app.domain.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerCheckerTest {
    @Test
    fun `single choice normalizes case and letters`() {
        assertEquals("B", AnswerChecker.normalize(QuestionType.SINGLE, " b "))
    }

    @Test
    fun `multiple choice normalizes order and separators`() {
        assertEquals("AC", AnswerChecker.normalize(QuestionType.MULTIPLE, "C、a"))
        assertEquals("ABC", AnswerChecker.normalize(QuestionType.MULTIPLE, "B A C"))
    }

    @Test
    fun `judge answers map to T and F`() {
        assertEquals("T", AnswerChecker.normalize(QuestionType.JUDGE, "对"))
        assertEquals("T", AnswerChecker.normalize(QuestionType.JUDGE, "√"))
        assertEquals("T", AnswerChecker.normalize(QuestionType.JUDGE, "正确"))
        assertEquals("F", AnswerChecker.normalize(QuestionType.JUDGE, "错"))
        assertEquals("F", AnswerChecker.normalize(QuestionType.JUDGE, "×"))
        assertEquals("F", AnswerChecker.normalize(QuestionType.JUDGE, "错误"))
    }

    @Test
    fun `multiple choice grading is order insensitive`() {
        assertTrue(AnswerChecker.isCorrect(QuestionType.MULTIPLE, "AC", "CA"))
        assertFalse(AnswerChecker.isCorrect(QuestionType.MULTIPLE, "AC", "A"))
    }

    @Test
    fun `empty answer is never correct`() {
        assertFalse(AnswerChecker.isCorrect(QuestionType.SINGLE, "", ""))
        assertFalse(AnswerChecker.isCorrect(QuestionType.JUDGE, "", ""))
    }
}
