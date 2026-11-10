package com.shutixing.app.data

import com.shutixing.app.data.export.RecordExporter
import com.shutixing.app.domain.model.Bank
import com.shutixing.app.domain.model.Option
import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionRecord
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.domain.model.ReviewState
import com.shutixing.app.domain.model.sortedForReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecordExporterTest {
    private val bank = Bank("b1", "网络题库", "net.pdf")
    private val today = LocalDate.of(2026, 10, 21)

    private fun question(
        id: String,
        type: QuestionType,
        stem: String,
        answer: String,
        order: Int = 0,
        favorite: Boolean = false,
    ) = Question(
        id = id,
        bankId = "b1",
        type = type,
        stem = stem,
        options = if (type == QuestionType.JUDGE) emptyList() else listOf(Option("A", "甲"), Option("B", "乙")),
        answer = answer,
        isFavorite = favorite,
        orderIndex = order,
    )

    private fun record(
        id: String,
        type: QuestionType = QuestionType.SINGLE,
        stem: String = "题干 $id",
        answer: String = "A",
        order: Int = 0,
        favorite: Boolean = false,
        level: Int = 0,
        consecutive: Int = 0,
        wrong: Int = 0,
        attempted: Boolean = false,
        attemptCount: Int = 0,
        correctCount: Int = 0,
        lastSelected: String? = null,
    ) = QuestionRecord(
        order = order + 1,
        question = question(id, type, stem, answer, order, favorite),
        review = ReviewState(
            questionId = id,
            level = level,
            nextReviewDate = today,
            consecutiveCorrect = consecutive,
            wrongCount = wrong,
            lastAnsweredAt = if (attempted) 1_700_000_000_000L else null,
        ),
        attemptCount = attemptCount,
        correctCount = correctCount,
        lastSelectedAnswer = lastSelected,
        lastAnsweredAt = if (attempted) 1_700_000_000_000L else null,
    )

    private fun rowFor(markdown: String, stem: String): String =
        markdown.lineSequence().first { it.trimStart().startsWith("|") && it.contains(stem) }

    @Test
    fun `summary counts are correct`() {
        val records = listOf(
            record("q1", order = 0, wrong = 2, attempted = true, attemptCount = 3, correctCount = 1),
            record("q2", order = 1, level = 6, consecutive = 2, attempted = true, attemptCount = 2, correctCount = 2),
            record("q3", order = 2),
        )
        val md = RecordExporter.toMarkdown(bank, records, exportedAt = "2026-11-10 12:00:00")
        assertTrue(md.contains("- 总题数：3"))
        assertTrue(md.contains("- 已作答：2"))
        assertTrue(md.contains("- 错题数：1"))
        assertTrue(md.contains("- 已掌握：1"))
        assertTrue(md.contains("- 累计作答次数：5"))
        assertTrue(md.contains("- 总体正确率：60.0%"))
    }

    @Test
    fun `formats judge and multiple choice answers`() {
        val records = listOf(
            record("q1", type = QuestionType.JUDGE, stem = "判断题", answer = "T", lastSelected = "F", attempted = true, attemptCount = 1),
            record("q2", type = QuestionType.MULTIPLE, stem = "多选题", answer = "AB", lastSelected = "AC", order = 1, attempted = true, attemptCount = 1),
        )
        val md = RecordExporter.toMarkdown(bank, records, exportedAt = "2026-11-10 12:00:00")
        val judgeRow = rowFor(md, "判断题")
        assertTrue(judgeRow.contains("| 判断 |"))
        assertTrue(judgeRow.contains("| 对 |"))
        assertTrue(judgeRow.contains("| 错 |"))
        val multipleRow = rowFor(md, "多选题")
        assertTrue(multipleRow.contains("| 多选 |"))
        assertTrue(multipleRow.contains("A、B"))
        assertTrue(multipleRow.contains("A、C"))
    }

    @Test
    fun `unanswered question shows dashes and zero counts`() {
        val records = listOf(record("q1", stem = "未作答", order = 0))
        val md = RecordExporter.toMarkdown(bank, records, exportedAt = "2026-11-10 12:00:00")
        val row = rowFor(md, "未作答")
        val cells = row.trim().trim('|').split("|").map { it.trim() }
        assertEquals("A", cells[3])
        assertEquals("-", cells[4])
        assertEquals("0", cells[5])
        assertEquals("0", cells[6])
        assertEquals("-", cells[7])
        assertEquals("新题", cells[12])
    }

    @Test
    fun `escapes pipes and newlines in stem`() {
        val records = listOf(record("q1", stem = "A|B\nC", order = 0))
        val md = RecordExporter.toMarkdown(bank, records, exportedAt = "2026-11-10 12:00:00")
        assertTrue(md.contains("A\\|B C"))
    }

    @Test
    fun `empty bank has dash accuracy`() {
        val md = RecordExporter.toMarkdown(bank, emptyList(), exportedAt = "2026-11-10 12:00:00")
        assertTrue(md.contains("- 总体正确率：-"))
    }

    @Test
    fun `records sorted with wrong questions first`() {
        val newOne = record("q_new", order = 0)
        val wrong = record("q_wrong", order = 1, wrong = 3, attempted = true, attemptCount = 4, correctCount = 1)
        val review = record("q_review", order = 2, level = 1, attempted = true, attemptCount = 1, correctCount = 1)
        val sorted = listOf(newOne, wrong, review).sortedForReview()
        assertEquals("q_wrong", sorted.first().question.id)
    }
}
