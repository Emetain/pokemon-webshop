package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.CardVariants.ETCHED
import com.emetain.pokemonwebshop.domain.CardVariants.FOIL
import com.emetain.pokemonwebshop.domain.CardVariants.NORMAL
import com.emetain.pokemonwebshop.domain.CardVariants.REVERSE
import com.emetain.pokemonwebshop.domain.CardmarketLink
import com.emetain.pokemonwebshop.domain.CardmarketSingle
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.PriceSource
import com.emetain.pokemonwebshop.service.provider.ProviderCard
import com.emetain.pokemonwebshop.service.provider.ProviderPrice
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardmarketLinksTest {

    private val updated = Instant.parse("2026-11-19T01:48:38Z")
    private val rates = ExchangeRates(mapOf(Currency.USD to BigDecimal("1.16")))

    private fun card(
        number: String,
        name: String = "Mega Rayquaza ex",
        productId: Long? = 918912,
        cardmarketTrend: Long? = 293,
        tcgplayerTrend: Long? = null,
        variants: Set<String> = setOf(NORMAL),
    ) = ProviderCard(
        externalId = "me06-$number",
        number = number,
        name = name,
        rarity = null,
        cardType = null,
        imageSmallUrl = null,
        imageLargeUrl = null,
        variants = variants,
        prices = listOfNotNull(cardmarketTrend?.let { ProviderPrice(NORMAL, it, null, updated) }),
        cardmarketProductId = productId,
        fallbackPrices = listOfNotNull(
            tcgplayerTrend?.let { ProviderPrice(NORMAL, it, null, updated, PriceSource.TCGPLAYER, Currency.USD) },
        ),
    )

    private fun single(
        productId: Long,
        trend: Long?,
        name: String = "Mega Rayquaza ex [Roar of the Ruler | Storm Emeralda]",
        metacard: Long? = 466225,
        expansion: Long = 6704,
        foilTrend: Long? = null,
        game: Game = Game.POKEMON,
    ) = CardmarketSingle(
        productId = productId,
        game = game,
        name = name,
        expansionId = expansion,
        metacardId = metacard,
        trendCents = trend,
        averageCents = null,
        foilTrendCents = foilTrend,
        updatedAt = updated,
    )

    private fun decide(
        cards: List<ProviderCard>,
        singles: List<CardmarketSingle>,
        manual: Map<String, ManualLink> = emptyMap(),
        game: Game = Game.POKEMON,
    ) = CardmarketLinks.decide(game, cards, manual, singles.associateBy { it.productId }, rates)

    /** A Magic card as Scryfall gives it: its Cardmarket product, Scryfall's euros and TCGplayer's dollars. */
    private fun magic(
        number: String,
        name: String,
        productId: Long?,
        variants: Set<String> = setOf(NORMAL),
        scryfall: Map<String, Long> = emptyMap(),
        tcgplayer: Map<String, Long> = emptyMap(),
    ) = ProviderCard(
        externalId = "mtg-$number",
        number = number,
        name = name,
        rarity = null,
        cardType = null,
        imageSmallUrl = null,
        imageLargeUrl = null,
        variants = variants,
        prices = scryfall.map { (variant, cents) -> ProviderPrice(variant, cents, null, updated) },
        cardmarketProductId = productId,
        fallbackPrices = tcgplayer.map { (variant, cents) -> ProviderPrice(variant, cents, null, updated, PriceSource.TCGPLAYER, Currency.USD) },
    )

    private fun magicSingle(productId: Long, name: String, trend: Long?, foilTrend: Long? = null, expansion: Long = 76) =
        single(productId, trend, name = name, metacard = null, expansion = expansion, foilTrend = foilTrend, game = Game.MTG)

    private fun LinkDecision.trendOf(variant: String) = prices.firstOrNull { it.variant == variant }?.trendCents

    private fun LinkDecision.trend() = prices.firstOrNull { it.variant == NORMAL }?.trendCents

    // Delta Reign, checked on 2026-11-19: TCGdex gave all three prints product 918912 (trend €2.93).
    private val rayquazas = listOf(
        card("084", tcgplayerTrend = 491),
        card("138", tcgplayerTrend = 27729),
        card("141", tcgplayerTrend = 21986),
    )
    private val rayquazaSingles = listOf(
        single(918912, 293),
        single(918931, 2, name = "Mega Rayquaza Cap", metacard = 466233),
        single(918982, 28545),
        single(918985, 12377),
    )

    @Test
    fun `gives each Rayquaza print its own product and price`() {
        val decisions = decide(rayquazas, rayquazaSingles)

        assertEquals(918912L, decisions.getValue("me06-084").productId)
        assertEquals(918982L, decisions.getValue("me06-138").productId)
        assertEquals(918985L, decisions.getValue("me06-141").productId)
        assertEquals(listOf(293L, 28545L, 12377L), listOf("084", "138", "141").map { decisions.getValue("me06-$it").trend() })
        assertTrue(decisions.values.all { it.link == CardmarketLink.MATCHED })
        assertTrue(decisions.values.all { decision -> decision.prices.all { it.source == PriceSource.CARDMARKET } })
    }

    @Test
    fun `matches in number order also without TCGplayer prices`() {
        val decisions = decide(rayquazas.map { it.copy(fallbackPrices = emptyList()) }, rayquazaSingles)

        assertEquals(listOf(918912L, 918982L, 918985L), listOf("084", "138", "141").map { decisions.getValue("me06-$it").productId })
    }

    @Test
    fun `a product only one card has stays that card's`() {
        val decisions = decide(listOf(card("001", name = "Pikachu", productId = 5)), listOf(single(5, 40, name = "Pikachu [Thunder]")))

        val decision = decisions.getValue("me06-001")
        assertEquals(CardmarketLink.SOURCE, decision.link)
        assertEquals(40L, decision.trend())
    }

    @Test
    fun `without today's file a unique product keeps TCGdex's own copy of its price`() {
        val decision = decide(listOf(card("001", name = "Pikachu", productId = 5, cardmarketTrend = 77)), emptyList()).getValue("me06-001")

        assertEquals(CardmarketLink.SOURCE, decision.link)
        assertEquals(77L, decision.trend())
    }

    @Test
    fun `a unique product whose price is worlds apart from TCGplayer's is doubtful`() {
        // Majestic Dawn Zapdos: €0.02 at Cardmarket, $66 at TCGplayer.
        val zapdos = card("014", name = "Zapdos", productId = 9, tcgplayerTrend = 6600)

        val decision = decide(listOf(zapdos), listOf(single(9, 2, name = "Zapdos Lv.45 [Sheet Lightning]"))).getValue("me06-014")

        assertEquals(CardmarketLink.DOUBTFUL, decision.link)
        assertEquals(9L, decision.productId)
        assertEquals(PriceSource.TCGPLAYER, decision.prices.single().source)
        assertEquals(6600L, decision.trend())
    }

    @Test
    fun `different cards that share a product are matched by name`() {
        // Sword & Shield: TCGdex gave Lapras and Lapras VMAX the product of Lapras VMAX.
        val lapras = card("048", name = "Lapras", productId = 427241, tcgplayerTrend = 30)
        val vmax = card("050", name = "Lapras VMAX", productId = 427241, tcgplayerTrend = 579)
        val singles = listOf(
            single(427200, 12, name = "Lapras [Ocean Swell]", metacard = 1, expansion = 2921),
            single(427241, 450, name = "Lapras VMAX [G-Max Pump]", metacard = 2, expansion = 2921),
        )

        val decisions = decide(listOf(lapras, vmax), singles)

        assertEquals(427200L, decisions.getValue("me06-048").productId)
        assertEquals(427241L, decisions.getValue("me06-050").productId)
    }

    @Test
    fun `when the counts differ each card gets the product closest to its TCGplayer price`() {
        // Skyridge Magcargo: three cards, two products.
        val cards = listOf(
            card("18", name = "Magcargo", productId = 275276, tcgplayerTrend = 1716),
            card("H16", name = "Magcargo", productId = 275276, tcgplayerTrend = null),
            card("H17", name = "Magcargo", productId = 275276, tcgplayerTrend = 20250),
        )
        val singles = listOf(
            single(275235, 15413, name = "Magcargo [Eruption | Fire Stream]", metacard = 7, expansion = 1538),
            single(275276, 362, name = "Magcargo [Eruption | Fire Stream]", metacard = 7, expansion = 1538),
        )

        val decisions = decide(cards, singles)

        assertEquals(275235L, decisions.getValue("me06-H17").productId)
        assertEquals(CardmarketLink.MATCHED, decisions.getValue("me06-H17").link)
        // €3.62 against $17.16 is more than three times apart, and H16 has nothing to compare with.
        assertEquals(CardmarketLink.DOUBTFUL, decisions.getValue("me06-18").link)
        assertEquals(CardmarketLink.DOUBTFUL, decisions.getValue("me06-H16").link)
        assertEquals(275276L, decisions.getValue("me06-H16").productId)
        assertTrue(decisions.getValue("me06-H16").prices.isEmpty())
    }

    @Test
    fun `a doubtful card never gets the shared price`() {
        val cards = listOf(card("131", name = "Squirtle", productId = 1), card("132", name = "Squirtle", productId = 1))

        val decisions = decide(cards, listOf(single(1, 2196, name = "Squirtle [Wave Splash]")))

        assertTrue(decisions.values.all { it.link == CardmarketLink.DOUBTFUL && it.prices.isEmpty() })
    }

    @Test
    fun `order that disagrees with TCGplayer falls back to prices`() {
        // Product ids don't follow the card numbers here.
        val cards = listOf(card("084", tcgplayerTrend = 27729), card("138", tcgplayerTrend = 491))
        val singles = listOf(single(918912, 293), single(918982, 28545))

        val decisions = decide(cards, singles)

        assertEquals(918982L, decisions.getValue("me06-084").productId)
        assertEquals(918912L, decisions.getValue("me06-138").productId)
    }

    @Test
    fun `a manual link is kept and priced from its product`() {
        val manual = mapOf("me06-138" to ManualLink(918985), "me06-141" to ManualLink(null))

        val decisions = decide(rayquazas, rayquazaSingles, manual)

        assertEquals(CardmarketLink.MANUAL, decisions.getValue("me06-138").link)
        assertEquals(12377L, decisions.getValue("me06-138").trend())
        assertEquals(CardmarketLink.MANUAL, decisions.getValue("me06-141").link)
        assertNull(decisions.getValue("me06-141").productId)
        assertEquals(PriceSource.TCGPLAYER, decisions.getValue("me06-141").prices.single().source)
        // 084 is the only one left on 918912 and keeps it.
        assertEquals(CardmarketLink.SOURCE, decisions.getValue("me06-084").link)
    }

    @Test
    fun `a card without a Cardmarket product shows its TCGplayer price`() {
        val decision = decide(listOf(card("001", productId = null, cardmarketTrend = null, tcgplayerTrend = 120)), emptyList())
            .getValue("me06-001")

        assertNull(decision.link)
        assertEquals(120L, decision.trend())
    }

    @Test
    fun `the foil column goes to the reverse holo`() {
        val prices = CardmarketLinks.pricesOf(setOf(NORMAL, REVERSE), single(1, 3, foilTrend = 15))

        assertEquals(mapOf(NORMAL to 3L, REVERSE to 15L), prices.associate { it.variant to it.trendCents })
    }

    @Test
    fun `strips levels and attacks from Cardmarket names`() {
        assertEquals("sceptile", CardmarketLinks.baseName(single(1, 1, name = "Sceptile Lv.57 [Green Breath | Leaf Blast]")))
        assertEquals("shaymin lv x", CardmarketLinks.baseName(single(1, 1, name = "Shaymin LV.X [Seed Flare]")))
    }

    @Test
    fun `price agreement`() {
        assertTrue(PriceAgreement.agrees(150, 10))
        assertTrue(PriceAgreement.agrees(300, 900))
        assertFalse(PriceAgreement.agrees(300, 901))
        assertFalse(PriceAgreement.agrees(293, 23900))
        assertTrue(PriceAgreement.agrees(600, 5000, PriceAgreement.SOURCE_FLOOR_CENTS, PriceAgreement.SOURCE_MAX_RATIO))
        assertFalse(PriceAgreement.agrees(2, 5690, PriceAgreement.SOURCE_FLOOR_CENTS, PriceAgreement.SOURCE_MAX_RATIO))
    }

    // Summer Magic, checked on 2026-11-19: Scryfall gave all three Islands product 16935 (€593.42).
    @Test
    fun `Magic gives each Summer Magic Island its own product`() {
        val islands = listOf("295", "296", "297").map { magic(it, "Island", 16935, scryfall = mapOf(NORMAL to 59342L)) }
        val singles = listOf(
            magicSingle(16935, "Island", 59342),
            magicSingle(16940, "Island", 65581),
            magicSingle(16945, "Island", 65454),
            magicSingle(16933, "Swamp", 38066),
        )

        val decisions = decide(islands, singles, game = Game.MTG)

        assertEquals(listOf(16935L, 16940L, 16945L), listOf("295", "296", "297").map { decisions.getValue("mtg-$it").productId })
        assertEquals(listOf(59342L, 65581L, 65454L), listOf("295", "296", "297").map { decisions.getValue("mtg-$it").trend() })
        assertTrue(decisions.values.all { it.link == CardmarketLink.MATCHED })
    }

    @Test
    fun `Magic foil prints listed as their own card share the product, each with its own column`() {
        // Seventh Edition Duress: 131 is nonfoil only, 131★ foil only, and Cardmarket has both in product 2893.
        val duress = magic("131", "Duress", 2893, setOf(NORMAL), tcgplayer = mapOf(NORMAL to 60L))
        val foil = magic("131★", "Duress", 2893, setOf(FOIL), tcgplayer = mapOf(FOIL to 13874L))

        val decisions = decide(listOf(duress, foil), listOf(magicSingle(2893, "Duress", 64, foilTrend = 11950)), game = Game.MTG)

        assertEquals(CardmarketLink.SOURCE, decisions.getValue("mtg-131").link)
        assertEquals(CardmarketLink.SOURCE, decisions.getValue("mtg-131★").link)
        assertEquals(64L, decisions.getValue("mtg-131").trendOf(NORMAL))
        assertEquals(11950L, decisions.getValue("mtg-131★").trendOf(FOIL))
        assertTrue(decisions.values.all { decision -> decision.prices.all { it.source == PriceSource.CARDMARKET } })
    }

    @Test
    fun `Magic cards that need the same column of one product are matched like Pokemon`() {
        // Secret Lair 681 and 681Φ are both foil only; Cardmarket has two products.
        val plain = magic("681", "Shadowborn Apostle", 760228, setOf(FOIL), tcgplayer = mapOf(FOIL to 2174L))
        val phi = magic("681Φ", "Shadowborn Apostle", 760228, setOf(FOIL), tcgplayer = mapOf(FOIL to 14898L))
        val singles = listOf(
            magicSingle(708378, "Shadowborn Apostle", null, foilTrend = 1101, expansion = 4000),
            magicSingle(760228, "Shadowborn Apostle", null, foilTrend = 8314, expansion = 4000),
        )

        val decisions = decide(listOf(plain, phi), singles, game = Game.MTG)

        assertEquals(708378L, decisions.getValue("mtg-681").productId)
        assertEquals(760228L, decisions.getValue("mtg-681Φ").productId)
        assertEquals(1101L, decisions.getValue("mtg-681").trendOf(FOIL))
    }

    @Test
    fun `Magic trusts a product only one card has, whatever TCGplayer says`() {
        // Summer Magic Library of Leng: €0.02 at Cardmarket, $400 at TCGplayer. Either can be wrong.
        val card = magic("261", "Library of Leng", 16810, tcgplayer = mapOf(NORMAL to 40000L))

        val decision = decide(listOf(card), listOf(magicSingle(16810, "Library of Leng", 2)), game = Game.MTG).getValue("mtg-261")

        assertEquals(CardmarketLink.SOURCE, decision.link)
        assertEquals(2L, decision.trend())
    }

    @Test
    fun `Magic keeps Scryfall's price when today's file has no trend for the product`() {
        val card = magic("53", "Copy Artifact", 16749, scryfall = mapOf(NORMAL to 702205L))

        val decision = decide(listOf(card), listOf(magicSingle(16749, "Copy Artifact", null)), game = Game.MTG).getValue("mtg-53")

        assertEquals(CardmarketLink.SOURCE, decision.link)
        assertEquals(702205L, decision.trend())
        assertEquals(PriceSource.CARDMARKET, decision.prices.single().source)
    }

    @Test
    fun `Magic etched foils keep their TCGplayer price`() {
        val card = magic("1074", "Sol Ring", 733593, setOf(ETCHED), tcgplayer = mapOf(ETCHED to 7314L))

        val decision = decide(listOf(card), listOf(magicSingle(733593, "Sol Ring", null, foilTrend = 5400)), game = Game.MTG).getValue("mtg-1074")

        assertEquals(CardmarketLink.SOURCE, decision.link)
        assertEquals(PriceSource.TCGPLAYER, decision.prices.single().source)
        assertEquals(7314L, decision.trendOf(ETCHED))
    }
}
