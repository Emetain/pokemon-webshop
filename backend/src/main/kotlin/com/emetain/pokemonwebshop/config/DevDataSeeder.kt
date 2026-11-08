package com.emetain.pokemonwebshop.config

import com.emetain.pokemonwebshop.domain.AccessoryType
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.ProductLanguage
import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.domain.ProductType
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.domain.StockReason
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.HeroSlideRequest
import com.emetain.pokemonwebshop.dto.ProductRequest
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.repository.UserRepository
import com.emetain.pokemonwebshop.service.HeroSlideService
import com.emetain.pokemonwebshop.service.ProductService
import com.emetain.pokemonwebshop.service.StockService
import com.emetain.pokemonwebshop.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Creates demo data for local development. Never active outside the dev profile. */
@Component
@Profile("dev")
class DevDataSeeder(
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val productService: ProductService,
    private val stockService: StockService,
    private val heroSlideService: HeroSlideService,
    private val clock: Clock,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val admin = seedUser(ADMIN_EMAIL, ADMIN_PASSWORD, "Demo Beheerder", Role.ADMIN)
        seedUser(CUSTOMER_EMAIL, CUSTOMER_PASSWORD, "Demo Klant", Role.CUSTOMER)
        if (productRepository.count() == 0L) {
            seedProducts(admin)
            seedSlides()
        }

        log.info("Demo admin login: {} / {}", ADMIN_EMAIL, ADMIN_PASSWORD)
        log.info("Demo customer login: {} / {}", CUSTOMER_EMAIL, CUSTOMER_PASSWORD)
    }

    private fun seedUser(email: String, password: String, displayName: String, role: Role): User =
        userRepository.findByEmail(email) ?: userService.register(email, password, displayName, role)

    /** The catalog is empty in dev, so these have no set or card. A sync and the import add real ones. */
    private fun seedProducts(admin: User) {
        val inAMonth = LocalDate.now(clock.withZone(ZoneId.of("Europe/Amsterdam"))).plusDays(30)
        val products = listOf(
            Demo("Destined Rivals Elite Trainer Box", Game.POKEMON, ProductType.ELITE_TRAINER_BOX, 5495, stock = 12,
                description = "9 boosters, 65 sleeves, 45 energiekaarten, dobbelstenen en een opbergdoos.\nEngelstalig."),
            Demo("Destined Rivals Booster Box", Game.POKEMON, ProductType.BOOSTER_BOX, 16995, stock = 3,
                description = "36 boosters met elk 10 kaarten. Engelstalig."),
            Demo("Prismatic Evolutions Booster Bundle", Game.POKEMON, ProductType.BOOSTER_BUNDLE, 4495, sale = 3995, stock = 6),
            Demo("Surging Sparks Booster", Game.POKEMON, ProductType.BOOSTER_PACK, 495, stock = 120),
            Demo("Journey Together Sleeved Booster", Game.POKEMON, ProductType.SLEEVED_BOOSTER, 549, stock = 10, sold = 10),
            Demo("Delta Reign Elite Trainer Box", Game.POKEMON, ProductType.ELITE_TRAINER_BOX, 5995, stock = 20,
                preOrder = inAMonth, description = "Pre-order. We verzenden op de releasedag."),
            Demo("Pokémon Mini Tin", Game.POKEMON, ProductType.TIN, 1295, stock = 1),
            Demo("Charizard ex Premium Collection", Game.POKEMON, ProductType.COLLECTION_BOX, 3995),
            Demo("Pokémon bulk: 100 kaarten", Game.POKEMON, ProductType.BULK, 995, stock = 15, language = null),
            Demo("Uithoorn TCG Mystery Box", Game.POKEMON, ProductType.MYSTERY_BOX, 4995, stock = 5, language = null),
            Demo("Archazia's Island Booster Box", Game.LORCANA, ProductType.BOOSTER_BOX, 13495, stock = 4),
            Demo("Fabled Starter Deck", Game.LORCANA, ProductType.STARTER_DECK, 1695, stock = 8),
            Demo("Foundations Play Booster Box", Game.MTG, ProductType.BOOSTER_BOX, 14995, stock = 5),
            Demo("Duskmourn Collector Booster", Game.MTG, ProductType.BOOSTER_PACK, 2995, stock = 10),
            Demo("Royal Blood Booster Box", Game.ONE_PIECE, ProductType.BOOSTER_BOX, 9995, stock = 2),
            Demo("Starter Deck: Boa Hancock", Game.ONE_PIECE, ProductType.STARTER_DECK, 1495),
            Demo("Matte sleeves blauw (100 stuks)", null, ProductType.ACCESSORY, 1195, stock = 30, language = null,
                accessory = AccessoryType.SLEEVES),
            Demo("Toploaders 35pt (25 stuks)", null, ProductType.ACCESSORY, 449, stock = 50, language = null,
                accessory = AccessoryType.TOPLOADERS),
            Demo("9-pocket verzamelmap Poké Ball", Game.POKEMON, ProductType.ACCESSORY, 2495, stock = 6, language = null,
                accessory = AccessoryType.BINDERS),
            Demo("Acryl displaycase voor een booster box", null, ProductType.ACCESSORY, 3495, stock = 3, language = null,
                accessory = AccessoryType.DISPLAY_CASES),
        )
        for (demo in products) {
            val product = productService.create(
                ProductRequest(
                    game = demo.game?.slug,
                    type = demo.type,
                    accessoryType = demo.accessory,
                    name = demo.name,
                    description = demo.description,
                    language = demo.language,
                    priceCents = demo.priceCents,
                    salePriceCents = demo.sale,
                    status = ProductStatus.ACTIVE,
                    preOrder = demo.preOrder != null,
                    releaseDate = demo.preOrder,
                    shipsFrom = demo.preOrder,
                ),
            )
            if (demo.stock > 0) stockService.adjust(product.id, demo.stock, StockReason.PURCHASE, "Demovoorraad", admin)
            if (demo.sold > 0) stockService.sell(product.id, demo.sold, "Demoverkoop")
        }
        log.info("Demo shop: {} products", products.size)
    }

    private fun seedSlides() {
        heroSlideService.create(
            HeroSlideRequest(
                title = "Destined Rivals is binnen",
                text = "Elite Trainer Boxen, booster boxen en losse boosters van de nieuwste Pokémon-set.",
                buttonLabel = "Bekijk Pokémon",
                linkUrl = "/pokemon",
                game = Game.POKEMON.slug,
            ),
        )
        heroSlideService.create(
            HeroSlideRequest(
                title = "Pre-order Delta Reign",
                text = "Reserveer nu, dan heb je hem op de releasedag in huis.",
                buttonLabel = "Naar de pre-orders",
                linkUrl = "/pre-order",
            ),
        )
        heroSlideService.create(
            HeroSlideRequest(
                title = "Nieuw in Lorcana: Archazia's Island",
                text = "Booster boxen en starterdecks, nu op voorraad.",
                buttonLabel = "Bekijk Lorcana",
                linkUrl = "/lorcana",
                game = Game.LORCANA.slug,
            ),
        )
    }

    private data class Demo(
        val name: String,
        val game: Game?,
        val type: ProductType,
        val priceCents: Long,
        val sale: Long? = null,
        val stock: Int = 0,
        val sold: Int = 0,
        val preOrder: LocalDate? = null,
        val description: String? = null,
        val language: ProductLanguage? = ProductLanguage.EN,
        val accessory: AccessoryType? = null,
    )

    private companion object {
        const val ADMIN_EMAIL = "admin@example.com"
        const val ADMIN_PASSWORD = "admin12345"
        const val CUSTOMER_EMAIL = "klant@example.com"
        const val CUSTOMER_PASSWORD = "klant12345"
    }
}
