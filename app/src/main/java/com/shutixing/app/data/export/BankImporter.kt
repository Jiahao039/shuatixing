package com.shutixing.app.data.export

import com.shutixing.app.domain.answer.AnswerChecker
import com.shutixing.app.domain.model.Option
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.parser.model.ParsedQuestion
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.InputStream

sealed interface ImportResult {
    data class Success(
        val bankName: String,
        val sourceFileName: String?,
        val questions: List<ParsedQuestion>,
        val skipped: Int,
        val skippedReasons: List<String>,
    ) : ImportResult

    data class Failure(val message: String) : ImportResult
}

object BankImporter {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): ImportResult {
        val file = try {
            json.decodeFromString<BankFile>(text)
        } catch (e: Exception) {
            return ImportResult.Failure("文件格式不正确")
        }
        if (file.format != BANK_FORMAT) return ImportResult.Failure("不是刷题星题库文件")
        if (file.version != BANK_FORMAT_VERSION) {
            return ImportResult.Failure("不支持的题库版本：${file.version}")
        }
        if (file.bank.name.isBlank()) return ImportResult.Failure("题库名称为空")

        val parsed = mutableListOf<ParsedQuestion>()
        val reasons = mutableListOf<String>()
        var skipped = 0
        file.questions.forEachIndexed { index, dto ->
            val question = validate(dto)
            if (question == null) {
                skipped++
                reasons.add("第 ${index + 1} 题数据无效")
            } else {
                parsed.add(question)
            }
        }
        if (parsed.isEmpty()) return ImportResult.Failure("没有可导入的题目")
        return ImportResult.Success(
            bankName = file.bank.name.trim(),
            sourceFileName = file.bank.sourceFileName,
            questions = parsed,
            skipped = skipped,
            skippedReasons = reasons,
        )
    }

    fun read(stream: InputStream): ImportResult =
        stream.use { parse(it.readBytes().toString(Charsets.UTF_8)) }

    private fun validate(dto: QuestionDto): ParsedQuestion? {
        if (dto.stem.isBlank()) return null
        val options = mutableListOf<Option>()
        for (option in dto.options) {
            val key = option.key.trim().uppercase()
            if (key.length != 1 || key[0] !in 'A'..'H' || option.text.isBlank()) return null
            options.add(Option(key, option.text.trim()))
        }
        if (options.map { it.key }.toSet().size != options.size) return null

        val letterCount = dto.answer.uppercase().count { it in 'A'..'H' }
        val type = when {
            options.isEmpty() -> QuestionType.JUDGE
            letterCount > 1 -> QuestionType.MULTIPLE
            else -> QuestionType.SINGLE
        }
        val answer = AnswerChecker.normalize(type, dto.answer)
        if (answer.isEmpty()) return null

        if (type == QuestionType.JUDGE) {
            if (answer != "T" && answer != "F") return null
            return ParsedQuestion(
                type = type,
                stem = dto.stem.trim(),
                options = emptyList(),
                answer = answer,
                explanation = dto.explanation?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

        val keys: Set<Char> = options.map { it.key.first() }.toSet()
        if (answer.toSet().any { it !in keys }) return null
        return ParsedQuestion(
            type = type,
            stem = dto.stem.trim(),
            options = options,
            answer = answer,
            explanation = dto.explanation?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
}
