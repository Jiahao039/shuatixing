package com.shutixing.app.parser

import com.shutixing.app.parser.model.TextLine
import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizerTest {
    @Test
    fun `collapses whitespace and converts full width characters`() {
        val input = listOf(TextLine("１．  ＴＣＰ  协议", page = 0, top = 100f))
        assertEquals(listOf("1. TCP 协议"), Normalizer.normalize(input).map { it.text })
    }

    @Test
    fun `removes page numbers`() {
        val input = listOf(
            TextLine("1. 第一题", 0, 100f),
            TextLine("- 12 -", 1, 800f),
            TextLine("第 13 页", 2, 800f),
        )
        assertEquals(listOf("1. 第一题"), Normalizer.normalize(input).map { it.text })
    }

    @Test
    fun `removes headers repeated on most pages at the same position`() {
        val input = (0..3).flatMap { page ->
            listOf(
                TextLine("计算机网络题库", page, top = 40f),
                TextLine("${page + 1}. 题目内容", page, top = 100f + page),
            )
        }
        val result = Normalizer.normalize(input)
        assertEquals(4, result.size)
        assertEquals(
            listOf("1. 题目内容", "2. 题目内容", "3. 题目内容", "4. 题目内容"),
            result.map { it.text },
        )
    }

    @Test
    fun `removes headers from a two page document`() {
        val input = listOf(
            TextLine("题库页眉", 0, 40f),
            TextLine("1. 第一页题目", 0, 100f),
            TextLine("题库页眉", 1, 40f),
            TextLine("2. 第二页题目", 1, 100f),
        )
        assertEquals(
            listOf("1. 第一页题目", "2. 第二页题目"),
            Normalizer.normalize(input).map { it.text },
        )
    }

    @Test
    fun `keeps lines repeated only on a minority of pages`() {
        val input = listOf(
            TextLine("答案：A", 0, 200f),
            TextLine("答案：A", 1, 200f),
            TextLine("别的内容", 2, 300f),
            TextLine("别的内容2", 3, 400f),
        )
        val result = Normalizer.normalize(input)
        assertEquals(4, result.size)
    }

    @Test
    fun `drops blank lines`() {
        val input = listOf(TextLine("   ", 0, 10f), TextLine("题干", 0, 20f))
        assertEquals(listOf("题干"), Normalizer.normalize(input).map { it.text })
    }
}
