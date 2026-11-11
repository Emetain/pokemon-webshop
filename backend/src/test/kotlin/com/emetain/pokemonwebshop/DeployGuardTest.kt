package com.emetain.pokemonwebshop

import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

/**
 * Fails on purpose, to check that a push with a failing test deploys nothing (phase 7). The next commit reverts it.
 */
class DeployGuardTest {
    @Test
    fun `a failing test stops the deploy`() {
        fail<Unit>("Fails on purpose: this push must not reach the preview")
    }
}
