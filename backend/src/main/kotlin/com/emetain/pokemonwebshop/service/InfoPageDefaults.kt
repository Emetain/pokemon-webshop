package com.emetain.pokemonwebshop.service

/** A page under /info with the text it starts with. [draft] pages must be checked before the shop opens. */
data class DefaultInfoPage(val slug: String, val title: String, val draft: Boolean, val body: String)

/**
 * The starting texts of the info pages, created at startup when a page doesn't exist yet. The admin edits them at
 * /admin/paginas, and an edited page is never overwritten. The legal pages are drafts: they were written by the
 * developer as a starting point, not by a lawyer, and the owner checks them in the legal check before going live.
 * Text between [square brackets] is for the owner to fill in.
 */
object InfoPageDefaults {

    val PAGES = listOf(
        DefaultInfoPage(
            slug = "verzending",
            title = "Verzending en levertijd",
            draft = false,
            body = """
                Wij versturen vanuit Nederland naar Nederland en België. Hieronder zie je wat verzenden kost en wanneer je pakket er is.

                ## Levertijd
                - We versturen je bestelling binnen [1 werkdag] nadat je betaling binnen is.
                - De levertijd per land zie je in je winkelwagen en bij het afrekenen.
                - Je krijgt een mail met een track & trace-code zodra je pakket onderweg is.

                ## Pre-orders
                Een pre-order versturen we zodra het product bij ons binnen is, meestal op of vlak voor de releasedatum. Zit er in je bestelling ook iets dat al op voorraad is? Dan kies je bij het afrekenen of we alles samen versturen of wat er al is eerst.

                ## Verpakking
                Kaarten en sealed producten verpakken we stevig, met extra bescherming voor dozen en losse kaarten in een toploader.
            """.trimIndent(),
        ),
        DefaultInfoPage(
            slug = "retourneren",
            title = "Retourneren",
            draft = true,
            body = """
                Niet tevreden? Je hebt 14 dagen bedenktijd vanaf de dag dat je je bestelling ontvangt.

                ## Zo werkt het
                - Meld je retour binnen 14 dagen met een ticket op de [contactpagina](/info/contact) of per mail, met je bestelnummer.
                - Stuur het product daarna binnen 14 dagen terug naar het adres dat je van ons krijgt.
                - De kosten van het terugsturen zijn voor jou.
                - We betalen het aankoopbedrag, en de standaard verzendkosten van je bestelling, binnen 14 dagen terug zodra we het product hebben ontvangen.

                ## Wat je niet kunt retourneren
                - **Geopende sealed producten**, zoals booster packs, booster boxes en Elite Trainer Boxes waarvan de seal of folie verbroken is. Bij een geopend product is niet meer na te gaan wat erin zat.
                - Geopende mystery boxes.
                - Graded kaarten waarvan de slab beschadigd of geopend is.

                Stuur een product terug in de staat waarin je het ontving, met de seal en verpakking heel.

                ## Kapot of verkeerd geleverd?
                Neem binnen 2 werkdagen na ontvangst contact op, het liefst met een foto. Dan lossen we het op, zonder kosten voor jou.
            """.trimIndent(),
        ),
        DefaultInfoPage(
            slug = "algemene-voorwaarden",
            title = "Algemene voorwaarden",
            draft = true,
            body = """
                Deze voorwaarden gelden voor elke bestelling bij Poketopia.

                ## 1. Wie wij zijn
                Poketopia, [adres], [postcode en plaats]. KVK-nummer [nummer], btw-nummer [nummer]. E-mail: [e-mailadres].

                ## 2. Aanbod en prijzen
                - Alle prijzen zijn in euro en inclusief 21% btw. Verzendkosten zie je in je winkelwagen voordat je betaalt.
                - Afbeeldingen van kaarten en producten zijn ter illustratie. Kleine verschillen in verpakking zijn mogelijk.
                - Kennelijke vergissingen in prijzen of omschrijvingen binden ons niet.

                ## 3. De overeenkomst
                De overeenkomst komt tot stand zodra je betaling is gelukt. Je krijgt daarvan een bevestiging per mail.

                ## 4. Pre-orders
                Een pre-order bestel en betaal je voordat het product uitkomt. De releasedatum komt van de uitgever en kan verschuiven. Krijgen wij minder binnen dan besteld, dan leveren we in volgorde van bestellen en betalen we de rest terug.

                ## 5. Drops
                Bij een drop gelden limieten per klant, per adres en per product. Bestellingen die deze limieten omzeilen, mogen wij annuleren. Je krijgt dan je geld terug.

                ## 6. Mystery boxes
                Een mystery box komt uit een batch die wij met de hand hebben ingepakt. Op de productpagina staat wat er in de batch zit en hoe groot de kans op elke soort doos is, gerekend over de dozen die nog over zijn. Welke doos jij krijgt, kiest het systeem willekeurig zodra je betaling binnen is.

                ## 7. Kortingscodes
                Een kortingscode geldt alleen zolang en voor de producten die bij de code staan, niet voor verzendkosten, en is niet in te wisselen voor geld.

                ## 8. Herroepingsrecht
                Je hebt 14 dagen bedenktijd. Hoe dat werkt en voor welke producten het niet geldt, staat op de pagina [Retourneren](/info/retourneren).

                ## 9. Levering
                We leveren in Nederland en België, op het adres dat je bij de bestelling opgeeft. Het risico van verlies of beschadiging gaat op jou over zodra je het pakket ontvangt.

                ## 10. Garantie en klachten
                Je hebt recht op een product dat doet wat je mag verwachten. Klachten meld je binnen redelijke tijd met een ticket op de [contactpagina](/info/contact). We reageren binnen 14 dagen.

                ## 11. Premium
                - Premium is een abonnement op extra functies van de site, zoals onbeperkt kaarten scannen, de volledige waardegrafiek en de pre-grade check. Het kost €6,99 per maand of €59,99 per jaar, inclusief btw, en wordt elke maand of elk jaar automatisch verlengd tot je opzegt.
                - Opzeggen of wisselen tussen maand en jaar doe je zelf onder Mijn account. Na opzeggen houd je Premium tot het einde van de periode die je al hebt betaald.
                - Premium is een digitale dienst die direct begint. Bij het afsluiten vragen we je toestemming daarvoor en bevestig je dat je herroepingsrecht vervalt zodra Premium begint.
                - Lukt een verlenging niet, dan stopt Premium tot de betaling alsnog lukt.
                - De pre-grade check is een indicatie, geen officiële beoordeling. Alleen een gradingbedrijf bepaalt een grade.

                ## 12. Geschillen
                Op deze voorwaarden is Nederlands recht van toepassing. Komen we er samen niet uit, dan kun je je klacht ook voorleggen via het Europese ODR-platform: https://ec.europa.eu/consumers/odr.
            """.trimIndent(),
        ),
        DefaultInfoPage(
            slug = "privacybeleid",
            title = "Privacybeleid",
            draft = true,
            body = """
                Poketopia gaat zorgvuldig om met je gegevens. Hier lees je welke gegevens we bewaren, waarom, en wat je rechten zijn.

                ## Wie is verantwoordelijk
                Poketopia, [adres], KVK-nummer [nummer]. Vragen over privacy stel je via [e-mailadres].

                ## Welke gegevens en waarom
                - **Account:** je naam, e-mailadres, land en wachtwoord (versleuteld), om in te loggen en je bestellingen te bewaren.
                - **Bestellingen:** naam, adres, e-mailadres, wat je bestelt en hoe je betaalt, om je bestelling te leveren en voor onze boekhouding (7 jaar, wettelijk verplicht).
                - **Collectie:** de kaarten die je bijhoudt en één waarde per dag voor de grafiek van je collectie. Deel je je collectie, dan ziet iedereen met de link je voornaam, je kaarten en de waarde.
                - **Drops:** je aanmelding voor een herinnering, je plek in de wachtrij (je account of een cookie, en wanneer je meedeed) en bij een bestelling in een drop een versie van je e-mailadres en adres, om de limieten per klant te bewaken.
                - **Reviews:** je beoordeling met je voornaam.
                - **Nieuwsbrief:** je e-mailadres, alleen als je je aanmeldt en dat bevestigt. Afmelden kan altijd via de link in elke mail.
                - **Tickets:** je naam, e-mailadres, eventueel je bestelnummer en wat je ons schrijft, om je vraag te beantwoorden. We bewaren een ticket [bewaartermijn].
                - **Premium:** je abonnement, wanneer het begon en tot wanneer het loopt, en je klantnummer bij Stripe, om Premium te leveren en te verlengen. Je betaalgegevens staan alleen bij Stripe.
                - **Kaartscanner:** de foto die je scant gaat naar onze server en naar OpenAI, dat de naam en het nummer van de kaart leest. We bewaren de foto niet na de scan; OpenAI gebruikt hem niet voor het trainen van zijn modellen. We tellen alleen hoeveel scans je gebruikte.
                - **Assistent:** wat je de digitale assistent vraagt gaat naar OpenAI, dat het antwoord maakt. OpenAI gebruikt het niet voor het trainen van zijn modellen en bewaart het hoogstens [termijn, zie de voorwaarden van OpenAI] tegen misbruik. Wij bewaren het gesprek niet. Stuur je het gesprek mee met een ticket, dan staat het bij dat ticket.

                ## Wie je gegevens ziet
                - De beheerders van de shop: [namen van iedereen met beheerderstoegang].
                - Stripe, voor de betaling.
                - OpenAI, voor de antwoorden van de digitale assistent en het lezen van gescande kaarten.
                - [Hostingpartij] in Nederland, waar de shop draait.
                - [Mailprovider], die onze mails verstuurt.
                - De vervoerder, voor de bezorging van je pakket.

                ## Cookies
                We gebruiken alleen functionele cookies: om je ingelogd te houden, voor je winkelwagen en voor de wachtrij van een drop. Geen tracking en geen advertentiecookies.

                ## Je rechten
                Je mag je gegevens inzien, laten verbeteren of laten verwijderen, en bezwaar maken. Stuur daarvoor een mail naar [e-mailadres]. Ben je niet tevreden over hoe we met je gegevens omgaan, dan kun je een klacht indienen bij de Autoriteit Persoonsgegevens.
            """.trimIndent(),
        ),
        DefaultInfoPage(
            slug = "veelgestelde-vragen",
            title = "Veelgestelde vragen",
            draft = false,
            body = """
                ## Wanneer wordt mijn bestelling verstuurd?
                Binnen [1 werkdag] nadat je betaling binnen is. Pre-orders versturen we zodra ze binnen zijn. Meer op [Verzending en levertijd](/info/verzending).

                ## Hoe betaal ik?
                Met iDEAL | Wero of een creditcard in Nederland, met Bancontact of een creditcard in België.

                ## Wat is een drop?
                Een beperkte release op een vast moment. Je komt in een wachtrij en we laten mensen in willekeurige volgorde door, zodat het eerlijk blijft. Meld je aan voor een herinnering op de pagina van de drop.

                ## Hoe werkt een mystery box?
                We pakken een hele batch met de hand in en zetten op de productpagina precies wat erin zit, met de kans op elke soort doos. Na je betaling kiest het systeem willekeurig een doos uit wat er nog over is.

                ## Kan ik mijn kaarten bijhouden?
                Ja, met een gratis account houd je per set bij welke kaarten je hebt en wat je collectie waard is, voor Pokémon, Lorcana, Magic, One Piece en Riftbound.

                ## Kan ik een product retourneren?
                Binnen 14 dagen, zolang een sealed product dicht is. Zie [Retourneren](/info/retourneren).
            """.trimIndent(),
        ),
        DefaultInfoPage(
            slug = "contact",
            title = "Contact",
            draft = false,
            body = """
                Vragen over een bestelling, een product of een drop? Kijk eerst bij de veelgestelde vragen of vraag het onze digitale assistent hieronder. Kom je er niet uit, maak dan een ticket. We reageren op werkdagen binnen [1 werkdag].

                Heb je een vraag over een bestelling? Zet je bestelnummer erbij, dan kunnen we je sneller helpen.
            """.trimIndent(),
        ),
        DefaultInfoPage(
            slug = "over-ons",
            title = "Over ons",
            draft = false,
            body = """
                Poketopia is een webshop voor verzamelaars en spelers van trading card games, met Pokémon als grootste liefde. Je vindt bij ons sealed producten, losse kaarten en accessoires voor Pokémon, Disney Lorcana, Magic: The Gathering, One Piece en Riftbound.

                Naast de shop kun je gratis je collectie bijhouden: per set zie je welke kaarten je hebt, wat er nog mist en wat je collectie waard is.

                [Vertel hier wie jullie zijn en waar jullie vandaan komen.]
            """.trimIndent(),
        ),
    )

    val SLUGS: Set<String> = PAGES.map { it.slug }.toSet()
}
