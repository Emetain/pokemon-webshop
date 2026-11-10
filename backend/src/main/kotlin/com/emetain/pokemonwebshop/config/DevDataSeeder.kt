package com.emetain.pokemonwebshop.config

import com.emetain.pokemonwebshop.domain.AccessoryType
import com.emetain.pokemonwebshop.domain.Carrier
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.Order
import com.emetain.pokemonwebshop.domain.OrderLine
import com.emetain.pokemonwebshop.domain.OrderStatus
import com.emetain.pokemonwebshop.domain.OrderStatusChange
import com.emetain.pokemonwebshop.domain.OrderStockState
import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.ProductLanguage
import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.domain.ProductType
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.domain.ShipmentPreference
import com.emetain.pokemonwebshop.domain.StockReason
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.DropProductRequest
import com.emetain.pokemonwebshop.dto.DropRequest
import com.emetain.pokemonwebshop.dto.HeroSlideRequest
import com.emetain.pokemonwebshop.dto.ProductRequest
import com.emetain.pokemonwebshop.repository.OrderRepository
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.repository.UserRepository
import com.emetain.pokemonwebshop.service.DropService
import com.emetain.pokemonwebshop.service.HeroSlideService
import com.emetain.pokemonwebshop.service.OrderMath
import com.emetain.pokemonwebshop.service.OrderNumbers
import com.emetain.pokemonwebshop.service.PricedLine
import com.emetain.pokemonwebshop.service.ProductService
import com.emetain.pokemonwebshop.service.ProductRules
import com.emetain.pokemonwebshop.service.ShippingPrice
import com.emetain.pokemonwebshop.service.StockService
import com.emetain.pokemonwebshop.service.TokenService
import com.emetain.pokemonwebshop.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
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
    private val dropService: DropService,
    private val orderRepository: OrderRepository,
    private val tokenService: TokenService,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val admin = seedUser(ADMIN_EMAIL, ADMIN_PASSWORD, "Demo Beheerder", Role.ADMIN)
        val customer = seedUser(CUSTOMER_EMAIL, CUSTOMER_PASSWORD, "Demo Klant", Role.CUSTOMER)
        if (productRepository.count() == 0L) {
            seedProducts(admin)
            seedSlides()
            transactions.executeWithoutResult { seedOrders(customer) }
            seedDrop(admin)
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
            Demo("Poketopia Mystery Box", Game.POKEMON, ProductType.MYSTERY_BOX, 4995, stock = 5, language = null),
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

    /**
     * One upcoming drop, two hours after startup on the next quarter hour, with a product only sold in the drop and
     * two booster boxes from the normal stock. The waiting room opens half an hour before the start.
     */
    private fun seedDrop(admin: User) {
        val exclusive = productService.create(
            ProductRequest(
                game = Game.POKEMON.slug,
                type = ProductType.COLLECTION_BOX,
                name = "Prismatic Evolutions Super Premium Collection",
                description = "Alleen in de drop. Maximaal 1 per klant.",
                language = ProductLanguage.EN,
                priceCents = 12995,
                status = ProductStatus.ACTIVE,
            ),
        )
        stockService.adjust(exclusive.id, 10, StockReason.PURCHASE, "Demovoorraad voor de drop", admin)
        val boosterBox = productRepository.findBySlug("destined-rivals-booster-box") ?: return

        val now = clock.instant()
        val quarter = Duration.ofMinutes(15).toSeconds()
        val startsAt = Instant.ofEpochSecond((now.plus(Duration.ofHours(2)).epochSecond / quarter + 1) * quarter)
        val drop = dropService.create(
            DropRequest(
                name = "Prismatic Evolutions drop",
                description = "De Super Premium Collection, alleen in deze drop, en twee Destined Rivals Booster Boxen voor een dropprijs.\n" +
                    "Kom een paar minuten voor de start naar de wachtkamer: bij de start krijgt iedereen die wacht een willekeurige plek.",
                startsAt = startsAt,
                maxPerCustomer = 2,
            ),
        )
        dropService.addProduct(drop.id, DropProductRequest(productId = exclusive.id, allocated = 10, maxPerCustomer = 1), admin)
        dropService.addProduct(drop.id, DropProductRequest(productId = boosterBox.id, allocated = 2, maxPerCustomer = 1, priceCents = 14995), admin)
        dropService.publish(drop.id)
        log.info("Demo drop starts at {}", startsAt)
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

    /**
     * Two paid orders of the demo customer, without Stripe: one to ship, with a pre-order in it, and one on its way.
     * Cancelling them in admin pays nothing back, because there is no payment at Stripe.
     */
    private fun seedOrders(customer: User) {
        val products = productRepository.findAll().associateBy { it.name }
        demoOrder(
            customer,
            listOf(products.getValue("Destined Rivals Elite Trainer Box") to 1, products.getValue("Delta Reign Elite Trainer Box") to 1),
            paidAgo = Duration.ofHours(3),
        )
        demoOrder(
            customer,
            listOf(products.getValue("Surging Sparks Booster") to 6, products.getValue("Toploaders 35pt (25 stuks)") to 2),
            paidAgo = Duration.ofDays(2),
        ) { order, at ->
            order.status = OrderStatus.SHIPPED
            order.shippedAt = at
            order.carrier = Carrier.POSTNL
            order.trackingCode = "3SDEMO1234567"
            order.trackingUrl = "https://jouw.postnl.nl/track-and-trace/3SDEMO1234567-NL-1421AB"
            order.history += OrderStatusChange(order, OrderStatus.SHIPPED, at, "Verzonden: PostNL 3SDEMO1234567")
        }
        log.info("Demo shop: 2 orders for {}", customer.email)
    }

    private fun demoOrder(
        customer: User,
        items: List<Pair<Product, Int>>,
        paidAgo: Duration,
        after: (Order, java.time.Instant) -> Unit = { _, _ -> },
    ) {
        val paidAt = clock.instant().minus(paidAgo)
        val priced = items.map { (product, quantity) -> PricedLine(ProductRules.shownPriceCents(product)!!, quantity) }
        val totals = OrderMath.totals(priced, ShippingPrice(495, 7500))
        val order = Order(
            number = OrderNumbers.generate(),
            user = customer,
            email = customer.email,
            customerName = customer.displayName,
            street = "Dorpsstraat",
            houseNumber = "12",
            postcode = "1421 AB",
            city = "Uithoorn",
            country = "NL",
            subtotalCents = totals.subtotalCents,
            shippingCents = totals.shippingCents,
            totalCents = totals.totalCents,
            vatCents = totals.vatCents,
            deliveryTime = "1 tot 2 werkdagen",
            shipmentPreference = ShipmentPreference.TOGETHER,
            accessToken = tokenService.generate(),
            cartId = null,
            termsAcceptedAt = paidAt,
            createdAt = paidAt.minusSeconds(90),
            updatedAt = paidAt,
            status = OrderStatus.PAID,
            stockState = OrderStockState.SOLD,
            paymentMethod = "ideal",
            paidAt = paidAt,
        )
        items.zip(priced).forEach { (item, price) ->
            val (product, quantity) = item
            order.lines += OrderLine(
                order, product, product.name, product.slug, product.type, product.images.firstOrNull(), false,
                price.unitPriceCents, quantity, price.totalCents, product.preOrder, product.shipsFrom,
            )
            stockService.sell(product.id, quantity, "Demobestelling ${order.number}")
        }
        order.history += OrderStatusChange(order, OrderStatus.PENDING_PAYMENT, order.createdAt, "Afgerekend, wacht op de betaling")
        order.history += OrderStatusChange(order, OrderStatus.PAID, paidAt, "Betaald met iDEAL (demo, zonder Stripe)")
        after(order, paidAt.plus(Duration.ofHours(20)))
        orderRepository.save(order)
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
