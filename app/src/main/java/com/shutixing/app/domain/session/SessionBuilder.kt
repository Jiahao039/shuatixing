package com.shutixing.app.domain.session

import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionWithState
import java.time.LocalDate

object SessionBuilder {
    fun build(
        dueWrong: List<QuestionWithState>,
        dueReview: List<QuestionWithState>,
        newQuestions: List<Question>,
        target: Int,
        today: LocalDate,
    ): List<Question> {
        if (target <= 0) return emptyList()
        val order = compareBy<QuestionWithState>(
            { it.review.nextReviewDate },
            { it.question.bankId },
            { it.question.orderIndex },
        )
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Question>()
        fun add(candidate: Question) {
            if (result.size < target && seen.add(candidate.id)) result.add(candidate)
        }
        dueWrong.sortedWith(order).forEach { add(it.question) }
        dueReview.sortedWith(order).forEach { add(it.question) }
        newQuestions.forEach { add(it) }
        return result
    }
}
