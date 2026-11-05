package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminStatusDto
import org.springframework.stereotype.Component

@Component
class AdminOrchestrator {

    fun status(user: User): AdminStatusDto = AdminStatusDto(displayName = user.displayName)
}
