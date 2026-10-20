package com.shutixing.app.parser

import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.parser.model.ParseWarning
import com.shutixing.app.parser.model.TextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionParserTest {
    private fun lines(vararg text: String) = text.mapIndexed { i, s -> TextLine(s, 0, i * 10f) }

    @Test
    fun `parses single choice with answer on next line`() {
        val result = QuestionParser.parse(
            lines(
                "1. TCP 协议工作在 OSI 的哪一层？",
                "A. 物理层",
                "B. 传输层",
                "C. 网络层",
                "D. 应用层",
                "答案：B",
            ),
        )
        val q = result.questions.single()
        assertEquals(QuestionType.SINGLE, q.type)
        assertEquals("TCP 协议工作在 OSI 的哪一层？", q.stem)
        assertEquals(listOf("A", "B", "C", "D"), q.options.map { it.key })
        assertEquals("B", q.answer)
        assertTrue(q.warnings.isEmpty())
    }

    @Test
    fun `parses answer appended to last option line`() {
        val result = QuestionParser.parse(
            lines(
                "2. 下列属于传输层协议的是？",
                "A. IP",
                "B. TCP  答案：B",
            ),
        )
        val q = result.questions.single()
        assertEquals("TCP", q.options[1].text)
        assertEquals("B", q.answer)
    }

    @Test
    fun `parses multiple choice with separators`() {
        val result = QuestionParser.parse(
            lines(
                "3. 以下哪些是面向连接的？",
                "A. TCP",
                "B. UDP",
                "C. SCTP",
                "正确答案：A、C",
            ),
        )
        val q = result.questions.single()
        assertEquals(QuestionType.MULTIPLE, q.type)
        assertEquals("AC", q.answer)
    }

    @Test
    fun `parses judge question without options`() {
        val result = QuestionParser.parse(
            lines(
                "4. 判断：TCP 是面向连接的协议。",
                "答案：对",
            ),
        )
        val q = result.questions.single()
        assertEquals(QuestionType.JUDGE, q.type)
        assertEquals("T", q.answer)
    }

    @Test
    fun `parses explanation across multiple lines`() {
        val result = QuestionParser.parse(
            lines(
                "5. 下列哪个是私有地址？",
                "A. 8.8.8.8",
                "B. 192.168.1.1",
                "答案：B",
                "解析：192.168.0.0/16 是私有地址段。",
                "其他选项均为公网地址。",
            ),
        )
        val q = result.questions.single()
        assertEquals("192.168.0.0/16 是私有地址段。 其他选项均为公网地址。", q.explanation)
    }

    @Test
    fun `splits multiple options on one line`() {
        val result = QuestionParser.parse(
            lines(
                "6. 以下哪个是操作系统？",
                "A. Windows B. Linux C. MySQL D. Oracle",
                "答案：A",
            ),
        )
        val q = result.questions.single()
        assertEquals(listOf("A", "B", "C", "D"), q.options.map { it.key })
        assertEquals("Linux", q.options[1].text)
    }

    @Test
    fun `continues option text on following lines`() {
        val result = QuestionParser.parse(
            lines(
                "7. 题干",
                "A. 第一行",
                "继续的说明",
                "B. 另一个选项",
                "答案：A",
            ),
        )
        val q = result.questions.single()
        assertEquals("第一行 继续的说明", q.options[0].text)
    }

    @Test
    fun `supports chinese question number format`() {
        val result = QuestionParser.parse(
            lines("第8题 下列哪个正确？", "A. 甲", "B. 乙", "答案：A"),
        )
        assertEquals(1, result.questions.size)
        assertEquals("下列哪个正确？", result.questions.single().stem)
    }

    @Test
    fun `decimal numbers do not start a new question`() {
        val result = QuestionParser.parse(
            lines("9. 圆的面积是多少？", "A. 3.14", "B. 6.28", "答案：A"),
        )
        assertEquals(1, result.questions.size)
        assertEquals(listOf("A", "B"), result.questions.single().options.map { it.key })
    }

    @Test
    fun `missing answer produces warning`() {
        val result = QuestionParser.parse(
            lines("10. 没有答案的题目", "A. 甲", "B. 乙"),
        )
        val q = result.questions.single()
        assertEquals("", q.answer)
        assertTrue(ParseWarning.MISSING_ANSWER in q.warnings)
    }

    @Test
    fun `answer letter not among options produces warning`() {
        val result = QuestionParser.parse(
            lines("11. 题干", "A. 甲", "B. 乙", "答案：D"),
        )
        assertTrue(ParseWarning.ANSWER_NOT_IN_OPTIONS in result.questions.single().warnings)
    }

    @Test
    fun `text before first question is ignored`() {
        val result = QuestionParser.parse(
            lines("第一章 计算机网络", "12. 题干", "A. 甲", "B. 乙", "答案：A"),
        )
        assertEquals(1, result.questions.size)
        assertNull(result.questions.single().explanation)
    }
}
