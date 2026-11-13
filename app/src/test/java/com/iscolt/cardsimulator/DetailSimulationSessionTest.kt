package com.iscolt.cardsimulator

import com.iscolt.cardsimulator.nfc.DetailSimulationSession
import org.junit.Assert.*
import org.junit.Test

class DetailSimulationSessionTest {
    private var active = false
    private var starts = 0
    private var selected = ""
    private fun controller() = DetailSimulationSession({ selected = it; active = false }, {
        if (!active) { active = true; starts++ }
    }, { active = false })
    @Test fun opensAutomaticallyAndLeavingAlwaysStops() {
        val session = controller()
        session.foreground(true); assertFalse(active)
        session.open("a"); assertTrue(active); assertEquals("a", selected)
        session.leave(); assertFalse(active); assertNull(session.cardId)
        session.foreground(true); assertFalse(active)
        session.open("a"); assertTrue(active); assertEquals(2, starts)
    }
    @Test fun repeatedResumeDoesNotRestartButBackgroundDoes() {
        val session = controller()
        session.open("a"); assertFalse(active)
        session.foreground(true); session.foreground(true); assertEquals(1, starts)
        session.foreground(false); assertFalse(active)
        session.foreground(true); assertEquals(2, starts)
    }
    @Test fun editingAndManualPauseCannotBeUndoneByRefresh() {
        val session = controller(); session.foreground(true); session.open("a")
        session.editing(true); assertFalse(active)
        session.foreground(true); assertFalse(active)
        session.editing(false); assertTrue(active)
        session.setRequested(false); session.foreground(true); assertFalse(active)
        session.editing(true); session.foreground(false)
        val requested = session.isRequested; session.open("copy"); session.setRequested(requested)
        session.editing(false); session.foreground(true); assertFalse(active)
        session.leave(); session.open("b"); assertTrue(active); assertEquals("b", selected)
    }
    @Test fun failureRemainsVisibleAndCanRetryAfterSettings() {
        var ready = false
        val session = DetailSimulationSession({}, { check(ready) { "NFC 未开启" }; active = true }, { active = false })
        session.foreground(true); session.open("a")
        assertFalse(active); assertEquals("NFC 未开启", session.error)
        session.foreground(false); ready = true; session.foreground(true)
        assertTrue(active); assertNull(session.error)
    }
}
