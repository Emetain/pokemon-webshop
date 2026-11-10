package com.emetain.pokemonwebshop.integration

import com.emetain.pokemonwebshop.domain.OrderStatus
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Many buyers at the same moment, as at the start of a real drop. The drop never sells more than it has. */
class DropConcurrencyIntegrationTest : DropTestSupport() {

    @Test
    fun `forty buyers checking out at once never take more than the seven allocated`() {
        val product = stocked(quantity = 20)
        val productId = product["id"].asLong()
        val drop = createDrop(listOf(productId to 7), productMax = 1)
        val buyers = (1..40).map { buyer() }
        buyers.forEach { join(drop, it).andExpect(status().isOk) }
        setSpeed(drop, 6000)
        startNow(drop)
        buyers.forEach { buyer ->
            check(ping(drop, buyer)["state"].asText() == "ADMITTED")
            setQuantity(drop, buyer, drop.dropProductIds[0], 1).andExpect(status().isOk)
        }

        // Each buyer uses their own address, so only the stock limits them.
        val statuses = runAtOnce(buyers.mapIndexed { index, buyer ->
            {
                checkout(drop, buyer, mapOf("address" to address + mapOf("houseNumber" to "${index + 1}")))
                    .andReturn().response.let { it.status to it.contentAsString }
            }
        })
        val created = statuses.filter { it.first == 201 }
        assertEquals(7, created.size, "exactly the allocated quantity: ${statuses.groupingBy { it.first }.eachCount()}")
        assertTrue(statuses.filter { it.first != 201 }.all { it.first == 409 }, "the rest hear it's gone: ${statuses.filter { it.first !in setOf(201, 409) }}")

        val held = dropProduct(drop.id, drop.dropProductIds[0])
        assertEquals(7, held.reserved)
        assertEquals(0, held.available)

        // All seven pay, also at the same moment.
        val numbers = created.map { objectMapper.readTree(it.second)["orderNumber"].asText() }
        runAtOnce(numbers.map { number -> { pay(number).andReturn().response.status } })
        val sold = dropProduct(drop.id, drop.dropProductIds[0])
        assertEquals(7, sold.sold)
        assertEquals(0, sold.reserved)
        val paid = orderRepository.findAll().filter { it.drop?.id == drop.id && it.status == OrderStatus.PAID }
        assertEquals(7, paid.size)
        assertEquals(13, stock(productId)["onHand"].asInt(), "the normal stock kept the 13 that weren't allocated")
    }

    @Test
    fun `buyers adding and checking out while the waiting room lets more people in still stop at the allocation`() {
        val product = stocked(quantity = 5)
        val drop = createDrop(listOf(product["id"].asLong() to 5), productMax = 2)
        startNow(drop)
        val buyers = (1..12).map { admittedBuyer(drop) }

        val results = runAtOnce(buyers.mapIndexed { index, buyer ->
            {
                val added = setQuantity(drop, buyer, drop.dropProductIds[0], 2).andReturn().response.status
                if (added == 200) {
                    checkout(drop, buyer, mapOf("address" to address + mapOf("houseNumber" to "${index + 1}"))).andReturn().response.status
                } else {
                    added
                }
            }
        })
        val held = dropProduct(drop.id, drop.dropProductIds[0])
        assertTrue(held.reserved <= 5, "never more held than allocated: ${held.reserved}")
        assertEquals(held.reserved, 2 * results.count { it == 201 }, "every checkout holds its 2: $results")
        assertTrue(held.reserved >= 4, "the stock that fits two checkouts was sold: $results")
    }

    private fun <T> runAtOnce(tasks: List<() -> T>): List<T> {
        val pool = Executors.newFixedThreadPool(tasks.size.coerceAtMost(THREADS))
        try {
            val start = CountDownLatch(1)
            val futures = tasks.map { task ->
                pool.submit<T> {
                    start.await()
                    task()
                }
            }
            start.countDown()
            return futures.map { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    private companion object {
        const val THREADS = 20
    }
}
