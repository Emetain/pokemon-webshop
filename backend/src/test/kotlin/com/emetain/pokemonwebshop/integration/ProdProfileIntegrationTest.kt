package com.emetain.pokemonwebshop.integration

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

/**
 * Starts the app on the prod profile with a real web server, like the preview server runs it: Flyway, schema
 * validation, no API documentation and no H2 console, and the health endpoint the deploy waits for.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.datasource.url=jdbc:h2:mem:prod;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        // Nothing may reach the card APIs from a test.
        "app.catalog.import-pokedex-on-start=false",
        "app.catalog.sync-cron=-",
        "app.collection.snapshot-cron=-",
        "app.media.directory=build/test-media",
    ],
)
@ActiveProfiles("prod")
class ProdProfileIntegrationTest {

    @Autowired
    private lateinit var http: TestRestTemplate

    @LocalServerPort
    private var port = 0

    /** A full URL, so the test sees the public paths with the /api context path exactly as a browser does. */
    private fun get(path: String) = http.getForEntity("http://localhost:$port$path", String::class.java)

    private fun statusOf(path: String): HttpStatus = HttpStatus.valueOf(get(path).statusCode.value())

    @Test
    fun `the health endpoint answers without details`() {
        val response = get("/api/actuator/health")

        assertEquals(HttpStatus.OK, HttpStatus.valueOf(response.statusCode.value()))
        assertEquals("""{"status":"UP"}""", response.body)
    }

    @Test
    fun `the api answers on its public path`() {
        // Makes sure the 404s below come from switched-off features, not from a wrong path.
        assertEquals(HttpStatus.OK, statusOf("/api/v1/catalog/pokemon/sets"))
    }

    @Test
    fun `no other actuator endpoint is reachable`() {
        assertEquals(HttpStatus.NOT_FOUND, statusOf("/api/actuator"))
        assertEquals(HttpStatus.NOT_FOUND, statusOf("/api/actuator/env"))
        assertEquals(HttpStatus.NOT_FOUND, statusOf("/api/actuator/info"))
    }

    @Test
    fun `swagger and the api docs are off`() {
        assertEquals(HttpStatus.NOT_FOUND, statusOf("/api/v3/api-docs"))
        assertEquals(HttpStatus.NOT_FOUND, statusOf("/api/swagger-ui.html"))
        assertEquals(HttpStatus.NOT_FOUND, statusOf("/api/swagger-ui/index.html"))
    }

    @Test
    fun `the h2 console is off`() {
        assertEquals(HttpStatus.NOT_FOUND, statusOf("/api/h2-console"))
        assertEquals(HttpStatus.NOT_FOUND, statusOf("/api/h2-console/"))
    }
}
