package com.shutixing.app.parser.model

import com.shutixing.app.domain.model.Option
import com.shutixing.app.domain.model.QuestionType

data class TextLine(
    val text: String,
    val page: Int,
    val top: Float,
)

enum class ParseWarning {
    MISSING_ANSWER,
    INVALID_ANSWER,
    ANSWER_NOT_IN_OPTIONS,
    MISSING_OPTIONS,
    EMPTY_STEM,
}

data class ParsedQuestion(
    val type: QuestionType,
    val stem: String,
    val options: List<Option> = emptyList(),
    val answer: String = "",
    val explanation: String? = null,
    val warnings: List<ParseWarning> = emptyList(),
)

data class ParseResult(
    val questions: List<ParsedQuestion>,
    val rawText: String,
)
