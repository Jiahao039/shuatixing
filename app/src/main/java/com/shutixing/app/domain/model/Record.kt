package com.shutixing.app.domain.model

import java.time.LocalDate

data class QuestionRecord(
    val order: Int,
    val question: Question,
    val review: ReviewState?,
    val attemptCount: Int,
    val correctCount: Int,
    val lastSelectedAnswer: String?,
    val lastAnsweredAt: Long?,
)

/** 错题优先排序：答错次数多者靠前，其次按下次复习日期，最后按题序。 */
fun List<QuestionRecord>.sortedForReview(): List<QuestionRecord> = sortedWith(
    compareByDescending<QuestionRecord> { it.review?.wrongCount ?: 0 }
        .thenBy { it.review?.nextReviewDate ?: LocalDate.MAX }
        .thenBy { it.question.orderIndex },
)
