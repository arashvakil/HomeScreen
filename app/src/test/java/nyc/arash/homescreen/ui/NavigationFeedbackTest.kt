package nyc.arash.homescreen.ui

import org.junit.Assert.*
import org.junit.Test

class NavigationFeedbackTest {
    @Test fun `initial and programmatic focus stays silent`() {
        assertFalse(NavigationFeedback().focusMoved(100))
    }

    @Test fun `directional move plays once and key release cancels blocked moves`() {
        val feedback = NavigationFeedback()
        feedback.directionKey(true, 100)
        assertTrue(feedback.focusMoved(105))
        assertFalse(feedback.focusMoved(110))
        feedback.directionKey(true, 200)
        feedback.directionKey(false, 210)
        assertFalse(feedback.focusMoved(220))
    }

    @Test fun `stale focus and rapid repeats stay quiet`() {
        val feedback = NavigationFeedback()
        feedback.directionKey(true, 100)
        assertFalse(feedback.focusMoved(300))
        feedback.directionKey(true, 400)
        assertTrue(feedback.focusMoved(405))
        feedback.directionKey(true, 420)
        assertFalse(feedback.focusMoved(425))
        feedback.directionKey(true, 500)
        assertTrue(feedback.focusMoved(505))
    }
}
