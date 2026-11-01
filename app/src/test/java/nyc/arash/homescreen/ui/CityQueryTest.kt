package nyc.arash.homescreen.ui

import org.junit.Assert.*
import org.junit.Test

class CityQueryTest {
    @Test fun `multiword city entry and delete preserve the remaining query`() {
        val query = CityQuery.append(CityQuery.append("NEW", " "), "YORK")
        assertEquals("NEW YORK", query)
        assertEquals("NEW YOR", CityQuery.delete(query))
        assertEquals("", CityQuery.delete(""))
    }
    @Test fun `hardware input stays bounded and delete removes a whole code point`() {
        assertEquals(100, CityQuery.append("A".repeat(99), "BC").length)
        assertEquals("A", CityQuery.delete("A\uD83C\uDF27"))
    }
}
