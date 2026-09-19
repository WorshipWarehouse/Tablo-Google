package com.example

import com.example.model.TabloAiring
import com.example.ui.guide.airingAtAnchor
import com.example.ui.guide.guideAnchorFor
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideFocusTest {
    private fun airing(id: String, start: Long, minutes: Long) = TabloAiring(id, "c", id, startTimeMillis = start, durationSeconds = minutes * 60)

    @Test fun verticalMoveUsesAnchorInsideEarlierLongShow() {
        val start = 1_000_000L
        val anchor = start + 15 * 60_000L
        assertEquals("long", airingAtAnchor(listOf(airing("long", start, 60)), anchor)?.airingId)
    }

    @Test fun boundarySelectsNextShowNotShowThatEnded() {
        val start = 1_000_000L
        assertEquals("next", airingAtAnchor(listOf(airing("first", start, 30), airing("next", start + 30 * 60_000L, 30)), start + 30 * 60_000L)?.airingId)
    }

    @Test fun inProgressInitialFocusAnchorsAtWindowStart() {
        val windowStart = 1_000_000L
        assertEquals(windowStart, guideAnchorFor(airing("live", windowStart - 15 * 60_000L, 60), windowStart))
    }

    @Test fun repeatedVerticalMovesDoNotDriftAnchor() {
        val anchor = 1_000_000L + 15 * 60_000L
        val rows = List(3) { listOf(airing("row$it", 1_000_000L, 60)) }
        assertEquals(listOf("row0", "row1", "row2"), rows.map { airingAtAnchor(it, anchor)?.airingId })
    }
}
