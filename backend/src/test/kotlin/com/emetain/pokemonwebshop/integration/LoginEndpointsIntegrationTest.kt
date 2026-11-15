package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.security.AdminOnly
import com.emetain.pokemonwebshop.security.RequiresLogin
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import kotlin.test.assertTrue

/**
 * The customer side of [AdminEndpointsIntegrationTest]: every endpoint that needs a login, found from the request
 * mappings, refuses a visitor. Whose data an endpoint shows is tested per feature ("someone else's ...").
 */
class LoginEndpointsIntegrationTest : IntegrationTest() {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    /** Paths that only make sense for a logged-in customer. */
    private val accountPaths = listOf("/v1/account", "/v1/collection", "/v1/wishlist", "/v1/binder/selection")

    private fun loginEndpoints(): List<Endpoint> =
        handlerMapping.endpoints().filter { it.isAnnotated(RequiresLogin::class.java) && !it.isAnnotated(AdminOnly::class.java) }

    @Test
    fun `every endpoint under the account paths needs a login`() {
        val unguarded = handlerMapping.endpoints().filter { endpoint ->
            accountPaths.any { endpoint.pattern.startsWith(it) } &&
                !endpoint.isAnnotated(RequiresLogin::class.java) && !endpoint.isAnnotated(AdminOnly::class.java)
        }

        assertTrue(unguarded.isEmpty(), "Account endpoints without @RequiresLogin: $unguarded")
    }

    @Test
    fun `the test finds the login endpoints`() {
        // A safety net for the test itself: if it stopped finding endpoints, the check below would pass on nothing.
        assertTrue(loginEndpoints().size >= 25, "Found only ${loginEndpoints().size} login endpoints")
    }

    @TestFactory
    fun `a visitor gets 401 on every endpoint that needs a login`(): List<DynamicTest> =
        loginEndpoints().map { endpoint ->
            DynamicTest.dynamicTest(endpoint.toString()) {
                mockMvc.perform(request(endpoint.method, endpoint.path).with(fromRandomIp()))
                    .andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.error").value("not_authenticated"))
            }
        }
}
