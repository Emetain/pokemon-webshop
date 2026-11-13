package com.emetain.pokemonwebshop.orchestration

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.SetRelease
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.AdminHeroSlideDto
import com.emetain.pokemonwebshop.dto.AdminSetReleaseDto
import com.emetain.pokemonwebshop.dto.CardSetDto
import com.emetain.pokemonwebshop.dto.ProductSummaryDto
import com.emetain.pokemonwebshop.dto.ReleaseCalendarDto
import com.emetain.pokemonwebshop.dto.SetReleaseDto
import com.emetain.pokemonwebshop.dto.SetReleaseRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.service.ProductWishlistService
import com.emetain.pokemonwebshop.service.ReleaseService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@Component
class ReleaseOrchestrator(
    private val releaseService: ReleaseService,
    private val wishlistService: ProductWishlistService,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun calendar(gameSlug: String?, viewer: User?): ReleaseCalendarDto {
        val releases = releaseService.calendar(game(gameSlug))
        val withProducts = releases.associateWith(releaseService::preOrdersOf)
        val wished = viewer?.let { wishlistService.wished(it.id, withProducts.values.flatten().map { product -> product.id }) }
        val today = LocalDate.now(clock.withZone(ZoneId.of("Europe/Amsterdam")))
        val dtos = releases.map { release ->
            SetReleaseDto(
                id = release.id,
                game = release.game.slug,
                name = release.name,
                releaseDate = release.releaseDate,
                tentative = release.tentative,
                description = release.description,
                imageUrl = release.imageUrl,
                set = release.cardSet?.let(CardSetDto::from),
                preOrders = withProducts.getValue(release).map { ProductSummaryDto.from(it, wished?.contains(it.id)) },
            )
        }
        val (upcoming, recent) = dtos.partition { !it.releaseDate.isBefore(today) }
        return ReleaseCalendarDto(upcoming, recent.sortedByDescending { it.releaseDate })
    }

    @Transactional(readOnly = true)
    fun preOrders(gameSlug: String?, viewer: User?): List<ProductSummaryDto> {
        val products = releaseService.preOrders(game(gameSlug))
        val wished = viewer?.let { wishlistService.wished(it.id, products.map { product -> product.id }) }
        return products.map { ProductSummaryDto.from(it, wished?.contains(it.id)) }
    }

    @Transactional(readOnly = true)
    fun all(): List<AdminSetReleaseDto> = releaseService.all().map(AdminSetReleaseDto::from)

    @Transactional
    fun create(request: SetReleaseRequest): AdminSetReleaseDto = admin(releaseService.create(request))

    @Transactional
    fun update(releaseId: Long, request: SetReleaseRequest): AdminSetReleaseDto = admin(releaseService.update(releaseId, request))

    fun delete(releaseId: Long) = releaseService.delete(releaseId)

    @Transactional
    fun replaceImage(releaseId: Long, bytes: ByteArray): AdminSetReleaseDto = admin(releaseService.replaceImage(releaseId, bytes))

    @Transactional
    fun removeImage(releaseId: Long): AdminSetReleaseDto = admin(releaseService.removeImage(releaseId))

    @Transactional
    fun createSlide(releaseId: Long): AdminHeroSlideDto = AdminHeroSlideDto.from(releaseService.createSlide(releaseId), clock.instant())

    private fun admin(release: SetRelease) = AdminSetReleaseDto.from(release)

    private fun game(slug: String?): Game? =
        slug?.takeIf { it.isNotBlank() }?.let { Game.fromSlug(it) ?: throw ApiException.notFound() }
}
