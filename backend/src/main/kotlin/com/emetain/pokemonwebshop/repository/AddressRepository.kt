package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.Address
import org.springframework.data.jpa.repository.JpaRepository

interface AddressRepository : JpaRepository<Address, Long> {
    fun findAllByUserIdOrderByCreatedAtAsc(userId: Long): List<Address>

    fun findByIdAndUserId(id: Long, userId: Long): Address?
}
