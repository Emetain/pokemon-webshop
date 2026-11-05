package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.ForgotPasswordRequest
import com.emetain.pokemonwebshop.dto.LoginRequest
import com.emetain.pokemonwebshop.dto.RegisterRequest
import com.emetain.pokemonwebshop.dto.ResetPasswordRequest
import com.emetain.pokemonwebshop.dto.UserDto
import com.emetain.pokemonwebshop.orchestration.AuthOrchestrator
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RateLimited
import com.emetain.pokemonwebshop.security.RequiresLogin
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/auth")
class AuthController(
    private val authOrchestrator: AuthOrchestrator,
) {

    @PostMapping("/register")
    @RateLimited("register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<UserDto> =
        authOrchestrator.register(request)

    @PostMapping("/login")
    @RateLimited("login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<UserDto> =
        authOrchestrator.login(request)

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest): ResponseEntity<Void> =
        authOrchestrator.logout(request)

    @GetMapping("/me")
    @RequiresLogin
    fun me(@CurrentUser user: User): UserDto =
        authOrchestrator.me(user)

    @PostMapping("/forgot-password")
    @RateLimited("forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun forgotPassword(@Valid @RequestBody request: ForgotPasswordRequest) =
        authOrchestrator.forgotPassword(request)

    @PostMapping("/reset-password")
    @RateLimited("reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resetPassword(@Valid @RequestBody request: ResetPasswordRequest) =
        authOrchestrator.resetPassword(request)
}
