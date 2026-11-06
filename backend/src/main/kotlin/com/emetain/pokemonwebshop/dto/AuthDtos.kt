package com.emetain.pokemonwebshop.dto

import com.emetain.pokemonwebshop.domain.BinderSource
import com.emetain.pokemonwebshop.domain.Currency
import com.emetain.pokemonwebshop.domain.Role
import com.emetain.pokemonwebshop.domain.User
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class RegisterRequest(
    @field:NotBlank(message = "Vul je e-mailadres in.")
    @field:Email(message = "Dit e-mailadres klopt niet.")
    @field:Size(max = 254, message = "Dit e-mailadres is te lang.")
    val email: String = "",

    @field:Size(min = 8, message = "Kies een wachtwoord van minstens 8 tekens.")
    val password: String = "",

    @field:NotBlank(message = "Vul je naam in.")
    @field:Size(max = 100, message = "Je naam mag maximaal 100 tekens lang zijn.")
    val displayName: String = "",
)

data class LoginRequest(
    @field:NotBlank(message = "Vul je e-mailadres in.")
    val email: String = "",

    @field:NotBlank(message = "Vul je wachtwoord in.")
    val password: String = "",
)

data class ForgotPasswordRequest(
    @field:NotBlank(message = "Vul je e-mailadres in.")
    @field:Email(message = "Dit e-mailadres klopt niet.")
    val email: String = "",
)

data class ResetPasswordRequest(
    @field:NotBlank(message = "Deze link is verlopen of al gebruikt. Vraag een nieuwe link aan.")
    val token: String = "",

    @field:Size(min = 8, message = "Kies een wachtwoord van minstens 8 tekens.")
    val password: String = "",
)

data class UserDto(
    val id: Long,
    val email: String,
    val displayName: String,
    val role: Role,
    val country: String,
    val currency: Currency,
    val binderSource: BinderSource,
    /** The random part of the public link to the collection. Null means the collection is private. */
    val collectionShareSlug: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(user: User) = UserDto(
            id = user.id,
            email = user.email,
            displayName = user.displayName,
            role = user.role,
            country = user.country,
            currency = user.currency,
            binderSource = user.binderSource ?: BinderSource.DEFAULT,
            collectionShareSlug = user.collectionShareSlug,
            createdAt = user.createdAt,
        )
    }
}
