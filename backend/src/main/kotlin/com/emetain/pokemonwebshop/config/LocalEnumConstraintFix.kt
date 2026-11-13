package com.emetain.pokemonwebshop.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Local Postgres databases only. Hibernate gives every enum column a CHECK with the values the enum had when it made
 * the table, such as `game IN ('POKEMON', 'LORCANA', 'MTG', 'ONE_PIECE')`, and `ddl-auto: update` never changes it
 * again. A new value, such as Riftbound in phase 8, then fails to save. This drops those checks at startup. The
 * Flyway migrations on the server never had them, so `prod` doesn't need this.
 */
@Component
@Profile("postgres", "docker")
class LocalEnumConstraintFix(private val jdbc: JdbcTemplate) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun dropEnumChecks() {
        val checks = jdbc.query(
            """
            SELECT c.conrelid::regclass::text AS table_name, c.conname AS constraint_name
            FROM pg_constraint c
            JOIN pg_namespace n ON n.oid = c.connamespace
            WHERE c.contype = 'c'
              AND n.nspname = current_schema()
              AND pg_get_constraintdef(c.oid) ~ '= ANY \(+ARRAY\['
            """.trimIndent(),
        ) { row, _ -> row.getString("table_name") to row.getString("constraint_name") }
        // Postgres writes them as `= ANY ((ARRAY[...` or `= ANY (ARRAY[(...`, depending on how they were made.
        for ((table, constraint) in checks) {
            jdbc.execute("ALTER TABLE $table DROP CONSTRAINT IF EXISTS \"${constraint.replace("\"", "")}\"")
        }
        if (checks.isNotEmpty()) log.info("Dropped {} enum checks that Hibernate made for older enum values", checks.size)
    }
}
