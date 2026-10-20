package com.shutixing.app.data

import com.shutixing.app.data.export.BankExporter
import com.shutixing.app.data.export.BankImporter
import com.shutixing.app.data.export.ImportResult
import com.shutixing.app.domain.model.Bank
import com.shutixing.app.domain.model.Option
import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankJsonTest {
    private val bank = Bank("b1", "网络题库", "net.pdf")
    private val questions = listOf(
        Question(
            id = "q1", bankId = "b1", type = QuestionType.SINGLE,
            stem = "TCP 属于哪一层？",
            options = listOf(Option("A", "网络层"), Option("B", "传输层")),
            answer = "B", explanation = "TCP 是传输层协议。",
        ),
        Question(
            id = "q2", bankId = "b1", type = QuestionType.MULTIPLE,
            stem = "以下哪些是传输层协议？",
            options = listOf(Option("A", "TCP"), Option("B", "UDP"), Option("C", "IP")),
            answer = "AB",
        ),
        Question(
            id = "q3", bankId = "b1", type = QuestionType.JUDGE,
            stem = "TCP 是面向连接的。", options = emptyList(), answer = "T",
        ),
    )

    @Test
    fun `export then import preserves questions`() {
        val text = BankExporter.toJson(bank, questions, exportedAt = "2026-10-20T00:00:00Z")
        val result = BankImporter.parse(text)
        assertTrue(result is ImportResult.Success)
        result as ImportResult.Success
        assertEquals("网络题库", result.bankName)
        assertEquals(3, result.questions.size)
        assertEquals(QuestionType.MULTIPLE, result.questions[1].type)
        assertEquals("AB", result.questions[1].answer)
        assertEquals(QuestionType.JUDGE, result.questions[2].type)
        assertEquals("T", result.questions[2].answer)
    }

    @Test
    fun `rejects foreign format`() {
        val text = BankExporter.toJson(bank, questions).replace("shuatixing.bank", "other.format")
        val result = BankImporter.parse(text)
        assertTrue(result is ImportResult.Failure)
    }

    @Test
    fun `rejects unsupported version`() {
        val text = BankExporter.toJson(bank, questions).replace("\"version\": 1", "\"version\": 2")
        val result = BankImporter.parse(text)
        assertTrue(result is ImportResult.Failure)
    }

    @Test
    fun `skips invalid questions and reports count`() {
        val text = BankExporter.toJson(
            bank,
            questions + questions[0].copy(id = "q4", stem = " ", options = emptyList(), answer = ""),
        )
        val result = BankImporter.parse(text)
        assertTrue(result is ImportResult.Success)
        result as ImportResult.Success
        assertEquals(3, result.questions.size)
        assertEquals(1, result.skipped)
    }

    @Test
    fun `normalizes multiple answer order on import`() {
        val text = BankExporter.toJson(
            bank,
            listOf(questions[1].copy(answer = "BA")),
        )
        val result = BankImporter.parse(text) as ImportResult.Success
        assertEquals("AB", result.questions.single().answer)
    }

    @Test
    fun `rejects garbage json`() {
        assertTrue(BankImporter.parse("not json") is ImportResult.Failure)
    }
}
