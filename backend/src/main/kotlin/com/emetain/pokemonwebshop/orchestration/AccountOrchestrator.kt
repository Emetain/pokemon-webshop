package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AddressDto
import com.emetain.pokemonwebshop.dto.AddressRequest
import com.emetain.pokemonwebshop.dto.UpdateProfileRequest
import com.emetain.pokemonwebshop.dto.UserDto
import com.emetain.pokemonwebshop.service.AddressService
import com.emetain.pokemonwebshop.service.UserService
import org.springframework.stereotype.Component

@Component
class AccountOrchestrator(
    private val userService: UserService,
    private val addressService: AddressService,
) {

    fun updateProfile(user: User, request: UpdateProfileRequest): UserDto =
        UserDto.from(userService.updateProfile(user.id, request.displayName, request.country, request.currency))

    fun addresses(user: User): List<AddressDto> =
        addressService.list(user.id).map(AddressDto::from)

    fun createAddress(user: User, request: AddressRequest): AddressDto =
        AddressDto.from(addressService.create(user, request))

    fun updateAddress(user: User, addressId: Long, request: AddressRequest): AddressDto =
        AddressDto.from(addressService.update(user.id, addressId, request))

    fun deleteAddress(user: User, addressId: Long) {
        addressService.delete(user.id, addressId)
    }
}
