package com.emetain.pokemonwebshop.repository

import com.emetain.pokemonwebshop.domain.AccessoryType
import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.ProductImportSource
import com.emetain.pokemonwebshop.domain.ProductLanguage
import com.emetain.pokemonwebshop.domain.ProductStatus
import com.emetain.pokemonwebshop.domain.ProductType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query

/** What kinds of products exist in a part of the shop, so a filter only offers choices with results. */
data class ProductFacetRow(
    val type: ProductType,
    val accessoryType: AccessoryType?,
    val language: ProductLanguage?,
    val setId: Long?,
)

data class SetProductCount(val setId: Long, val count: Long)

interface ProductRepository : JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = ["stock", "cardSet", "card"])
    override fun findAll(spec: Specification<Product>?, pageable: Pageable): Page<Product>

    @EntityGraph(attributePaths = ["stock", "cardSet", "card", "card.set"])
    fun findBySlug(slug: String): Product?

    @EntityGraph(attributePaths = ["stock", "cardSet", "card", "card.set"])
    fun findWithDetailsById(id: Long): Product?

    fun existsBySlug(slug: String): Boolean

    /** A duplicated product shares its images, so a file may only go when nothing uses it any more. */
    @Query("select count(p) from Product p join p.images i where i = :url")
    fun countUsingImage(url: String): Long

    @Query("select p.slug from Product p")
    fun findAllSlugs(): List<String>

    @EntityGraph(attributePaths = ["stock"])
    fun findAllByImportSourceAndImportKeyIn(source: ProductImportSource, keys: Collection<String>): List<Product>

    @EntityGraph(attributePaths = ["stock", "cardSet", "card"])
    fun findAllByIdIn(ids: Collection<Long>): List<Product>

    /** Singles and slabs of these cards that are in the shop and can be bought right now. */
    @EntityGraph(attributePaths = ["stock"])
    @Query(
        "select p from Product p where p.card.id in :cardIds and p.status = :status " +
            "and p.priceCents is not null and p.stock.onHand - p.stock.reserved > 0",
    )
    fun findBuyableForCards(cardIds: Collection<Long>, status: ProductStatus = ProductStatus.ACTIVE): List<Product>

    @Query(
        "select distinct new com.emetain.pokemonwebshop.repository.ProductFacetRow(p.type, p.accessoryType, p.language, s.id) " +
            "from Product p left join p.cardSet s where p.status = :status and p.game = :game",
    )
    fun findFacetsOfGame(game: Game, status: ProductStatus = ProductStatus.ACTIVE): List<ProductFacetRow>

    @Query(
        "select distinct new com.emetain.pokemonwebshop.repository.ProductFacetRow(p.type, p.accessoryType, p.language, s.id) " +
            "from Product p left join p.cardSet s where p.status = :status",
    )
    fun findFacets(status: ProductStatus = ProductStatus.ACTIVE): List<ProductFacetRow>

    @Query(
        "select new com.emetain.pokemonwebshop.repository.SetProductCount(p.cardSet.id, count(p)) " +
            "from Product p where p.status = :status and p.game = :game and p.cardSet is not null group by p.cardSet.id",
    )
    fun countPerSet(game: Game, status: ProductStatus = ProductStatus.ACTIVE): List<SetProductCount>

    @Query(
        "select p.type from Product p where p.cardSet.id = :setId and p.language = :language " +
            "and p.status <> :archived",
    )
    fun findTypesInSet(setId: Long, language: ProductLanguage, archived: ProductStatus = ProductStatus.ARCHIVED): List<ProductType>
}
