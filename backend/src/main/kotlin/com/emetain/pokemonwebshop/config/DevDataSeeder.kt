package com.emetain.pokemonwebshop.config

import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.repository.UserRepository
import com.emetain.pokemonwebshop.service.UserService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/** Creates demo data for local development. Never active outside the dev profile. */
@Component
@Profile("dev")
class DevDataSeeder(
    private val userService: UserService,
    private val userRepository: UserRepository,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        seedUser(ADMIN_EMAIL, ADMIN_PASSWORD, "Demo Beheerder", Role.ADMIN)
        seedUser(CUSTOMER_EMAIL, CUSTOMER_PASSWORD, "Demo Klant", Role.CUSTOMER)

        log.info("Demo admin login: {} / {}", ADMIN_EMAIL, ADMIN_PASSWORD)
        log.info("Demo customer login: {} / {}", CUSTOMER_EMAIL, CUSTOMER_PASSWORD)
    }

    private fun seedUser(email: String, password: String, displayName: String, role: Role) {
        if (!userRepository.existsByEmail(email)) {
            userService.register(email, password, displayName, role)
        }
    }

    private companion object {
        const val ADMIN_EMAIL = "admin@example.com"
        const val ADMIN_PASSWORD = "admin12345"
        const val CUSTOMER_EMAIL = "klant@example.com"
        const val CUSTOMER_PASSWORD = "klant12345"
    }
}
