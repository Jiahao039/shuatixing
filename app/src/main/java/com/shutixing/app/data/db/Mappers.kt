package com.shutixing.app.data.db

import com.shutixing.app.domain.model.Attempt
import com.shutixing.app.domain.model.AttemptMode
import com.shutixing.app.domain.model.Bank
import com.shutixing.app.domain.model.Option
import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.domain.model.ReviewState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val json = Json { ignoreUnknownKeys = true }

fun BankEntity.toDomain() = Bank(id, name, sourceFileName, createdAt, sortOrder)
fun Bank.toEntity() = BankEntity(id, name, sourceFileName, createdAt, sortOrder)

fun QuestionEntity.toDomain() = Question(
    id = id,
    bankId = bankId,
    type = QuestionType.valueOf(type),
    stem = stem,
    options = runCatching { json.decodeFromString<List<Option>>(optionsJson) }.getOrDefault(emptyList()),
    answer = answer,
    explanation = explanation,
    orderIndex = orderIndex,
    stemHash = stemHash,
    isFavorite = isFavorite,
    createdAt = createdAt,
)

fun Question.toEntity() = QuestionEntity(
    id = id,
    bankId = bankId,
    type = type.name,
    stem = stem,
    optionsJson = json.encodeToString(options),
    answer = answer,
    explanation = explanation,
    orderIndex = orderIndex,
    stemHash = stemHash,
    isFavorite = isFavorite,
    createdAt = createdAt,
)

fun ReviewStateEntity.toDomain() = ReviewState(
    questionId = questionId,
    level = level,
    nextReviewDate = LocalDate.parse(nextReviewDate),
    consecutiveCorrect = consecutiveCorrect,
    wrongCount = wrongCount,
    lastAnsweredAt = lastAnsweredAt,
)

fun ReviewState.toEntity() = ReviewStateEntity(
    questionId = questionId,
    level = level,
    nextReviewDate = nextReviewDate.toString(),
    consecutiveCorrect = consecutiveCorrect,
    wrongCount = wrongCount,
    lastAnsweredAt = lastAnsweredAt,
)

fun AttemptEntity.toDomain() = Attempt(
    id = id,
    questionId = questionId,
    localDate = LocalDate.parse(localDate),
    answeredAt = answeredAt,
    isCorrect = isCorrect,
    selectedAnswer = selectedAnswer,
    durationMs = durationMs,
    mode = AttemptMode.valueOf(mode),
)

fun Attempt.toEntity() = AttemptEntity(
    id = id,
    questionId = questionId,
    localDate = localDate.toString(),
    answeredAt = answeredAt,
    isCorrect = isCorrect,
    selectedAnswer = selectedAnswer,
    durationMs = durationMs,
    mode = mode.name,
)
