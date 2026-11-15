package com.emetain.pokemonwebshop.security

import com.emetain.pokemonwebshop.config.AppProperties
import com.emetain.pokemonwebshop.dto.ErrorResponse
import tools.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset

/** Thrown while reading a body that turns out larger than allowed. The exception handler answers 413. */
class RequestTooLargeException(maxBytes: Long) : IOException("Request body larger than $maxBytes bytes")

/**
 * Refuses a request body above `app.http.max-body-size` with 413, so nobody can make the backend read megabytes of
 * JSON without logging in. A body with a Content-Length above the limit is refused before anything is read; a body
 * without one (chunked) is counted while it's read and stops at the limit. Uploads are multipart and have their own
 * limit in `spring.servlet.multipart`. Caddy has the same limits; this guards requests that reach the backend
 * another way.
 */
@Component
class RequestSizeFilter(
    properties: AppProperties,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    private val maxBytes = properties.http.maxBodySize.toBytes()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.contentType?.startsWith("multipart/", ignoreCase = true) == true

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        if (request.contentLengthLong > maxBytes) {
            response.status = HttpStatus.PAYLOAD_TOO_LARGE.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            objectMapper.writeValue(response.outputStream, tooLarge())
            return
        }
        filterChain.doFilter(LimitedBodyRequest(request, maxBytes), response)
    }

    companion object {
        fun tooLarge() = ErrorResponse("request_too_large", "Dit verzoek is te groot.")
    }
}

private class LimitedBodyRequest(request: HttpServletRequest, private val maxBytes: Long) : HttpServletRequestWrapper(request) {

    private val limited: ServletInputStream by lazy { LimitedInputStream(super.getInputStream(), maxBytes) }

    override fun getInputStream(): ServletInputStream = limited

    override fun getReader(): BufferedReader {
        val charset = characterEncoding?.let(Charset::forName) ?: Charsets.UTF_8
        return BufferedReader(InputStreamReader(inputStream, charset))
    }
}

private class LimitedInputStream(private val delegate: ServletInputStream, private val maxBytes: Long) : ServletInputStream() {

    private var count = 0L

    override fun read(): Int {
        val byte = delegate.read()
        if (byte >= 0) counted(1)
        return byte
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = delegate.read(buffer, offset, length)
        if (read > 0) counted(read)
        return read
    }

    private fun counted(bytes: Int) {
        count += bytes
        if (count > maxBytes) throw RequestTooLargeException(maxBytes)
    }

    override fun isFinished(): Boolean = delegate.isFinished

    override fun isReady(): Boolean = delegate.isReady

    override fun setReadListener(listener: ReadListener) = delegate.setReadListener(listener)

    override fun close() = delegate.close()
}
