package com.shutixing.app.data.repository

import androidx.room.withTransaction
import com.shutixing.app.data.db.AppDatabase
import com.shutixing.app.data.db.toDomain
import com.shutixing.app.data.db.toEntity
import com.shutixing.app.domain.answer.AnswerChecker
import com.shutixing.app.domain.model.Attempt
import com.shutixing.app.domain.model.AttemptMode
import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionWithState
import com.shutixing.app.domain.model.ReviewState
import com.shutixing.app.domain.review.Scheduler
import com.shutixing.app.domain.session.SessionBuilder
import java.time.LocalDate

data class AnswerOutcome(val isCorrect: Boolean, val state: ReviewState)

class PracticeRepository(private val db: AppDatabase) {

    suspend fun buildDailyQueue(target: Int, today: LocalDate): List<Question> {
        val due = dueQuestions(today)
        val (wrong, review) = due.partition { it.review.isInWrongBook }
        val newQuestions = db.questionDao().getNewQuestions().map { it.toDomain() }
        return SessionBuilder.build(wrong, review, newQuestions, target, today)
    }

    suspend fun dueQuestions(today: LocalDate): List<QuestionWithState> {
        val entities = db.questionDao().getDueQuestions(today.toString())
        val states = db.reviewDao().getByIds(entities.map { it.id }).associateBy { it.questionId }
        return entities.mapNotNull { entity ->
            states[entity.id]?.let { QuestionWithState(entity.toDomain(), it.toDomain()) }
        }
    }

    suspend fun wrongBook(): List<QuestionWithState> {
        val entities = db.questionDao().getWrongBook()
        val states = db.reviewDao().getByIds(entities.map { it.id }).associateBy { it.questionId }
        return entities.mapNotNull { entity ->
            states[entity.id]?.let { QuestionWithState(entity.toDomain(), it.toDomain()) }
        }
    }

    suspend fun answer(
        question: Question,
        selected: String,
        mode: AttemptMode,
        today: LocalDate,
        now: Long,
        durationMs: Long,
    ): AnswerOutcome = db.withTransaction {
        val current = db.reviewDao().get(question.id)?.toDomain()
            ?: ReviewState(question.id, nextReviewDate = today)
        val correct = AnswerChecker.isCorrect(question.type, question.answer, selected)
        val updated = Scheduler.onAnswer(current, correct, today, now)
        db.reviewDao().insert(updated.toEntity())
        db.attemptDao().insert(
            Attempt(
                questionId = question.id,
                localDate = today,
                answeredAt = now,
                isCorrect = correct,
                selectedAnswer = AnswerChecker.normalize(question.type, selected),
                durationMs = durationMs,
                mode = mode,
            ).toEntity(),
        )
        AnswerOutcome(correct, updated)
    }

    suspend fun getQuestion(id: String): Question? = db.questionDao().get(id)?.toDomain()

    suspend fun getQuestions(ids: List<String>): List<Question> {
        val byId = db.questionDao().getByIds(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toDomain() }
    }

    suspend fun reviewState(id: String): ReviewState? = db.reviewDao().get(id)?.toDomain()

    suspend fun countNew(): Int = db.questionDao().getNewQuestions().size

    suspend fun countDue(today: LocalDate): Int = dueQuestions(today).size
}
