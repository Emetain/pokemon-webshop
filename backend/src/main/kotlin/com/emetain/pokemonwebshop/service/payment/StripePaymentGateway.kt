package com.emetain.pokemonwebshop.service.payment

import com.emetain.pokemonwebshop.config.AppProperties
import com.stripe.StripeClient
import com.stripe.exception.InvalidRequestException
import com.stripe.exception.StripeException
import com.emetain.pokemonwebshop.domain.PremiumPlan
import com.stripe.model.Price
import com.stripe.model.checkout.Session
import com.stripe.net.RequestOptions
import com.stripe.param.CouponCreateParams
import com.stripe.param.CustomerCreateParams
import com.stripe.param.PriceCreateParams
import com.stripe.param.PriceListParams
import com.stripe.param.ProductCreateParams
import com.stripe.param.RefundCreateParams
import com.stripe.param.billingportal.ConfigurationCreateParams
import com.stripe.param.billingportal.ConfigurationListParams
import com.stripe.param.checkout.SessionCreateParams
import com.stripe.param.checkout.SessionRetrieveParams
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Stripe Checkout for orders, and Stripe Billing for Premium (phase 11). The library pins its own API version (`2025-05-28.basil` for stripe-java 29.2.0), so every call
 * here speaks that version whatever the account's default is.
 */
@Component
class StripePaymentGateway(properties: AppProperties) : PaymentGateway, SubscriptionGateway {

    private val log = LoggerFactory.getLogger(javaClass)
    private val secretKey = properties.payments.stripe.secretKey
    private val keyProblem = StripeKeyRules.problem(secretKey, properties.shopAccess)
        ?.also { log.error(it.logMessage) }
    private val client: StripeClient? = secretKey.takeIf { it.isNotBlank() && keyProblem == null }?.let(::StripeClient)
    private val premium = properties.premium
    private val baseUrl = properties.baseUrl.trimEnd('/')

    // Found or made once per run: the Premium product, its two prices and the customer portal's settings.
    @Volatile private var premiumPrices: Map<PremiumPlan, PremiumPrice>? = null

    @Volatile private var portalConfiguration: String? = null

    override val configured: Boolean get() = client != null

    override val setupProblem: String? get() = keyProblem?.code

    override fun createCheckout(request: CheckoutRequest): CheckoutSession {
        val params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setLocale(SessionCreateParams.Locale.NL)
            .setCustomerEmail(request.email)
            .setClientReferenceId(request.orderNumber)
            .putMetadata(ORDER_NUMBER, request.orderNumber)
            .setPaymentIntentData(
                SessionCreateParams.PaymentIntentData.builder()
                    .setDescription("Bestelling ${request.orderNumber}")
                    .putMetadata(ORDER_NUMBER, request.orderNumber)
                    .build(),
            )
            .setSuccessUrl(request.successUrl)
            .setCancelUrl(request.cancelUrl)
            .setExpiresAt(request.expiresAt.epochSecond)
            .addShippingOption(
                SessionCreateParams.ShippingOption.builder()
                    .setShippingRateData(
                        SessionCreateParams.ShippingOption.ShippingRateData.builder()
                            .setType(SessionCreateParams.ShippingOption.ShippingRateData.Type.FIXED_AMOUNT)
                            .setDisplayName(request.shippingLabel)
                            .setFixedAmount(
                                SessionCreateParams.ShippingOption.ShippingRateData.FixedAmount.builder()
                                    .setAmount(request.shippingCents)
                                    .setCurrency(CURRENCY)
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
        request.paymentMethods.forEach { params.addPaymentMethodType(paymentMethodType(it)) }
        request.lines.forEach { line ->
            val product = SessionCreateParams.LineItem.PriceData.ProductData.builder().setName(line.name)
            // Stripe shows the picture if it can reach it. Our own uploads are relative links, card images aren't.
            line.imageUrl?.takeIf { it.startsWith("https://") }?.let(product::addImage)
            params.addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(line.quantity.toLong())
                    .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(CURRENCY)
                            .setUnitAmount(line.unitAmountCents)
                            .setProductData(product.build())
                            .build(),
                    )
                    .build(),
            )
        }
        request.discount?.let { discount ->
            val coupon = coupon(request.orderNumber, discount)
            params.addDiscount(SessionCreateParams.Discount.builder().setCoupon(coupon).build())
        }
        val options = RequestOptions.builder().setIdempotencyKey("checkout-${request.orderNumber}").build()
        val session = call("create a checkout session") { stripe().checkout().sessions().create(params.build(), options) }
        return CheckoutSession(session.id, session.url, Instant.ofEpochSecond(session.expiresAt))
    }

    /**
     * A coupon for this one order: the discount the shop worked out, as a fixed amount, so Stripe's total is exactly
     * the order's. Stripe takes a coupon off the products, never off shipping, like the shop does.
     */
    private fun coupon(orderNumber: String, discount: PaymentDiscount): String {
        val params = CouponCreateParams.builder()
            .setAmountOff(discount.amountCents)
            .setCurrency(CURRENCY)
            .setDuration(CouponCreateParams.Duration.ONCE)
            .setMaxRedemptions(1L)
            .setName("Kortingscode ${discount.code}".take(COUPON_NAME_LENGTH))
            .putMetadata(ORDER_NUMBER, orderNumber)
            .build()
        val options = RequestOptions.builder().setIdempotencyKey("coupon-$orderNumber").build()
        return call("create a coupon") { stripe().coupons().create(params, options) }.id
    }

    override fun retrieve(sessionId: String): SessionState {
        val params = SessionRetrieveParams.builder().addExpand("payment_intent.latest_charge").build()
        val session = call("retrieve a checkout session") { stripe().checkout().sessions().retrieve(sessionId, params) }
        return state(session)
    }

    override fun expire(sessionId: String): ExpireResult {
        return try {
            stripe().checkout().sessions().expire(sessionId)
            ExpireResult.EXPIRED
        } catch (exception: InvalidRequestException) {
            if (exception.statusCode == NOT_FOUND || exception.code == "resource_missing") return ExpireResult.NOT_FOUND
            // Only an open session can be expired. Ask what it is instead.
            when (retrieve(sessionId).status) {
                SessionStatus.COMPLETE -> ExpireResult.COMPLETE
                SessionStatus.EXPIRED -> ExpireResult.EXPIRED
                SessionStatus.OPEN -> throw PaymentUnavailableException("Stripe refused to expire $sessionId", exception)
            }
        } catch (exception: StripeException) {
            throw PaymentUnavailableException("Could not expire checkout session $sessionId", exception)
        }
    }

    override fun refund(paymentIntentId: String, amountCents: Long, idempotencyKey: String, orderNumber: String): String {
        val params = RefundCreateParams.builder()
            .setPaymentIntent(paymentIntentId)
            .setAmount(amountCents)
            .putMetadata(ORDER_NUMBER, orderNumber)
            .build()
        val options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build()
        return call("refund $orderNumber") { stripe().refunds().create(params, options) }.id
    }

    // Premium (phase 11) ------------------------------------------------------------------------------------------

    override fun createCustomer(userId: Long, email: String): String {
        val params = CustomerCreateParams.builder().setEmail(email).putMetadata(USER_ID, userId.toString()).build()
        val options = RequestOptions.builder().setIdempotencyKey("customer-user-$userId").build()
        return call("create a customer") { stripe().customers().create(params, options) }.id
    }

    override fun createSubscriptionCheckout(request: SubscriptionCheckoutRequest): SubscriptionCheckout {
        val price = prices().getValue(request.plan)
        val params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setLocale(SessionCreateParams.Locale.NL)
            .setCustomer(request.customerId)
            .setClientReferenceId(request.userId.toString())
            .putMetadata(KIND, PREMIUM)
            .putMetadata(USER_ID, request.userId.toString())
            .addLineItem(SessionCreateParams.LineItem.builder().setPrice(price.id).setQuantity(1L).build())
            .setSubscriptionData(
                SessionCreateParams.SubscriptionData.builder()
                    .putMetadata(KIND, PREMIUM)
                    .putMetadata(USER_ID, request.userId.toString())
                    .build(),
            )
            .setCustomText(
                SessionCreateParams.CustomText.builder()
                    .setSubmit(SessionCreateParams.CustomText.Submit.builder().setMessage(request.consentText).build())
                    .build(),
            )
            .setSuccessUrl(request.successUrl)
            .setCancelUrl(request.cancelUrl)
            .build()
        val session = call("create a Premium checkout") { stripe().checkout().sessions().create(params) }
        return SubscriptionCheckout(session.id, session.url)
    }

    override fun subscriptionOfCheckout(sessionId: String): CheckoutSubscription? {
        val session = try {
            stripe().checkout().sessions().retrieve(sessionId)
        } catch (exception: InvalidRequestException) {
            return null
        } catch (exception: StripeException) {
            throw PaymentUnavailableException("Could not retrieve checkout session $sessionId", exception)
        }
        if (session.mode != "subscription" || session.metadata?.get(KIND) != PREMIUM) return null
        val subscription = session.subscription ?: return null
        return CheckoutSubscription(subscription, session.customer, session.metadata?.get(USER_ID)?.toLongOrNull())
    }

    override fun retrieveSubscription(subscriptionId: String): SubscriptionState {
        val subscription = call("retrieve subscription $subscriptionId") { stripe().subscriptions().retrieve(subscriptionId) }
        // Since API version 2025-03-31.basil the period lives on the subscription's items.
        val item = subscription.items?.data?.firstOrNull()
        return SubscriptionState(
            id = subscription.id,
            customerId = subscription.customer,
            userId = subscription.metadata?.get(USER_ID)?.toLongOrNull(),
            plan = planOf(item?.price),
            status = subscription.status,
            currentPeriodEnd = item?.currentPeriodEnd?.let(Instant::ofEpochSecond),
            cancelAtPeriodEnd = subscription.cancelAtPeriodEnd == true || subscription.cancelAt != null,
            endedAt = subscription.endedAt?.let(Instant::ofEpochSecond),
        )
    }

    override fun subscriptionOfInvoice(invoiceId: String): String? {
        val invoice = call("retrieve invoice $invoiceId") { stripe().invoices().retrieve(invoiceId) }
        return invoice.parent?.subscriptionDetails?.subscription
    }

    override fun refundedCharge(chargeId: String): RefundedCharge {
        val charge = call("retrieve charge $chargeId") { stripe().charges().retrieve(chargeId) }
        return RefundedCharge(charge.customer, charge.refunded == true)
    }

    override fun cancelSubscription(subscriptionId: String) {
        try {
            stripe().subscriptions().cancel(subscriptionId)
        } catch (exception: InvalidRequestException) {
            // Already cancelled: nothing left to stop.
            log.info("Subscription {} could not be cancelled: {}", subscriptionId, exception.message)
        } catch (exception: StripeException) {
            throw PaymentUnavailableException("Could not cancel subscription $subscriptionId", exception)
        }
    }

    override fun portalUrl(customerId: String, returnUrl: String): String {
        val params = com.stripe.param.billingportal.SessionCreateParams.builder()
            .setCustomer(customerId)
            .setReturnUrl(returnUrl)
            .setConfiguration(portal())
            .build()
        return call("open the customer portal") { stripe().billingPortal().sessions().create(params) }.url
    }

    /**
     * The Premium product and its two prices, found by lookup key or made the first time, so nobody has to set them
     * up in the dashboard. A changed price in `app.premium` makes a new price that takes over the lookup key; Stripe
     * keeps existing subscriptions on their old price until they're moved.
     */
    private fun prices(): Map<PremiumPlan, PremiumPrice> {
        premiumPrices?.let { return it }
        synchronized(this) {
            premiumPrices?.let { return it }
            val wanted = mapOf(PremiumPlan.MONTHLY to premium.monthlyPriceCents, PremiumPlan.YEARLY to premium.yearlyPriceCents)
            val listed = call("list the Premium prices") {
                stripe().prices().list(
                    PriceListParams.builder().setActive(true).addAllLookupKey(PremiumPlan.entries.map(::lookupKey)).build(),
                )
            }.data.associateBy { it.lookupKey }
            val product = listed.values.firstOrNull()?.product ?: createProduct()
            val prices = wanted.mapValues { (plan, cents) ->
                val existing = listed[lookupKey(plan)]
                if (existing != null && existing.unitAmount == cents) {
                    PremiumPrice(existing.id, existing.product)
                } else {
                    PremiumPrice(createPrice(product, plan, cents), product)
                }
            }
            premiumPrices = prices
            return prices
        }
    }

    private fun createProduct(): String {
        val params = ProductCreateParams.builder()
            .setName("Poketopia Premium")
            .setDescription("Onbeperkt kaarten scannen, de volledige waardegrafiek en de pre-grade check.")
            .putMetadata(KIND, PREMIUM)
            .build()
        val options = RequestOptions.builder().setIdempotencyKey("premium-product-v1").build()
        return call("create the Premium product") { stripe().products().create(params, options) }.id
    }

    private fun createPrice(product: String, plan: PremiumPlan, cents: Long): String {
        val params = PriceCreateParams.builder()
            .setProduct(product)
            .setCurrency(CURRENCY)
            .setUnitAmount(cents)
            .setTaxBehavior(PriceCreateParams.TaxBehavior.INCLUSIVE)
            .setRecurring(
                PriceCreateParams.Recurring.builder()
                    .setInterval(if (plan == PremiumPlan.MONTHLY) PriceCreateParams.Recurring.Interval.MONTH else PriceCreateParams.Recurring.Interval.YEAR)
                    .build(),
            )
            .setLookupKey(lookupKey(plan))
            .setTransferLookupKey(true)
            .setNickname(if (plan == PremiumPlan.MONTHLY) "Premium per maand" else "Premium per jaar")
            .build()
        val options = RequestOptions.builder().setIdempotencyKey("premium-price-${lookupKey(plan)}-$cents").build()
        return call("create the Premium price") { stripe().prices().create(params, options) }.id
    }

    /** The customer portal's settings: cancel at the end of the period, switch between the two prices, see invoices. */
    private fun portal(): String {
        portalConfiguration?.let { return it }
        synchronized(this) {
            portalConfiguration?.let { return it }
            val existing = call("list the portal settings") {
                stripe().billingPortal().configurations().list(ConfigurationListParams.builder().setActive(true).setLimit(100L).build())
            }.data.firstOrNull { it.metadata?.get(KIND) == PORTAL_VERSION }
            val prices = prices()
            val id = existing?.id ?: call("create the portal settings") {
                stripe().billingPortal().configurations().create(
                    ConfigurationCreateParams.builder()
                        .setBusinessProfile(
                            ConfigurationCreateParams.BusinessProfile.builder()
                                .setPrivacyPolicyUrl("$baseUrl/info/privacybeleid")
                                .setTermsOfServiceUrl("$baseUrl/info/algemene-voorwaarden")
                                .build(),
                        )
                        .setFeatures(
                            ConfigurationCreateParams.Features.builder()
                                .setInvoiceHistory(ConfigurationCreateParams.Features.InvoiceHistory.builder().setEnabled(true).build())
                                .setPaymentMethodUpdate(
                                    ConfigurationCreateParams.Features.PaymentMethodUpdate.builder().setEnabled(true).build(),
                                )
                                .setSubscriptionCancel(
                                    ConfigurationCreateParams.Features.SubscriptionCancel.builder()
                                        .setEnabled(true)
                                        .setMode(ConfigurationCreateParams.Features.SubscriptionCancel.Mode.AT_PERIOD_END)
                                        .build(),
                                )
                                .setSubscriptionUpdate(
                                    ConfigurationCreateParams.Features.SubscriptionUpdate.builder()
                                        .setEnabled(true)
                                        .addDefaultAllowedUpdate(
                                            ConfigurationCreateParams.Features.SubscriptionUpdate.DefaultAllowedUpdate.PRICE,
                                        )
                                        .addProduct(
                                            ConfigurationCreateParams.Features.SubscriptionUpdate.Product.builder()
                                                .setProduct(prices.getValue(PremiumPlan.MONTHLY).product)
                                                .addAllPrice(prices.values.map { it.id })
                                                .build(),
                                        )
                                        .setProrationBehavior(
                                            ConfigurationCreateParams.Features.SubscriptionUpdate.ProrationBehavior.CREATE_PRORATIONS,
                                        )
                                        .build(),
                                )
                                .build(),
                        )
                        .putMetadata(KIND, PORTAL_VERSION)
                        .build(),
                )
            }.id
            portalConfiguration = id
            return id
        }
    }

    private fun planOf(price: Price?): PremiumPlan? = when {
        price == null -> null
        price.lookupKey == lookupKey(PremiumPlan.MONTHLY) -> PremiumPlan.MONTHLY
        price.lookupKey == lookupKey(PremiumPlan.YEARLY) -> PremiumPlan.YEARLY
        price.recurring?.interval == "month" -> PremiumPlan.MONTHLY
        price.recurring?.interval == "year" -> PremiumPlan.YEARLY
        else -> null
    }

    private fun lookupKey(plan: PremiumPlan) = "poketopia_premium_${plan.name.lowercase()}"

    private data class PremiumPrice(val id: String, val product: String)

    private fun state(session: Session): SessionState {
        val paymentIntent = session.paymentIntentObject
        return SessionState(
            id = session.id,
            paid = session.paymentStatus == "paid",
            status = when (session.status) {
                "complete" -> SessionStatus.COMPLETE
                "expired" -> SessionStatus.EXPIRED
                else -> SessionStatus.OPEN
            },
            amountTotalCents = session.amountTotal,
            currency = session.currency,
            paymentIntentId = paymentIntent?.id ?: session.paymentIntent,
            paymentMethod = paymentIntent?.latestChargeObject?.paymentMethodDetails?.type,
        )
    }

    private fun stripe(): StripeClient =
        client ?: throw PaymentUnavailableException(keyProblem?.logMessage ?: "STRIPE_SECRET_KEY is not set")

    private fun <T> call(what: String, action: () -> T): T = try {
        action()
    } catch (exception: StripeException) {
        log.warn("Stripe could not {}: {} ({})", what, exception.message, exception.requestId)
        throw PaymentUnavailableException("Stripe could not $what", exception)
    }

    private fun paymentMethodType(method: String): SessionCreateParams.PaymentMethodType = when (method) {
        "ideal" -> SessionCreateParams.PaymentMethodType.IDEAL
        "bancontact" -> SessionCreateParams.PaymentMethodType.BANCONTACT
        "card" -> SessionCreateParams.PaymentMethodType.CARD
        else -> throw IllegalArgumentException("Unknown payment method $method")
    }

    private companion object {
        const val CURRENCY = "eur"
        const val ORDER_NUMBER = "order_number"
        const val KIND = "kind"
        const val PREMIUM = "premium"
        const val USER_ID = "user_id"
        const val PORTAL_VERSION = "premium-portal-v1"
        const val NOT_FOUND = 404
        const val COUPON_NAME_LENGTH = 40
    }
}
