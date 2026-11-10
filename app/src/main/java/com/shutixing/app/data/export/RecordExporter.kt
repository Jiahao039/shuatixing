package com.shutixing.app.data.export

import com.shutixing.app.domain.model.Bank
import com.shutixing.app.domain.model.QuestionRecord
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.domain.model.ReviewState
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object RecordExporter {
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val exportTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun write(stream: OutputStream, bank: Bank, records: List<QuestionRecord>) {
        stream.use { it.write(toMarkdown(bank, records).toByteArray(Charsets.UTF_8)) }
    }

    fun toMarkdown(
        bank: Bank,
        records: List<QuestionRecord>,
        exportedAt: String = LocalDateTime.now().format(exportTimeFormatter),
    ): String {
        val totalAttempts = records.sumOf { it.attemptCount }
        val totalCorrect = records.sumOf { it.correctCount }
        val attempted = records.count { it.attemptCount > 0 }
        val wrong = records.count { it.review?.isInWrongBook == true }
        val mastered = records.count { it.review?.isMastered == true }
        val accuracy = if (totalAttempts == 0) "-" else percent(totalCorrect, totalAttempts)

        return buildString {
            appendLine("# ${bank.name} 做题记录")
            appendLine()
            appendLine("- 导出时间：$exportedAt")
            appendLine("- 总题数：${records.size}")
            appendLine("- 已作答：$attempted")
            appendLine("- 错题数：$wrong")
            appendLine("- 已掌握：$mastered")
            appendLine("- 累计作答次数：$totalAttempts")
            appendLine("- 总体正确率：$accuracy")
            appendLine()
            appendLine("## 逐题明细")
            appendLine()
            appendLine(
                "| # | 题型 | 题干 | 正确答案 | 我的最近答案 | 作答次数 | 答错次数 | " +
                    "正确率 | 连续答对 | 复习等级 | 下次复习日期 | 最近作答时间 | 状态 | 收藏 |",
            )
            appendLine("| ---:| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- | --- | --- |")
            records.forEach { record ->
                val review = record.review
                val accuracyCell = if (record.attemptCount == 0) {
                    "-"
                } else {
                    percent(record.correctCount, record.attemptCount)
                }
                appendLine(
                    listOf(
                        record.order.toString(),
                        typeLabel(record.question.type),
                        cell(record.question.stem),
                        formatAnswer(record.question.type, record.question.answer),
                        formatAnswer(record.question.type, record.lastSelectedAnswer),
                        record.attemptCount.toString(),
                        (review?.wrongCount ?: 0).toString(),
                        accuracyCell,
                        (review?.consecutiveCorrect ?: 0).toString(),
                        (review?.level ?: 0).toString(),
                        review?.nextReviewDate?.toString() ?: "-",
                        formatTime(record.lastAnsweredAt),
                        statusLabel(review),
                        if (record.question.isFavorite) "是" else "",
                    ).joinToString(separator = " | ", prefix = "| ", postfix = " |"),
                )
            }
        }
    }

    private fun typeLabel(type: QuestionType): String = when (type) {
        QuestionType.SINGLE -> "单选"
        QuestionType.MULTIPLE -> "多选"
        QuestionType.JUDGE -> "判断"
    }

    private fun formatAnswer(type: QuestionType, raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return "-"
        return when (type) {
            QuestionType.JUDGE -> when (value.uppercase()) {
                "T" -> "对"
                "F" -> "错"
                else -> value
            }
            else -> value.toList().joinToString("、")
        }
    }

    private fun statusLabel(review: ReviewState?): String = when {
        review == null -> "新题"
        review.isInWrongBook -> "错题"
        review.isMastered -> "已掌握"
        review.lastAnsweredAt != null -> "复习中"
        else -> "新题"
    }

    private fun percent(part: Int, whole: Int): String =
        String.format(Locale.ROOT, "%.1f%%", part * 100.0 / whole)

    private fun formatTime(millis: Long?): String =
        millis?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()).format(timeFormatter)
        } ?: "-"

    private fun cell(value: String): String =
        value.replace("\r\n", " ")
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace("|", "\\|")
            .trim()
}
