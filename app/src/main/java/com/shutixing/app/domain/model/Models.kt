package com.shutixing.app.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

enum class QuestionType { SINGLE, MULTIPLE, JUDGE }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class Option(val key: String, val text: String)

data class Question(
    val id: String,
    val bankId: String,
    val type: QuestionType,
    val stem: String,
    val options: List<Option> = emptyList(),
    val answer: String,
    val explanation: String? = null,
    val orderIndex: Int = 0,
    val stemHash: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = 0L,
)

object ReviewLevel {
    const val MASTERED = 6
}

data class ReviewState(
    val questionId: String,
    val level: Int = 0,
    val nextReviewDate: LocalDate,
    val consecutiveCorrect: Int = 0,
    val wrongCount: Int = 0,
    val lastAnsweredAt: Long? = null,
) {
    val isInWrongBook: Boolean get() = wrongCount > 0 && consecutiveCorrect < 2
    val isMastered: Boolean get() = level >= ReviewLevel.MASTERED
}

enum class AttemptMode { DAILY, RETEST, WRONG_BOOK }

data class Attempt(
    val id: Long = 0L,
    val questionId: String,
    val localDate: LocalDate,
    val answeredAt: Long,
    val isCorrect: Boolean,
    val selectedAnswer: String,
    val durationMs: Long,
    val mode: AttemptMode,
)

data class QuestionWithState(
    val question: Question,
    val review: ReviewState,
)

data class Bank(
    val id: String,
    val name: String,
    val sourceFileName: String? = null,
    val createdAt: Long = 0L,
    val sortOrder: Int = 0,
)
