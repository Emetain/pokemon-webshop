package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.ProductLanguage
import com.emetain.pokemonwebshop.domain.ProductType
import com.emetain.pokemonwebshop.service.pictures.PictureMatch
import com.emetain.pokemonwebshop.service.pictures.PictureMatching
import com.emetain.pokemonwebshop.service.pictures.TcgGroup
import com.emetain.pokemonwebshop.service.pictures.TcgProduct
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The examples are real: the owner's catalog against TCGplayer's on 2026-11-13. */
class PictureMatchingTest {

    private var nextId = 1L
    private fun sealed(name: String) = TcgProduct(nextId++, name, null)
    private fun card(name: String, number: String) = TcgProduct(nextId++, name, number)
    private fun group(name: String, abbreviation: String? = null, date: String? = null) =
        TcgGroup(nextId++, name, abbreviation, date?.let(LocalDate::parse))

    private fun sureName(match: PictureMatch): String = assertIs<PictureMatch.Sure>(match).product.name

    @Test
    fun `sets match by name without the code in front, by code, or by a name that contains the other`() {
        val surging = group("SV08: Surging Sparks", "SSP", "2024-11-08")
        val lostThunder = group("SM - Lost Thunder", "SM8", "2018-11-02")
        val xyBase = group("XY Base Set", "XY", "2014-02-05")
        val xyKit = group("XY Trainer Kit: Sylveon & Noivern", null, "2014-02-05")
        val mh2 = group("Modern Horizons 2", "MH2", "2021-06-18")
        val mh2Art = group("Art Series: Modern Horizons 2", "AMH2", "2021-06-18")
        val groups = listOf(surging, lostThunder, xyBase, xyKit, mh2, mh2Art)

        assertEquals(surging, PictureMatching.groupFor("sv08", "Surging Sparks", LocalDate.parse("2024-11-08"), groups))
        assertEquals(lostThunder, PictureMatching.groupFor("sm8", "Lost Thunder", LocalDate.parse("2018-11-02"), groups))
        assertEquals(xyBase, PictureMatching.groupFor("xy1", "XY", LocalDate.parse("2014-02-05"), groups), "the closest name, not a trainer kit")
        assertEquals(mh2Art, PictureMatching.groupFor("amh2", "Modern Horizons 2 Art Series", LocalDate.parse("2021-06-18"), groups))
        assertNull(PictureMatching.groupFor("bog", "Best of game", LocalDate.parse("2002-12-01"), groups))
        assertEquals("art horizons modern series 2".split(' ').sorted().joinToString(" "), PictureMatching.setKey("Art Series: Modern Horizons 2"))
        assertEquals(PictureMatching.setKey("Modern Horizons 2 Art Series"), PictureMatching.setKey("Art Series: Modern Horizons 2"))
    }

    @Test
    fun `cards match by number and name, and a variant or another name goes to the admin`() {
        val products = listOf(
            card("Masquerain - 003/103", "003/103"),
            card("Virizion", "009/103"),
            card("Monkey.D.Luffy (003)", "OP01-003"),
            card("Monkey.D.Luffy (003) (Parallel)", "OP01-003"),
            card("Nidoran F", "055/103"),
            card("Bubbly W Energy", "132/103"),
            card("Rowlet", "SV001/SV122"),
            sealed("Delta Reign Booster Pack"),
        )
        assertEquals("Masquerain - 003/103", sureName(PictureMatching.matchCard("003", "Masquerain", products)))
        assertEquals("Virizion", sureName(PictureMatching.matchCard("9", "Virizion", products)))
        assertEquals("Monkey.D.Luffy (003)", sureName(PictureMatching.matchCard("OP01-003", "Monkey.D.Luffy", products)), "the plain one, not the parallel")
        assertEquals("Nidoran F", sureName(PictureMatching.matchCard("055", "Nidoran♀", products)))
        assertEquals("Rowlet", sureName(PictureMatching.matchCard("SV001", "Rowlet", products)))
        val energy = assertIs<PictureMatch.Doubtful>(PictureMatching.matchCard("132", "Bubbly Water Energy", products))
        assertEquals(listOf("Bubbly W Energy"), energy.candidates.map { it.product.name })
        assertIs<PictureMatch.None>(PictureMatching.matchCard("200", "Unknown", products))
        assertEquals("pokemon catcher", PictureMatching.cardName("Pokémon Catcher"))
    }

    private val surgingSparks = listOf(
        "Surging Sparks Build & Battle Box", "Surging Sparks Build & Battle Box Display", "Surging Sparks Sleeved Booster Pack",
        "Surging Sparks Sleeved Booster Pack Bundle [Set of 4]", "Surging Sparks Booster Pack", "Surging Sparks Booster Box",
        "Surging Sparks Booster Bundle (LGS)", "Surging Sparks Elite Trainer Box", "Surging Sparks Elite Trainer Box Case",
        "Surging Sparks Pokemon Center Elite Trainer Box (Exclusive)", "Surging Sparks 3 Pack Blisters [Zapdos]",
        "Surging Sparks 3 Pack Blisters [Quagsire]", "Surging Sparks Single Pack Blister [Wooper]",
        "Surging Sparks Single Pack Blister [Pachirisu]", "Surging Sparks Premium Checklane Blister [Alakazam]",
        "Surging Sparks Premium Checklane Blister [Chandelure]", "Surging Sparks Booster Box Case", "Code Card - Surging Sparks Booster Pack",
        "Surging Sparks Fun Pack", "Surging Sparks Half Booster Box", "Surging Sparks Booster Bundle (Retail)",
    ).map(::sealed)

    private fun matchSurging(name: String, type: ProductType) =
        PictureMatching.matchProduct(name, type, listOf("Surging Sparks", "SV08: Surging Sparks"), surgingSparks)

    @Test
    fun `sealed products match with other words, order and Dutch names`() {
        assertEquals("Surging Sparks Booster Box", sureName(matchSurging("Surging Sparks Booster Box", ProductType.BOOSTER_BOX)))
        assertEquals("Surging Sparks Half Booster Box", sureName(matchSurging("Surging Sparks Halve Booster Box", ProductType.BOOSTER_BOX)))
        assertEquals("Surging Sparks Booster Pack", sureName(matchSurging("Surging Sparks Booster", ProductType.BOOSTER_PACK)))
        assertEquals("Surging Sparks Booster Pack", sureName(matchSurging("Surging Sparks Booster (6 Cards)", ProductType.BOOSTER_PACK)))
        assertEquals("Surging Sparks Fun Pack", sureName(matchSurging("Surging Sparks Fun Pack (3 Cards)", ProductType.BOOSTER_PACK)))
        assertEquals("Surging Sparks Sleeved Booster Pack", sureName(matchSurging("Surging Sparks Sleeved Booster", ProductType.SLEEVED_BOOSTER)))
        assertEquals("Surging Sparks Elite Trainer Box", sureName(matchSurging("Surging Sparks Elite Trainer Box", ProductType.ELITE_TRAINER_BOX)))
        assertEquals("Surging Sparks Build & Battle Box", sureName(matchSurging("Surging Sparks Build & Battle Box", ProductType.COLLECTION_BOX)))
        assertEquals("Surging Sparks Single Pack Blister [Pachirisu]", sureName(matchSurging("Surging Sparks: Pachirisu 1-Pack Blister", ProductType.BLISTER)))
        assertEquals("Surging Sparks 3 Pack Blisters [Quagsire]", sureName(matchSurging("Surging Sparks: Quagsire 3-Pack Blister", ProductType.BLISTER)))
        assertEquals(
            "Surging Sparks Premium Checklane Blister [Alakazam]",
            sureName(matchSurging("Surging Sparks: Alakazam Premium Checklane Blister", ProductType.BLISTER)),
        )
    }

    @Test
    fun `two versions that fit equally well go to the admin, and cases, displays and code cards never match`() {
        val bundle = assertIs<PictureMatch.Doubtful>(matchSurging("Surging Sparks Booster Bundle", ProductType.BOOSTER_BUNDLE))
        assertEquals(
            setOf("Surging Sparks Booster Bundle (LGS)", "Surging Sparks Booster Bundle (Retail)"),
            bundle.candidates.take(2).map { it.product.name }.toSet(),
        )
        val all = PictureMatching.matchProduct("Surging Sparks Booster Box", ProductType.BOOSTER_BOX, listOf("Surging Sparks"), surgingSparks)
        assertFalse(sureName(all).contains("Case"))
        assertEquals(0, PictureMatching.productScore("Surging Sparks Booster", ProductType.BOOSTER_PACK, listOf("Surging Sparks"), sealed("Code Card - Surging Sparks Booster Pack")))
    }

    @Test
    fun `Magic boxes are displays at TCGplayer and a fat pack is a bundle`() {
        val bloomburrow = listOf("Bloomburrow - Play Booster Display", "Bloomburrow - Play Booster Pack", "Bloomburrow - Collector Booster Display", "Bloomburrow - Bundle").map(::sealed)
        fun match(name: String, type: ProductType) = PictureMatching.matchProduct(name, type, listOf("Bloomburrow"), bloomburrow)
        assertEquals("Bloomburrow - Play Booster Display", sureName(match("Bloomburrow Play Booster Box", ProductType.BOOSTER_BOX)))
        assertEquals("Bloomburrow - Play Booster Pack", sureName(match("Bloomburrow Play Booster", ProductType.BOOSTER_PACK)))
        assertEquals("Bloomburrow - Bundle", sureName(match("Bloomburrow Fat Pack Bundle", ProductType.BOOSTER_BUNDLE)))
    }

    @Test
    fun `products in another language get no English picture`() {
        assertTrue(PictureMatching.skipProduct("Romance Dawn Booster (Non-English)", ProductLanguage.EN))
        assertTrue(PictureMatching.skipProduct("Surging Sparks Booster", ProductLanguage.JP))
        assertFalse(PictureMatching.skipProduct("Surging Sparks Booster", ProductLanguage.EN))
        assertFalse(PictureMatching.skipProduct("Surging Sparks Booster", null))
    }

    @Test
    fun `release suggestions leave out tokens and promos, and keep one per name with the catalog's date`() {
        assertFalse(ReleaseSuggestionRules.worthSuggesting("Nauctis: The Sunken Realm Tokens"))
        assertFalse(ReleaseSuggestionRules.worthSuggesting("MagicFest 2027"))
        assertTrue(ReleaseSuggestionRules.worthSuggesting("Nauctis: The Sunken Realm"))
        val merged = ReleaseSuggestionRules.merge(
            listOf(
                UpcomingSet(com.emetain.pokemonwebshop.domain.Game.MTG, "Marvel: Darkhold Destiny", LocalDate.parse("2027-04-10"), com.emetain.pokemonwebshop.domain.ReleaseSource.TCGPLAYER),
                UpcomingSet(com.emetain.pokemonwebshop.domain.Game.MTG, "Marvel: Darkhold Destiny", LocalDate.parse("2027-04-09"), com.emetain.pokemonwebshop.domain.ReleaseSource.CATALOG, "dhd"),
                UpcomingSet(com.emetain.pokemonwebshop.domain.Game.MTG, "Marvel: Darkhold Destiny Tokens", LocalDate.parse("2027-04-09"), com.emetain.pokemonwebshop.domain.ReleaseSource.CATALOG, "tdhd"),
            ),
        )
        assertEquals(1, merged.size)
        assertEquals(LocalDate.parse("2027-04-09"), merged.single().releaseDate)
        assertEquals("dhd", merged.single().setCode)
    }
}
