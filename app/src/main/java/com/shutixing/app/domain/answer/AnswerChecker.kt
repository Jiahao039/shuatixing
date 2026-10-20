package com.shutixing.app.domain.answer

import com.shutixing.app.domain.model.QuestionType

object AnswerChecker {
    private val judgeTrue = setOf("对", "正确", "√", "T", "TRUE", "是", "Y", "YES")
    private val judgeFalse = setOf("错", "错误", "×", "X", "F", "FALSE", "否", "N", "NO")

    fun normalize(type: QuestionType, raw: String): String {
        val trimmed = raw.trim()
        return when (type) {
            QuestionType.JUDGE -> when (trimmed.uppercase()) {
                in judgeTrue -> "T"
                in judgeFalse -> "F"
                else -> trimmed.uppercase()
            }
            QuestionType.SINGLE, QuestionType.MULTIPLE ->
                trimmed.uppercase().filter { it in 'A'..'H' }.toSortedSet().joinToString("")
        }
    }

    fun isCorrect(type: QuestionType, answer: String, selected: String): Boolean {
        val normalizedAnswer = normalize(type, answer)
        return normalizedAnswer.isNotEmpty() && normalizedAnswer == normalize(type, selected)
    }
}
