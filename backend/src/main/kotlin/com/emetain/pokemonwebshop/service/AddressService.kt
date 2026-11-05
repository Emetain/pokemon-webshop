package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Address
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AddressRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.AddressRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class AddressService(
    private val addressRepository: AddressRepository,
    private val userService: UserService,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun list(userId: Long): List<Address> = addressRepository.findAllByUserIdOrderByCreatedAtAsc(userId)

    @Transactional
    fun create(user: User, request: AddressRequest): Address {
        userService.requireSupportedCountry(request.country)
        return addressRepository.save(
            Address(
                user = user,
                name = request.name.trim(),
                street = request.street.trim(),
                houseNumber = request.houseNumber.trim(),
                postcode = normalizePostcode(request.postcode, request.country),
                city = request.city.trim(),
                country = request.country,
                createdAt = clock.instant(),
            ),
        )
    }

    @Transactional
    fun update(userId: Long, addressId: Long, request: AddressRequest): Address {
        userService.requireSupportedCountry(request.country)
        val address = findOwned(userId, addressId)
        address.name = request.name.trim()
        address.street = request.street.trim()
        address.houseNumber = request.houseNumber.trim()
        address.postcode = normalizePostcode(request.postcode, request.country)
        address.city = request.city.trim()
        address.country = request.country
        return address
    }

    @Transactional
    fun delete(userId: Long, addressId: Long) {
        addressRepository.delete(findOwned(userId, addressId))
    }

    /** Checks the postcode for the country and returns it in its usual notation, such as `1234 AB`. */
    fun normalizePostcode(postcode: String, country: String): String {
        val compact = postcode.replace(WHITESPACE, "").uppercase()
        return when (country) {
            "NL" -> {
                if (!DUTCH_POSTCODE.matches(compact)) throw invalidPostcode("1234 AB")
                "${compact.take(4)} ${compact.drop(4)}"
            }
            "BE" -> {
                if (!BELGIAN_POSTCODE.matches(compact)) throw invalidPostcode("1000")
                compact
            }
            else -> compact
        }
    }

    private fun findOwned(userId: Long, addressId: Long): Address =
        addressRepository.findByIdAndUserId(addressId, userId)
            ?: throw ApiException.notFound("We konden dit adres niet vinden.")

    private fun invalidPostcode(example: String) =
        ApiException.validation("Deze postcode klopt niet. Gebruik de notatie $example.")

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val DUTCH_POSTCODE = Regex("[1-9][0-9]{3}[A-Z]{2}")
        val BELGIAN_POSTCODE = Regex("[1-9][0-9]{3}")
    }
}
