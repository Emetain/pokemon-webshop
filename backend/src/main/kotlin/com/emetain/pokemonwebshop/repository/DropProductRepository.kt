package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.DropProduct
import com.emetain.pokemonwebshop.domain.DropStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface DropProductRepository : JpaRepository<DropProduct, Long> {

    fun findByDropIdAndProductId(dropId: Long, productId: Long): DropProduct?

    /** The upcoming or live drop a product is in, for its product page. */
    @Query(
        "select dp from DropProduct dp join fetch dp.drop d where dp.product.id = :productId and d.status in :statuses " +
            "order by d.startsAt",
    )
    fun findInDrops(productId: Long, statuses: Collection<DropStatus>): List<DropProduct>
}
