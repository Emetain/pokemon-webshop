package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.service.UserService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.internet.MimeMessage
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Keeps mails in memory instead of sending them. */
class CapturingMailSender : JavaMailSenderImpl() {
    val sent = CopyOnWriteArrayList<MimeMessage>()

    override fun doSend(mimeMessages: Array<out MimeMessage>, originalMessages: Array<out Any>?) {
        mimeMessages.forEach { it.saveChanges() }
        sent.addAll(mimeMessages)
    }
}

@TestConfiguration
class TestMailConfig {
    @Bean
    @Primary
    fun capturingMailSender() = CapturingMailSender()
}

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestMailConfig::class)
abstract class IntegrationTest {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    @Autowired
    protected lateinit var userService: UserService

    @Autowired
    protected lateinit var mailSender: CapturingMailSender

    protected val password = "geheim123"

    protected fun uniqueEmail() = "${UUID.randomUUID()}@example.com"

    /** Gives every test its own IP address, so tests don't use up each other's rate limit. */
    protected fun fromRandomIp(): RequestPostProcessor {
        val ip = "10.${(0..255).random()}.${(0..255).random()}.${(0..255).random()}"
        return RequestPostProcessor { request -> request.apply { remoteAddr = ip } }
    }

    protected fun MockHttpServletRequestBuilder.json(body: Any): MockHttpServletRequestBuilder =
        contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body))

    protected fun ResultActions.sessionCookie(): Cookie =
        requireNotNull(andReturn().response.getCookie("session")) { "No session cookie in the response" }

    protected fun login(email: String, password: String = this.password): ResultActions =
        mockMvc.perform(post("/v1/auth/login").with(fromRandomIp()).json(mapOf("email" to email, "password" to password)))

    protected fun loggedInUser(role: Role = Role.CUSTOMER, email: String = uniqueEmail()): Cookie {
        userService.register(email, password, "Testgebruiker", role)
        return login(email).sessionCookie()
    }

    protected fun plainText(part: Part): String? = when {
        part.isMimeType("text/plain") -> part.content as String
        part.isMimeType("multipart/*") -> {
            val multipart = part.content as Multipart
            (0 until multipart.count).firstNotNullOfOrNull { plainText(multipart.getBodyPart(it)) }
        }
        else -> null
    }
}
