package nyc.arash.homescreen.ui

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class WelcomeQuotesTest {
    @Test fun `visits vary without repeating the previous quote`() {
        val quotes = WelcomeQuotes(Random(42))
        val visits = List(100) { quotes.next() }
        assertTrue(visits.zipWithNext().all { (a, b) -> a != b })
        assertTrue(visits.toSet().size > 10)
        assertTrue(visits.all { it in WelcomeQuotes.lines })
    }
}
