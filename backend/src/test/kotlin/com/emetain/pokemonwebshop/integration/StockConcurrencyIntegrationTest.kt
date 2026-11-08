package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.repository.StockMovementRepository
import com.emetain.pokemonwebshop.service.StockService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Two people can't both buy the last item. */
class StockConcurrencyIntegrationTest : ShopTestSupport() {

    @Autowired
    private lateinit var stockService: StockService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var movementRepository: StockMovementRepository

    @Autowired
    private lateinit var transactions: TransactionTemplate

    @Test
    fun `two purchases that both saw the last item can't both succeed`() {
        val productId = stocked(quantity = 1)["id"].asLong()
        val bothRead = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)

        // Each buyer reads the stock first and waits for the other, so both start from "1 left".
        val outcomes = (1..2).map {
            executor.submit<Result<Unit>> {
                runCatching {
                    transactions.execute {
                        productRepository.findWithDetailsById(productId)!!.stock.available
                        bothRead.await(5, TimeUnit.SECONDS)
                        stockService.sell(productId, 1)
                    }
                    Unit
                }
            }
        }.map { it.get(20, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(1, outcomes.count { it.isSuccess })
        assertIs<OptimisticLockingFailureException>(outcomes.single { it.isFailure }.exceptionOrNull())
        val product = transactions.execute { productRepository.findWithDetailsById(productId)!!.stock.onHand }
        assertEquals(0, product)
        val sales = movementRepository.findByProduct(productId, PageRequest.of(0, 10)).content.count { it.reason.name == "SALE" }
        assertEquals(1, sales)
    }

    @Test
    fun `many buyers at once never buy more than there is`() {
        val stock = 3
        val buyers = 20
        val productId = stocked(quantity = stock)["id"].asLong()
        val start = CyclicBarrier(buyers)
        val sold = AtomicInteger()
        val soldOut = AtomicInteger()
        val executor = Executors.newFixedThreadPool(buyers)

        (1..buyers).map {
            executor.submit {
                start.await(5, TimeUnit.SECONDS)
                // As a checkout would: a lost race is tried once more with the new numbers.
                repeat(MAX_TRIES) {
                    try {
                        stockService.sell(productId, 1)
                        sold.incrementAndGet()
                        return@submit
                    } catch (exception: OptimisticLockingFailureException) {
                        // Someone else changed the stock first: look again.
                    } catch (exception: ApiException) {
                        soldOut.incrementAndGet()
                        return@submit
                    }
                }
            }
        }.forEach { it.get(30, TimeUnit.SECONDS) }
        executor.shutdown()

        assertEquals(stock, sold.get())
        val onHand = transactions.execute { productRepository.findWithDetailsById(productId)!!.stock.onHand }
        assertEquals(0, onHand)
        assertEquals(stock, movementRepository.findByProduct(productId, PageRequest.of(0, 50)).content.count { it.reason.name == "SALE" })
    }

    @Test
    fun `a sale of more than is left is refused`() {
        val productId = stocked(quantity = 2)["id"].asLong()
        val error = kotlin.runCatching { stockService.sell(productId, 3) }.exceptionOrNull()
        assertIs<ApiException>(error)
        assertEquals("out_of_stock", error.code)
    }

    @Test
    fun `reserved stock can't be sold to someone else and comes back on release`() {
        val productId = stocked(quantity = 2)["id"].asLong()
        stockService.reserve(productId, 2)

        assertIs<ApiException>(kotlin.runCatching { stockService.sell(productId, 1) }.exceptionOrNull())
        stockService.release(productId, 1)
        stockService.sell(productId, 1)

        val stock = transactions.execute { productRepository.findWithDetailsById(productId)!!.stock.let { it.onHand to it.reserved } }
        assertEquals(1 to 1, stock)
    }

    private companion object {
        const val MAX_TRIES = 50
    }
}
