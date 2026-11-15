package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.config.ShopAccess
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.test.context.ActiveProfiles
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertContains
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
        // Nothing may reach the card APIs from a test.
        "app.catalog.import-pokedex-on-start=false",
        "app.catalog.sync-cron=-",
        "app.collection.snapshot-cron=-",
        "app.media.directory=build/test-media",
        // As the server's .env would set them for a real mail provider.
        "MAIL_HOST=smtp.example.com",
        "MAIL_PORT=587",
        "MAIL_USERNAME=shop@example.com",
        "MAIL_PASSWORD=geheim",
        "MAIL_SMTP_AUTH=true",
        "MAIL_SMTP_STARTTLS=true",
    ],
)
@ActiveProfiles("prod")
class ProdProfileIntegrationTest {

    private val http = HttpClient.newHttpClient()

    @Autowired
    private lateinit var mailSender: JavaMailSender

    @Autowired
    private lateinit var appProperties: AppProperties

    @LocalServerPort
    private var port = 0

    /** A full URL, so the test sees the public paths with the /api context path exactly as a browser does. */
    private fun get(path: String): HttpResponse<String> =
        http.send(HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).build(), HttpResponse.BodyHandlers.ofString())

    private fun statusOf(path: String): HttpStatus = HttpStatus.valueOf(get(path).statusCode())

    @Test
    fun `the health endpoint answers without details`() {
        val response = get("/api/actuator/health")

        assertEquals(HttpStatus.OK, HttpStatus.valueOf(response.statusCode()))
        assertEquals("""{"status":"UP"}""", response.body())
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

    @Test
    fun `the mail settings reach the mail sender, with timeouts`() {
        val sender = mailSender as JavaMailSenderImpl
        val properties = sender.javaMailProperties

        assertEquals("smtp.example.com", sender.host)
        assertEquals(587, sender.port)
        assertEquals("shop@example.com", sender.username)
        assertEquals("geheim", sender.password)
        assertEquals("true", properties.getProperty("mail.smtp.auth"))
        assertEquals("true", properties.getProperty("mail.smtp.starttls.enable"))
        assertEquals("true", properties.getProperty("mail.smtp.starttls.required"))
        assertEquals("10000", properties.getProperty("mail.smtp.connectiontimeout"))
        assertEquals("10000", properties.getProperty("mail.smtp.timeout"))
        assertEquals("10000", properties.getProperty("mail.smtp.writetimeout"))
    }

    @Test
    fun `without SHOP_ACCESS the shop is the closed preview, with the default checkout caps`() {
        assertEquals(ShopAccess.PREVIEW, appProperties.shopAccess)
        assertEquals(2, appProperties.checkout.maxOpenPerCustomer)
        assertEquals(5, appProperties.checkout.maxOpenPerIp)
    }

    @Test
    fun `the business details for the footer are public and empty until they are set`() {
        val response = get("/api/v1/shop/company")

        assertEquals(200, response.statusCode())
        assertEquals("""{"legalName":null,"address":null,"kvkNumber":null,"vatNumber":null}""", response.body())
    }

    @Test
    fun `a chunked form body above tomcat's own limit answers 413 in the error format`() {
        // Without a Content-Length, so the size filter can't refuse it up front, and as a form, which Tomcat reads
        // itself instead of through that filter. Its limit is 2 MB.
        val body = "x=".toByteArray() + ByteArray(3 * 1024 * 1024) { 'a'.code.toByte() }
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port/api/v1/auth/login"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofInputStream { ByteArrayInputStream(body) })
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(413, response.statusCode())
        assertContains(response.body(), "\"error\":\"request_too_large\"")
        assertContains(response.body(), "Dit verzoek is te groot.")
    }
}
