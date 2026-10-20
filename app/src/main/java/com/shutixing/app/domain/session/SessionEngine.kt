package com.shutixing.app.domain.session

data class SessionState(
    val mainQueue: List<String>,
    val index: Int = 0,
    val retestQueue: List<String> = emptyList(),
    val inRetest: Boolean = false,
    val answeredMain: Int = 0,
    val totalAnswered: Int = 0,
    val correctCount: Int = 0,
    val finished: Boolean = false,
) {
    val currentId: String?
        get() = if (inRetest) retestQueue.firstOrNull() else mainQueue.getOrNull(index)

    val progress: Float
        get() = if (mainQueue.isEmpty()) 0f else answeredMain.toFloat() / mainQueue.size
}

object SessionEngine {
    fun start(mainQueue: List<String>): SessionState =
        SessionState(mainQueue = mainQueue, finished = mainQueue.isEmpty())

    fun onAnswered(state: SessionState, correct: Boolean): SessionState {
        val current = state.currentId ?: return state
        return state.copy(
            retestQueue = if (correct) state.retestQueue else state.retestQueue + current,
            totalAnswered = state.totalAnswered + 1,
            correctCount = state.correctCount + if (correct) 1 else 0,
        )
    }

    fun advance(state: SessionState): SessionState {
        if (state.finished) return state
        if (state.inRetest) {
            val remaining = state.retestQueue.drop(1)
            return if (remaining.isEmpty()) {
                state.copy(retestQueue = emptyList(), finished = true)
            } else {
                state.copy(retestQueue = remaining)
            }
        }
        val nextIndex = state.index + 1
        val answered = state.answeredMain + 1
        return when {
            nextIndex < state.mainQueue.size ->
                state.copy(index = nextIndex, answeredMain = answered)
            state.retestQueue.isNotEmpty() ->
                state.copy(index = state.mainQueue.size, inRetest = true, answeredMain = answered)
            else ->
                state.copy(index = state.mainQueue.size, answeredMain = answered, finished = true)
        }
    }
}
