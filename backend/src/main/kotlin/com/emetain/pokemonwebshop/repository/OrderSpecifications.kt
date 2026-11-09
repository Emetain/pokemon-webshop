package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.Order
import com.emetain.pokemonwebshop.domain.OrderStatus
import org.springframework.data.jpa.domain.Specification
import java.time.Instant
import java.util.Locale

object OrderSpecifications {

    /** Matches the order number, the email or the name. */
    fun search(term: String?): Specification<Order>? {
        val needle = term?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return null
        val pattern = "%${needle.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
        return Specification { root, _, builder ->
            builder.or(
                builder.like(builder.lower(root.get("number")), pattern, '\\'),
                builder.like(builder.lower(root.get("email")), pattern, '\\'),
                builder.like(builder.lower(root.get("customerName")), pattern, '\\'),
            )
        }
    }

    fun withStatus(status: OrderStatus) = Specification<Order> { root, _, builder ->
        builder.equal(root.get<OrderStatus>("status"), status)
    }

    /** Checkouts that were given up: cancelled without ever being paid. */
    fun abandoned() = Specification<Order> { root, _, builder ->
        builder.and(
            builder.isNull(root.get<Instant>("paidAt")),
            builder.equal(root.get<OrderStatus>("status"), OrderStatus.CANCELLED),
        )
    }

    fun notAbandoned(): Specification<Order> = Specification.not(abandoned())
}
