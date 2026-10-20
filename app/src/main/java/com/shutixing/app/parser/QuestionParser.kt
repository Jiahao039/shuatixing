package com.shutixing.app.parser

import com.shutixing.app.domain.answer.AnswerChecker
import com.shutixing.app.domain.model.Option
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.parser.model.ParseResult
import com.shutixing.app.parser.model.ParseWarning
import com.shutixing.app.parser.model.ParsedQuestion
import com.shutixing.app.parser.model.TextLine

object QuestionParser {
    private val questionStart = Regex(
        """^\s*(?:(\d{1,3})\s*[.、．)）](?!\d)|第\s*(\d{1,3})\s*题)\s*(.*)$""",
    )
    private val optionStart = Regex("""^([A-H])\s*[.、．)）]\s*(.*)$""")
    private val answerLine = Regex(
        """^\s*[【\[]?\s*(答案|正确答案|参考答案|Answer|Ans)\s*[】\]]?\s*[:：]?\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val explanationLine = Regex(
        """^\s*[【\[]?\s*(解析|分析|解答|答案解析)\s*[】\]]?\s*[:：]?\s*(.*)$""",
    )
    private val inlineAnswer = Regex(
        """\s*[【\[]?\s*(?:答案|正确答案|参考答案|Answer|Ans)\s*[】\]]?\s*[:：]\s*(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val optionSplit = Regex("""(?=\s*[A-H]\s*[.、．)）])""")

    private enum class Mode { STEM, OPTIONS, EXPLANATION }

    fun parse(lines: List<TextLine>, rawText: String = lines.joinToString("\n") { it.text }): ParseResult {
        val blocks = mutableListOf<MutableList<String>>()
        var current: MutableList<String>? = null

        for (line in lines) {
            val match = questionStart.find(line.text)
            if (match != null) {
                current?.let { blocks.add(it) }
                current = mutableListOf<String>().apply {
                    match.groupValues[3].trim().takeIf { it.isNotEmpty() }?.let(::add)
                }
            } else {
                current?.add(line.text)
            }
        }
        current?.let { blocks.add(it) }

        val questions = blocks.mapNotNull { parseBlock(it) }
        return ParseResult(questions = questions, rawText = rawText)
    }

    private fun parseBlock(block: List<String>): ParsedQuestion? {
        val stemParts = mutableListOf<String>()
        val options = mutableListOf<Option>()
        val explanationParts = mutableListOf<String>()
        var answer = ""
        var mode = Mode.STEM

        for (raw in block) {
            var line = raw.trim()
            if (line.isEmpty()) continue

            val expMatch = explanationLine.find(line)
            if (expMatch != null) {
                mode = Mode.EXPLANATION
                expMatch.groupValues[2].trim().takeIf { it.isNotEmpty() }?.let(explanationParts::add)
                continue
            }
            val ansMatch = answerLine.find(line)
            if (ansMatch != null) {
                answer = ansMatch.groupValues[2].trim()
                mode = Mode.EXPLANATION
                continue
            }

            val inline = inlineAnswer.find(line)
            if (inline != null && line.substring(0, inline.range.first).isNotBlank()) {
                answer = inline.groupValues[1].trim()
                line = line.substring(0, inline.range.first).trim()
            }

            when (mode) {
                Mode.STEM -> {
                    val opt = optionStart.find(line)
                    if (opt != null && opt.groupValues[1][0] == expectedKey(options.size)) {
                        mode = Mode.OPTIONS
                        parseOptionLine(opt, options)
                    } else {
                        stemParts.add(line)
                    }
                }
                Mode.OPTIONS -> {
                    val opt = optionStart.find(line)
                    if (opt != null && opt.groupValues[1][0] == expectedKey(options.size)) {
                        parseOptionLine(opt, options)
                    } else if (options.isEmpty()) {
                        stemParts.add(line)
                    } else {
                        options[options.lastIndex] = options.last().copy(
                            text = (options.last().text + " " + line).trim(),
                        )
                    }
                }
                Mode.EXPLANATION -> explanationParts.add(line)
            }
        }

        val stem = stemParts.joinToString(" ").trim()
        if (stem.isEmpty() && options.isEmpty() && answer.isEmpty()) return null

        val warnings = mutableListOf<ParseWarning>()
        val type = inferType(options, answer)
        val normalizedAnswer = AnswerChecker.normalize(type, answer)

        if (stem.isEmpty()) warnings.add(ParseWarning.EMPTY_STEM)
        if (answer.isBlank()) warnings.add(ParseWarning.MISSING_ANSWER)
        if (options.isEmpty() && type != QuestionType.JUDGE) warnings.add(ParseWarning.MISSING_OPTIONS)
        if (answer.isNotBlank() && normalizedAnswer.isEmpty()) warnings.add(ParseWarning.INVALID_ANSWER)
        if (type != QuestionType.JUDGE && normalizedAnswer.isNotEmpty()) {
            val available: Set<Char> = options.map { it.key.first() }.toSet()
            if (normalizedAnswer.toSet().any { it !in available }) {
                warnings.add(ParseWarning.ANSWER_NOT_IN_OPTIONS)
            }
        }

        return ParsedQuestion(
            type = type,
            stem = stem,
            options = options,
            answer = normalizedAnswer,
            explanation = explanationParts.joinToString(" ").trim().takeIf { it.isNotEmpty() },
            warnings = warnings,
        )
    }

    private fun parseOptionLine(match: MatchResult, options: MutableList<Option>) {
        val key = match.groupValues[1]
        val rest = match.groupValues[2].trim()
        val parts = rest.split(optionSplit).filter { it.isNotBlank() }
        options.add(Option(key, parts.firstOrNull()?.trim().orEmpty()))
        for (part in parts.drop(1)) {
            val m = optionStart.find(part.trim()) ?: continue
            options.add(Option(m.groupValues[1], m.groupValues[2].trim()))
        }
    }

    private fun expectedKey(index: Int): Char = ('A' + index)

    private fun inferType(options: List<Option>, answer: String): QuestionType {
        if (options.isEmpty()) return QuestionType.JUDGE
        val letters = answer.uppercase().filter { it in 'A'..'H' }
        return if (letters.length > 1) QuestionType.MULTIPLE else QuestionType.SINGLE
    }
}
