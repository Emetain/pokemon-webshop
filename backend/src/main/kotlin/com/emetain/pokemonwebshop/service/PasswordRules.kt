package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.exception.ApiException

/**
 * A new password is typed twice: when registering, after a reset mail, and when a guest makes an account after paying.
 * The page checks it too, but the rule shouldn't depend on the page. Logging in types it once.
 */
object PasswordRules {
    const val MISMATCH = "De wachtwoorden zijn niet hetzelfde."

    fun requireConfirmed(password: String, confirmation: String) {
        if (password != confirmation) throw ApiException.validation(MISMATCH)
    }
}
