package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.MysteryBoxBatch
import com.emetain.pokemonwebshop.domain.MysteryBoxDraw
import com.emetain.pokemonwebshop.domain.MysteryBoxPrize
import com.emetain.pokemonwebshop.domain.Order
import com.emetain.pokemonwebshop.domain.OrderStatusChange
import com.emetain.pokemonwebshop.domain.ProductType
import com.emetain.pokemonwebshop.domain.StockReason
import com.emetain.pokemonwebshop.domain.User
import com.emetain.pokemonwebshop.dto.MysteryBoxBatchRequest
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.CardRepository
import com.emetain.pokemonwebshop.repository.MysteryBoxBatchRepository
import com.emetain.pokemonwebshop.repository.MysteryBoxDrawRepository
import com.emetain.pokemonwebshop.repository.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock

/** The chances of a batch. Pure, so the draw can be tested without a database. */
object MysteryBoxMath {

    /**
     * Which kind of box a roll gets, for a roll from 0 up to the number of boxes left. Every box that is left has the
     * same chance, so a kind with 3 boxes left is three times as likely as one with 1.
     */
    fun pick(remaining: List<Int>, roll: Int): Int {
        require(remaining.all { it >= 0 }) { "Counts can't be negative" }
        require(roll in 0 until remaining.sum()) { "Roll $roll is outside 0 until ${remaining.sum()}" }
        var left = roll
        remaining.forEachIndexed { index, count ->
            if (left < count) return index
            left -= count
        }
        error("The roll is always inside the counts")
    }

    /** The chance of each kind in percent, with one decimal, of the boxes that are left. */
    fun chances(remaining: List<Int>): List<Double> {
        val total = remaining.sum()
        if (total == 0) return remaining.map { 0.0 }
        return remaining.map { Math.round(it * PER_MILLE / total) / 10.0 }
    }

    private const val PER_MILLE = 1000.0
}

/** Batches of mystery boxes, and which box a paid order gets. */
@Service
class MysteryBoxService(
    private val batchRepository: MysteryBoxBatchRepository,
    private val drawRepository: MysteryBoxDrawRepository,
    private val productRepository: ProductRepository,
    private val cardRepository: CardRepository,
    private val stockService: StockService,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    @Transactional(readOnly = true)
    fun batches(productId: Long): List<MysteryBoxBatch> = batchRepository.findByProduct(productId)

    @Transactional(readOnly = true)
    fun draws(batchId: Long): List<MysteryBoxDraw> = drawRepository.findByBatch(batchId)

    /** The batch boxes are drawn from now: the oldest with boxes left. Null when none has any. */
    @Transactional(readOnly = true)
    fun current(productId: Long): MysteryBoxBatch? = batchRepository.findByProduct(productId).firstOrNull { it.remaining > 0 }

    /** Every box goes on the stock, with a line in the stock log. */
    @Transactional
    fun createBatch(productId: Long, request: MysteryBoxBatchRequest, admin: User): MysteryBoxBatch {
        val product = productRepository.findById(productId).orElseThrow { ApiException.notFound("We konden dit product niet vinden.") }
        if (product.type != ProductType.MYSTERY_BOX) throw ApiException.validation("Alleen een mystery box heeft batches.")
        val name = request.name.trim()
        if (name.isEmpty()) throw ApiException.validation("Geef de batch een naam.")
        if (request.prizes.isEmpty()) throw ApiException.validation("Voeg minstens één soort doos toe.")
        if (request.prizes.size > MAX_PRIZES) throw ApiException.validation("Een batch heeft maximaal $MAX_PRIZES soorten dozen.")
        val batch = MysteryBoxBatch(product = product, name = name, createdAt = clock.instant())
        for (prize in request.prizes) {
            val prizeName = prize.name.trim()
            if (prizeName.isEmpty()) throw ApiException.validation("Elke soort doos heeft een omschrijving.")
            if (prize.count !in 1..MAX_BOXES) throw ApiException.validation("Kies per soort 1 tot $MAX_BOXES dozen.")
            if (prize.valueCents < 0) throw ApiException.validation("Een waarde kan niet negatief zijn.")
            val card = prize.cardId?.let { cardRepository.findById(it).orElseThrow { ApiException.notFound("We konden deze kaart niet vinden.") } }
            batch.prizes += MysteryBoxPrize(batch = batch, name = prizeName, valueCents = prize.valueCents, count = prize.count, card = card)
        }
        if (batch.boxCount > MAX_BOXES) throw ApiException.validation("Een batch heeft maximaal $MAX_BOXES dozen.")
        val saved = batchRepository.save(batch)
        stockService.adjust(product.id, saved.boxCount, StockReason.PURCHASE, "Mystery box batch \"$name\": ${saved.boxCount} dozen", admin)
        return saved
    }

    /** Only before any box of it was sold. Its boxes leave the stock again. */
    @Transactional
    fun deleteBatch(batchId: Long, admin: User) {
        val batch = batchRepository.findById(batchId).orElseThrow { ApiException.notFound("We konden deze batch niet vinden.") }
        if (batch.prizes.any { it.drawn > 0 }) {
            throw ApiException.validation("Uit deze batch zijn al dozen verkocht. Hij blijft staan, zodat de bestellingen kloppen.")
        }
        val product = batch.product
        if (product.stock.available < batch.remaining) {
            throw ApiException.validation("Er lopen nog bestellingen met deze dozen. Probeer het over een kwartier opnieuw.")
        }
        stockService.adjust(product.id, -batch.remaining, StockReason.CORRECTION, "Mystery box batch \"${batch.name}\" verwijderd", admin)
        batchRepository.delete(batch)
    }

    /**
     * Called when an order is paid, in the same transaction. Every mystery box in it gets a random box that is left,
     * from the oldest batch first. The order's history says which, so the shop knows what to send.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun drawFor(order: Order) {
        val lines = order.lines.filter { it.productType == ProductType.MYSTERY_BOX }
        for (line in lines) {
            val batches = batchRepository.findLockedByProduct(line.product.id)
            val names = mutableListOf<String>()
            repeat(line.quantity) {
                val batch = batches.firstOrNull { it.remaining > 0 }
                if (batch == null) {
                    names += "geen doos meer over in de batches, kies er zelf een"
                    return@repeat
                }
                val prize = batch.prizes[MysteryBoxMath.pick(batch.prizes.map { it.remaining }, random.nextInt(batch.remaining))]
                prize.drawn += 1
                drawRepository.save(MysteryBoxDraw(prize = prize, orderLine = line, drawnAt = clock.instant()))
                names += "${prize.name} (${batch.name})"
            }
            order.history += OrderStatusChange(order, order.status, clock.instant(), "Mystery box ${line.productName}: ${names.joinToString("; ")}")
        }
    }

    /** When the stock of an order goes back: its boxes go back in their batch, and can be drawn again. */
    @Transactional(propagation = Propagation.MANDATORY)
    fun releaseFor(order: Order) {
        val draws = drawRepository.findByOrder(order.id)
        if (draws.isEmpty()) return
        draws.map { it.orderLine.product.id }.distinct().forEach(batchRepository::findLockedByProduct)
        for (draw in draws) {
            draw.prize.drawn -= 1
            drawRepository.delete(draw)
        }
        order.history += OrderStatusChange(order, order.status, clock.instant(), "Mystery box: ${draws.size} doos/dozen terug in de batch")
    }

    /** Per order line, the boxes it got. */
    @Transactional(readOnly = true)
    fun namesByLine(orderId: Long): Map<Long, List<String>> =
        drawRepository.findByOrder(orderId).groupBy({ it.orderLine.id }, { it.prize.name })

    private companion object {
        const val MAX_PRIZES = 30
        const val MAX_BOXES = 10_000
    }
}
