package com.emetain.pokemonwebshop.exception

import com.emetain.pokemonwebshop.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/** Turns every failure into `{ "error": "<code>", "message": "<Dutch text>" }`. */
@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(exception: ApiException): ResponseEntity<ErrorResponse> {
        val response = ResponseEntity.status(exception.status)
        exception.retryAfterSeconds?.let { response.header(HttpHeaders.RETRY_AFTER, it.toString()) }
        return response.body(ErrorResponse(exception.code, exception.message))
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(exception: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
        log.warn("Data integrity violation: {}", exception.mostSpecificCause.message)
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse("conflict", "Dit kon niet worden opgeslagen omdat het conflicteert met bestaande gegevens."))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception", exception)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("internal_error", "Er ging iets mis aan onze kant. Probeer het later opnieuw."))
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val message = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "Controleer de ingevulde gegevens."
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("validation_failed", message))
    }

    override fun handleMaxUploadSizeExceededException(
        ex: MaxUploadSizeExceededException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? = ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(ErrorResponse("file_too_large", "Dit bestand is te groot. Kies een afbeelding tot 5 MB."))

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val error = when (statusCode.value()) {
            400 -> ErrorResponse("bad_request", "Dit verzoek klopt niet. Controleer de ingevulde gegevens.")
            404 -> ErrorResponse("not_found", "We konden dit niet vinden.")
            405 -> ErrorResponse("method_not_allowed", "Deze actie is hier niet mogelijk.")
            415 -> ErrorResponse("unsupported_media_type", "Dit bestandstype of formaat wordt niet ondersteund.")
            else -> ErrorResponse("request_failed", "Dit verzoek kon niet worden verwerkt.")
        }
        return ResponseEntity.status(statusCode).headers(headers).body(error)
    }
}
