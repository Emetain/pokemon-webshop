package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AddressDto
import com.emetain.pokemonwebshop.dto.AddressRequest
import com.emetain.pokemonwebshop.dto.UpdateProfileRequest
import com.emetain.pokemonwebshop.dto.UserDto
import com.emetain.pokemonwebshop.orchestration.AccountOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RequiresLogin
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/account")
@RequiresLogin
class AccountController(
    private val accountOrchestrator: AccountOrchestrator,
) {

    @PatchMapping
    fun updateProfile(@CurrentUser user: User, @Valid @RequestBody request: UpdateProfileRequest): UserDto =
        accountOrchestrator.updateProfile(user, request)

    @GetMapping("/addresses")
    fun addresses(@CurrentUser user: User): List<AddressDto> =
        accountOrchestrator.addresses(user)

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    fun createAddress(@CurrentUser user: User, @Valid @RequestBody request: AddressRequest): AddressDto =
        accountOrchestrator.createAddress(user, request)

    @PutMapping("/addresses/{id}")
    fun updateAddress(
        @CurrentUser user: User,
        @PathVariable id: Long,
        @Valid @RequestBody request: AddressRequest,
    ): AddressDto = accountOrchestrator.updateAddress(user, id, request)

    @DeleteMapping("/addresses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAddress(@CurrentUser user: User, @PathVariable id: Long) =
        accountOrchestrator.deleteAddress(user, id)
}
