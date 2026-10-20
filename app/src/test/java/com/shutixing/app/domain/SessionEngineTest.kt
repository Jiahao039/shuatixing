package com.shutixing.app.domain

import com.shutixing.app.domain.session.SessionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEngineTest {
    @Test
    fun `empty queue finishes immediately`() {
        assertTrue(SessionEngine.start(emptyList()).finished)
    }

    @Test
    fun `correct answers finish the main queue`() {
        var state = SessionEngine.start(listOf("q1", "q2"))
        assertEquals("q1", state.currentId)
        state = SessionEngine.advance(SessionEngine.onAnswered(state, true))
        assertEquals("q2", state.currentId)
        state = SessionEngine.advance(SessionEngine.onAnswered(state, true))
        assertTrue(state.finished)
        assertEquals(2, state.totalAnswered)
        assertEquals(2, state.correctCount)
        assertEquals(2, state.answeredMain)
    }

    @Test
    fun `wrong answer is requeued and retested after the main queue`() {
        var state = SessionEngine.start(listOf("q1", "q2"))
        state = SessionEngine.onAnswered(state, false)
        assertEquals(listOf("q1"), state.retestQueue)
        state = SessionEngine.advance(state)
        assertEquals("q2", state.currentId)
        state = SessionEngine.advance(SessionEngine.onAnswered(state, true))
        assertTrue(state.inRetest)
        assertEquals("q1", state.currentId)
        state = SessionEngine.advance(SessionEngine.onAnswered(state, true))
        assertTrue(state.finished)
    }

    @Test
    fun `wrong retest answer re-enters the queue`() {
        var state = SessionEngine.start(listOf("q1"))
        state = SessionEngine.onAnswered(state, false)
        state = SessionEngine.advance(state)
        assertEquals("q1", state.currentId)
        state = SessionEngine.onAnswered(state, false)
        assertEquals(listOf("q1", "q1"), state.retestQueue)
        state = SessionEngine.advance(state)
        assertTrue(state.inRetest)
        assertEquals("q1", state.currentId)
        state = SessionEngine.advance(SessionEngine.onAnswered(state, true))
        assertTrue(state.finished)
    }

    @Test
    fun `progress tracks main queue completion`() {
        var state = SessionEngine.start(listOf("q1", "q2"))
        assertEquals(0f, state.progress, 0.001f)
        state = SessionEngine.advance(SessionEngine.onAnswered(state, true))
        assertEquals(0.5f, state.progress, 0.001f)
        state = SessionEngine.advance(SessionEngine.onAnswered(state, true))
        assertEquals(1f, state.progress, 0.001f)
        assertTrue(state.finished)
    }
}
