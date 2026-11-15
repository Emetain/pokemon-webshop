package com.emetain.pokemonwebshop.config

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * The dev profile has an in-memory database, the H2 console and demo accounts with known passwords. It is the default
 * when no profile is set, so a jar started on a server without SPRING_PROFILES_ACTIVE would run it. A shop address
 * on https means a real server: refuse to start there.
 */
@Component
@Profile("dev")
class DevProfileGuard(properties: AppProperties) {

    init {
        check(!isPublic(properties.baseUrl)) {
            "The dev profile won't start with APP_BASE_URL ${properties.baseUrl}: it has demo accounts and an open " +
                "database console. Set SPRING_PROFILES_ACTIVE=prod on a server."
        }
    }

    companion object {
        fun isPublic(baseUrl: String): Boolean = baseUrl.trim().startsWith("https://", ignoreCase = true)
    }
}
