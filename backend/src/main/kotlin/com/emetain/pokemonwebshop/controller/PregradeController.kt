package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.PregradeResultDto
import com.emetain.pokemonwebshop.security.CurrentUser
import com.emetain.pokemonwebshop.security.RateLimited
import com.emetain.pokemonwebshop.security.RequiresLogin
import com.emetain.pokemonwebshop.service.PregradeService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/** The pre-grade check (phase 11). Premium only: the service checks it. */
@RestController
@RequestMapping("/v1/pregrade")
@RequiresLogin
class PregradeController(
    private val pregradeService: PregradeService,
) {

    @PostMapping
    @RateLimited("scanner")
    fun check(
        @CurrentUser user: User,
        @RequestPart("front") front: MultipartFile,
        @RequestPart("back") back: MultipartFile,
    ): PregradeResultDto = pregradeService.check(user, front.bytes, back.bytes)
}
