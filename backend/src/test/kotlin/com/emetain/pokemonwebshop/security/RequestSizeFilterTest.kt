package com.emetain.pokemonwebshop.security

import com.emetain.pokemonwebshop.exception.GlobalExceptionHandler
import com.emetain.pokemonwebshop.service.testProperties
import tools.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.util.unit.DataSize
import org.springframework.web.context.request.ServletWebRequest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestSizeFilterTest {

    private val properties = testProperties().copy(http = com.emetain.pokemonwebshop.config.AppProperties.Http(DataSize.ofBytes(100)))
    private val filter = RequestSizeFilter(properties, ObjectMapper())

    /** A body sent in chunks: the size is only known once it has been read. */
    private fun chunkedRequest(bytes: Int) = object : MockHttpServletRequest("POST", "/v1/auth/login") {
        override fun getContentLength() = -1
        override fun getContentLengthLong() = -1L
    }.apply {
        contentType = "application/json"
        setContent(ByteArray(bytes) { 'a'.code.toByte() })
    }

    private fun readingChain(read: (ByteArray) -> Unit = {}) = FilterChain { request, _ ->
        read(request.inputStream.readAllBytes())
    }

    @Test
    fun `a body without a length stops at the limit while it is read`() {
        assertThrows<RequestTooLargeException> {
            filter.doFilter(chunkedRequest(101), MockHttpServletResponse(), readingChain())
        }
    }

    @Test
    fun `a body without a length up to the limit is read completely`() {
        var read = 0
        filter.doFilter(chunkedRequest(100), MockHttpServletResponse(), readingChain { read = it.size })

        assertEquals(100, read)
    }

    @Test
    fun `a body with a length above the limit never reaches the application`() {
        val request = MockHttpServletRequest("POST", "/v1/auth/login").apply {
            contentType = "application/json"
            setContent(ByteArray(101))
        }
        val response = MockHttpServletResponse()
        var reached = false

        filter.doFilter(request, response) { _, _ -> reached = true }

        assertEquals(413, response.status)
        assertTrue(response.contentAsString.contains("request_too_large"))
        assertTrue(!reached)
    }

    @Test
    fun `multipart uploads are left to their own limit`() {
        val request = MockHttpServletRequest("POST", "/v1/admin/products/1/images").apply {
            contentType = "multipart/form-data; boundary=x"
            setContent(ByteArray(1000))
        }
        var read = 0

        filter.doFilter(request, MockHttpServletResponse(), readingChain { read = it.size })

        assertEquals(1000, read)
    }

    @Test
    fun `the exception handler answers 413 when the limit is hit while reading`() {
        val exception = HttpMessageNotReadableException(
            "I/O error while reading input message",
            RequestTooLargeException(100),
            MockHttpInputMessage(ByteArray(0)),
        )

        val response = GlobalExceptionHandler().handleException(exception, ServletWebRequest(MockHttpServletRequest()))

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response?.statusCode)
        assertEquals(RequestSizeFilter.tooLarge(), response?.body)
    }
}
