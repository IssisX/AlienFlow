package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Test

class StatusNoticePositionTest {
    @Test fun tallNoticeNearTopIsPlacedBelowAlienWithoutClipping() {
        val p = StatusNoticePosition.place(0, 60, 1080, 1000, 800, 250, 990, 100, 80, 10)
        assertEquals(190, p.y)
        assertTrue(p.x >= 0 && p.x + 800 <= 1080)
        assertTrue(p.y >= 60 && p.y + 250 <= 1000)
    }
    @Test fun noticeNearKeyboardFitsAboveAlien() {
        val p = StatusNoticePosition.place(40, 60, 1080, 800, 800, 250, 10, 740, 80, 10)
        assertEquals(480, p.y)
        assertEquals(40, p.x)
        assertTrue(p.y + 250 <= 800)
    }
    @Test fun betweenTopAndKeyboardWholeMeasuredNoticeStillFits() {
        val p = StatusNoticePosition.place(0, 20, 400, 500, 380, 350, 200, 200, 70, 10)
        assertTrue(p.y in 20..150)
        assertTrue(p.x in 0..20)
    }
}
