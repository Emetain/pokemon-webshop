package com.emetain.pokemonwebshop.integration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertTrue

/** Runs the migrations like the prod profile does. The context fails to start when they don't match the entities. */
@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:flyway;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
    ],
)
@ActiveProfiles("test")
class FlywayMigrationTest {

    @Autowired
    private lateinit var flyway: Flyway

    @Test
    fun `migrations run and hibernate validation passes`() {
        assertTrue(flyway.info().applied().isNotEmpty())
    }
}
