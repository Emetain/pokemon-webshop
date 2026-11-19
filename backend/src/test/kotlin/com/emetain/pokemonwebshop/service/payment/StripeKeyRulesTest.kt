package com.emetain.pokemonwebshop.service.payment

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.config.ShopAccess
import com.emetain.pokemonwebshop.service.testProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StripeKeyRulesTest {

    @Test
    fun `no key is no problem in either mode, online payment is just off`() {
        assertNull(StripeKeyRules.problem("", ShopAccess.PREVIEW))
        assertNull(StripeKeyRules.problem("  ", ShopAccess.OPEN))
    }

    @Test
    fun `the closed preview takes test keys and refuses live keys`() {
        assertNull(StripeKeyRules.problem("sk_test_abc", ShopAccess.PREVIEW))
        assertNull(StripeKeyRules.problem("rk_test_abc", ShopAccess.PREVIEW))
        assertEquals(StripeKeyRules.Problem.LIVE_KEY_IN_PREVIEW, StripeKeyRules.problem("sk_live_abc", ShopAccess.PREVIEW))
        assertEquals(StripeKeyRules.Problem.LIVE_KEY_IN_PREVIEW, StripeKeyRules.problem("rk_live_abc", ShopAccess.PREVIEW))
    }

    @Test
    fun `online without the login, before opening, also only takes test keys`() {
        assertNull(StripeKeyRules.problem("sk_test_abc", ShopAccess.UNLISTED))
        assertEquals(StripeKeyRules.Problem.LIVE_KEY_IN_PREVIEW, StripeKeyRules.problem("sk_live_abc", ShopAccess.UNLISTED))
    }

    @Test
    fun `the open shop takes live keys and refuses anything else`() {
        assertNull(StripeKeyRules.problem("sk_live_abc", ShopAccess.OPEN))
        assertNull(StripeKeyRules.problem(" rk_live_abc ", ShopAccess.OPEN))
        assertEquals(StripeKeyRules.Problem.TEST_KEY_IN_OPEN_SHOP, StripeKeyRules.problem("sk_test_abc", ShopAccess.OPEN))
        assertEquals(StripeKeyRules.Problem.TEST_KEY_IN_OPEN_SHOP, StripeKeyRules.problem("something_else", ShopAccess.OPEN))
    }

    @Test
    fun `the gateway keeps online payment off for a key that doesn't fit, and says why`() {
        fun gateway(key: String, access: ShopAccess) = StripePaymentGateway(
            testProperties().copy(payments = AppProperties.Payments(AppProperties.Stripe(secretKey = key)), shopAccess = access),
        )

        val livePreview = gateway("sk_live_unit", ShopAccess.PREVIEW)
        assertFalse(livePreview.configured)
        assertEquals("live_key_in_preview", livePreview.setupProblem)

        val testOpen = gateway("sk_test_unit", ShopAccess.OPEN)
        assertFalse(testOpen.configured)
        assertEquals("test_key_in_open_shop", testOpen.setupProblem)

        assertTrue(gateway("sk_test_unit", ShopAccess.PREVIEW).configured)
        assertTrue(gateway("sk_live_unit", ShopAccess.OPEN).configured)
        assertNull(gateway("sk_live_unit", ShopAccess.OPEN).setupProblem)
        assertFalse(gateway("", ShopAccess.OPEN).configured)
        assertNull(gateway("", ShopAccess.OPEN).setupProblem)
    }
}
