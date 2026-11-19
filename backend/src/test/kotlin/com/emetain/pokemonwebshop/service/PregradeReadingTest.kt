package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.dto.BoxDto
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PregradeReadingTest {

    private val mapper = ObjectMapper()

    @Test
    fun `boxes stay inside the photo and in order`() {
        assertEquals(BoxDto(0, 20, 980, 1000), PregradeReading.box(mapper.readTree("""{"left": 980, "top": 20, "right": -5, "bottom": 1200}""")))
        assertNull(PregradeReading.box(mapper.readTree("""{"left": 500, "top": 500, "right": 505, "bottom": 900}""")))
        assertNull(PregradeReading.box(mapper.readTree("null")))
    }

    @Test
    fun `clean corners are left out and the sides come front first`() {
        val result = PregradeReading.parse(
            mapper.readTree(
                """
                {"sides": [
                  {"side": "back", "usable": false, "problem": "Maak de foto recht van boven.", "outer": null, "inner": null,
                   "corners": [], "edges": [], "surface": {"position": "surface", "condition": "clean", "note": null}},
                  {"side": "front", "usable": true, "problem": null,
                   "outer": {"left": 1, "top": 1, "right": 999, "bottom": 999}, "inner": null,
                   "corners": [{"position": "top_left", "condition": "clean", "note": null},
                               {"position": "bottom_right", "condition": "clear", "note": "Wit randje"}],
                   "edges": [], "surface": {"position": "surface", "condition": "light", "note": "Een krasje"}}
                ]}
                """,
            ),
        )

        assertEquals(listOf("front", "back"), result.sides.map { it.side })
        assertEquals(listOf("bottom_right"), result.sides[0].corners.map { it.position })
        assertNull(result.sides[0].inner)
        assertEquals("Maak de foto recht van boven.", result.sides[1].problem)
    }
}
