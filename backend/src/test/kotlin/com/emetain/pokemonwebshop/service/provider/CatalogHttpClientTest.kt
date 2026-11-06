package com.emetain.pokemonwebshop.service.provider

import com.emetain.pokemonwebshop.service.testCatalogProperties
import com.emetain.pokemonwebshop.service.testProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogHttpClientTest {

    private lateinit var server: HttpServer
    private val calls = AtomicInteger()
    private val headers = CopyOnWriteArrayList<Map<String, String?>>()

    private val rateLimitDelay = Duration.ofMillis(5)
    private val client = CatalogHttpClient(
        testProperties().copy(catalog = testCatalogProperties().copy(rateLimitDelay = rateLimitDelay)),
        ObjectMapper(),
    )

    private val url get() = "http://localhost:${server.address.port}/data"
    private val noPause = Duration.ZERO

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    private fun respond(handler: (call: Int, exchange: HttpExchange) -> Unit) {
        server.createContext("/data") { exchange ->
            headers.add(mapOf("User-Agent" to exchange.requestHeaders.getFirst("User-Agent"), "Accept" to exchange.requestHeaders.getFirst("Accept")))
            exchange.use { handler(calls.incrementAndGet(), it) }
        }
    }

    private fun HttpExchange.send(status: Int, body: ByteArray = ByteArray(0)) {
        sendResponseHeaders(status, if (body.isEmpty()) -1 else body.size.toLong())
        if (body.isNotEmpty()) responseBody.write(body)
    }

    @Test
    fun `reads json and identifies itself on every request`() {
        respond { _, exchange -> exchange.send(200, """{ "name": "Pikachu" }""".toByteArray()) }

        val json = client.getJson(url, noPause)

        assertEquals("Pikachu", json.path("name").asText())
        assertEquals("UithoornTCG/test", headers.single()["User-Agent"])
        assertTrue(headers.single()["Accept"]!!.contains("application/json"))
    }

    @Test
    fun `waits for Retry-After on a 429 and then tries again`() {
        respond { call, exchange ->
            if (call == 1) {
                exchange.responseHeaders.add("Retry-After", "1")
                exchange.send(429)
            } else {
                exchange.send(200, "{}".toByteArray())
            }
        }

        val started = System.nanoTime()
        client.getJson(url, noPause)

        assertEquals(2, calls.get())
        assertTrue(Duration.ofNanos(System.nanoTime() - started) >= Duration.ofSeconds(1))
    }

    @Test
    fun `waits the configured time on a 429 without Retry-After`() {
        respond { call, exchange -> if (call == 1) exchange.send(429) else exchange.send(200, "{}".toByteArray()) }

        client.getJson(url, noPause)

        assertEquals(2, calls.get())
    }

    @Test
    fun `tries again after a server error`() {
        respond { call, exchange -> if (call < 4) exchange.send(503) else exchange.send(200, "{}".toByteArray()) }

        client.getJson(url, noPause)

        assertEquals(4, calls.get())
    }

    @Test
    fun `gives up after 4 attempts`() {
        respond { _, exchange -> exchange.send(500) }

        val exception = assertThrows<CatalogHttpException> { client.getJson(url, noPause) }

        assertEquals(4, calls.get())
        assertTrue(exception.message!!.contains("HTTP 500"))
    }

    @Test
    fun `a 404 fails at once`() {
        respond { _, exchange -> exchange.send(404) }

        val exception = assertThrows<CatalogHttpException> { client.getJson(url, noPause) }

        assertEquals(1, calls.get())
        assertTrue(exception.message!!.contains("HTTP 404"))
    }

    @Test
    fun `tries again when reading the answer fails`() {
        respond { call, exchange -> exchange.send(200, (if (call == 1) "{ broken" else "{}").toByteArray()) }

        client.getJson(url, noPause)

        assertEquals(2, calls.get())
    }

    @Test
    fun `streams a gzip file into the reader`() {
        val compressed = ByteArrayOutputStream().also { bytes ->
            GZIPOutputStream(bytes).use { it.write("first\nsecond\n".toByteArray()) }
        }.toByteArray()
        respond { _, exchange -> exchange.send(200, compressed) }

        val lines = client.get(url, noPause) { body -> GZIPInputStream(body).bufferedReader().readLines() }

        assertEquals(listOf("first", "second"), lines)
    }

    @Test
    fun `tries again when the gzip stream is cut off`() {
        respond { call, exchange ->
            exchange.send(200, if (call == 1) "not gzip at all".toByteArray() else gzip("ok"))
        }

        val text = client.get(url, noPause) { body -> GZIPInputStream(body).bufferedReader().readText() }

        assertEquals("ok", text)
    }

    @Test
    fun `an unreachable host counts as a failure`() {
        val closedPort = HttpServer.create(InetSocketAddress("localhost", 0), 0).run {
            address.port.also { stop(0) }
        }

        assertThrows<CatalogHttpException> { client.getJson("http://localhost:$closedPort/data", noPause) }
    }

    @Test
    fun `pauses before a request`() {
        respond { _, exchange -> exchange.send(200, "{}".toByteArray()) }

        val started = System.nanoTime()
        client.getJson(url, Duration.ofMillis(50))

        assertTrue(Duration.ofNanos(System.nanoTime() - started) >= Duration.ofMillis(50))
    }

    private fun gzip(text: String): ByteArray =
        ByteArrayOutputStream().also { bytes -> GZIPOutputStream(bytes).use { it.write(text.toByteArray()) } }.toByteArray()

    @Test
    fun `io failures are wrapped`() {
        // The checked exception of the reader must not leak out of the client.
        respond { _, exchange -> exchange.send(200, "{}".toByteArray()) }

        assertThrows<CatalogHttpException> { client.get<Unit>(url, noPause) { throw IOException("disk full") } }
    }
}
