package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.security.AdminOnly
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.http.HttpMethod
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import kotlin.test.assertTrue

/**
 * Calls every admin endpoint the application has, found from its own request mappings, as a visitor and as a
 * customer. A new admin endpoint is covered without changing this test.
 */
class AdminEndpointsIntegrationTest : IntegrationTest() {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private lateinit var handlerMapping: RequestMappingHandlerMapping

    private data class Endpoint(val method: HttpMethod, val pattern: String, val handler: HandlerMethod) {
        /** Any value will do for a path variable: the check happens before the handler reads it. */
        val path: String = pattern.replace(Regex("\\{[^}]+}"), "1")
        override fun toString() = "$method $pattern"
    }

    private fun HandlerMethod.isAdminOnly(): Boolean =
        hasMethodAnnotation(AdminOnly::class.java) || AnnotatedElementUtils.hasAnnotation(beanType, AdminOnly::class.java)

    private fun allEndpoints(): List<Endpoint> =
        handlerMapping.handlerMethods.flatMap { (info, handler) ->
            val methods = info.methodsCondition.methods.map { HttpMethod.valueOf(it.name) }.ifEmpty { listOf(HttpMethod.GET) }
            info.patternValues.flatMap { pattern -> methods.map { Endpoint(it, pattern, handler) } }
        }.sortedBy { it.toString() }

    private fun adminEndpoints(): List<Endpoint> =
        allEndpoints().filter { it.pattern.startsWith("/v1/admin") || it.handler.isAdminOnly() }

    @Test
    fun `every endpoint under the admin path is admin only`() {
        val unguarded = allEndpoints().filter { it.pattern.startsWith("/v1/admin") && !it.handler.isAdminOnly() }

        assertTrue(unguarded.isEmpty(), "Admin endpoints without @AdminOnly: $unguarded")
    }

    @Test
    fun `the test finds the admin endpoints`() {
        // A safety net for the test itself: if it stopped finding endpoints, the checks below would pass on nothing.
        assertTrue(adminEndpoints().size >= 50, "Found only ${adminEndpoints().size} admin endpoints")
    }

    @TestFactory
    fun `a visitor gets 401 on every admin endpoint`(): List<DynamicTest> =
        adminEndpoints().map { endpoint ->
            DynamicTest.dynamicTest(endpoint.toString()) {
                mockMvc.perform(request(endpoint.method, endpoint.path).with(fromRandomIp()))
                    .andExpect(status().isUnauthorized)
                    .andExpect(jsonPath("$.error").value("not_authenticated"))
            }
        }

    @TestFactory
    fun `a customer gets 403 on every admin endpoint`(): List<DynamicTest> {
        val customer: Cookie by lazy { loggedInUser(Role.CUSTOMER) }
        return adminEndpoints().map { endpoint ->
            DynamicTest.dynamicTest(endpoint.toString()) {
                mockMvc.perform(request(endpoint.method, endpoint.path).cookie(customer).with(fromRandomIp()))
                    .andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.error").value("forbidden"))
            }
        }
    }
}
