package com.emetain.pokemonwebshop.service.provider

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.xml.sax.SAXParseException
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EcbRatesTest {

    private fun parse(xml: String) = EcbRates.parse(xml.byteInputStream())

    @Test
    fun `reads the date and rates from the real file`() {
        val published = requireNotNull(javaClass.getResourceAsStream("/catalog/ecb-daily.xml")).use(EcbRates::parse)

        assertEquals(LocalDate.of(2026, 11, 5), published.date)
        assertEquals(BigDecimal("1.1224"), published.perEuro["USD"])
        assertEquals(BigDecimal("0.83393"), published.perEuro["GBP"])
    }

    @Test
    fun `leaves out a rate that isn't a positive number`() {
        val published = parse(
            """
            <Envelope><Cube><Cube time="2026-11-05">
              <Cube currency="USD" rate="1.1"/><Cube currency="AAA" rate="0"/><Cube currency="BBB" rate="n/a"/>
            </Cube></Cube></Envelope>
            """,
        )

        assertEquals(setOf("USD"), published.perEuro.keys)
    }

    @Test
    fun `a file without a date is refused`() {
        assertThrows<IllegalArgumentException> { parse("""<Envelope><Cube currency="USD" rate="1.1"/></Envelope>""") }
    }

    @Test
    fun `xml that tries to pull in other files is refused`() {
        val attack = """
            <?xml version="1.0"?>
            <!DOCTYPE Envelope [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
            <Envelope><Cube time="2026-11-05"><Cube currency="USD" rate="&secret;"/></Cube></Envelope>
        """.trimIndent()

        val exception = assertThrows<SAXParseException> { parse(attack) }

        assertFalse(exception.message.orEmpty().contains("root:"))
    }
}
