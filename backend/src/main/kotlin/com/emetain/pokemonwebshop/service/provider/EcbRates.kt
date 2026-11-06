package com.emetain.pokemonwebshop.service.provider

import org.w3c.dom.Element
import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Reads the ECB's daily file: `<Cube time="2026-11-05"><Cube currency="USD" rate="1.1224"/>...</Cube>`. */
object EcbRates {

    data class Published(val date: LocalDate, val perEuro: Map<String, BigDecimal>)

    fun parse(xml: InputStream): Published {
        // Outside XML may never pull in other files or expand entities.
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val cubes = factory.newDocumentBuilder().parse(xml).getElementsByTagNameNS("*", "Cube")
        val elements = (0 until cubes.length).map { cubes.item(it) as Element }

        val date = elements.firstNotNullOfOrNull { it.getAttribute("time").ifBlank { null } }
        requireNotNull(date) { "The ECB file has no date" }

        val rates = elements.filter { it.hasAttribute("currency") }
            .associate { it.getAttribute("currency") to it.getAttribute("rate").toBigDecimalOrNull() }
            .mapNotNull { (currency, rate) -> rate?.takeIf { it.signum() > 0 }?.let { currency to it } }
            .toMap()
        return Published(LocalDate.parse(date), rates)
    }
}
