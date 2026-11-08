package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.domain.Game
import com.emetain.pokemonwebshop.domain.Product
import com.emetain.pokemonwebshop.domain.ProductType
import com.emetain.pokemonwebshop.domain.StockMovement
import com.emetain.pokemonwebshop.domain.StockReason
import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.repository.ProductRepository
import com.emetain.pokemonwebshop.repository.StockMovementRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StockServiceTest {

    private val clock = MutableClock()
    private val product = Product(
        game = Game.POKEMON,
        type = ProductType.BOOSTER_BOX,
        name = "Box",
        nameSearch = "box",
        slug = "box",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        priceCents = 1000,
        id = 7,
    )
    private val products = mock<ProductRepository> { on { findById(7) } doReturn Optional.of(product) }
    private val movements = mock<StockMovementRepository> { on { save(any<StockMovement>()) } doAnswer { it.getArgument(0) } }
    private val events = mock<ApplicationEventPublisher>()
    private val service = StockService(products, movements, events, clock)

    @Test
    fun `a purchase adds to the stock on hand and writes the numbers after it`() {
        val movement = service.adjust(7, 5, StockReason.PURCHASE, "  Levering  ", null)

        assertEquals(5, product.stock.onHand)
        assertEquals(5, movement.change)
        assertEquals(5, movement.onHandAfter)
        assertEquals(0, movement.reservedAfter)
        assertEquals("Levering", movement.note)
    }

    @Test
    fun `coming back into stock sets the arrival time and tells who is waiting`() {
        service.adjust(7, 2, StockReason.PURCHASE, null, null)
        assertEquals(clock.instant(), product.arrivedAt)
        verify(events).publishEvent(ProductAvailabilityChanged(7))

        val firstArrival = product.arrivedAt
        clock.advance(java.time.Duration.ofHours(1))
        service.adjust(7, 2, StockReason.PURCHASE, null, null)
        assertEquals(firstArrival, product.arrivedAt, "Still in stock, so it didn't arrive again")
    }

    @Test
    fun `a correction can go down, but not below what is reserved`() {
        service.adjust(7, 5, StockReason.PURCHASE, null, null)
        service.reserve(7, 3)

        service.adjust(7, -2, StockReason.CORRECTION, null, null)
        assertEquals(3, product.stock.onHand)
        assertThrows<ApiException> { service.adjust(7, -1, StockReason.CORRECTION, null, null) }
    }

    @Test
    fun `every reason only goes one way`() {
        assertThrows<ApiException> { service.adjust(7, -1, StockReason.PURCHASE, null, null) }
        assertThrows<ApiException> { service.adjust(7, -1, StockReason.RETURN, null, null) }
        assertThrows<ApiException> { service.adjust(7, 1, StockReason.SALE, null, null) }
        assertThrows<ApiException> { service.adjust(7, 1, StockReason.DROP_ALLOCATION, null, null) }
        assertThrows<ApiException> { service.adjust(7, 1, StockReason.RESERVATION, null, null) }
        assertThrows<ApiException> { service.adjust(7, 0, StockReason.CORRECTION, null, null) }
        verify(movements, never()).save(any<StockMovement>())
    }

    @Test
    fun `a count writes the difference as a correction, and nothing when it matches`() {
        service.adjust(7, 5, StockReason.PURCHASE, null, null)

        val movement = service.correctTo(7, 3, "Geteld", null)!!
        assertEquals(-2, movement.change)
        assertEquals(StockReason.CORRECTION, movement.reason)
        assertNull(service.correctTo(7, 3, "Geteld", null))
        assertThrows<ApiException> { service.correctTo(7, -1, null, null) }
    }

    @Test
    fun `a reservation holds stock and a release gives back no more than is reserved`() {
        service.adjust(7, 4, StockReason.PURCHASE, null, null)

        val reservation = service.reserve(7, 3)
        assertEquals(3, reservation.change)
        assertEquals(1, product.stock.available)
        assertThrows<ApiException> { service.reserve(7, 2) }

        val release = service.release(7, 10)!!
        assertEquals(-3, release.change)
        assertEquals(0, product.stock.reserved)
        assertNull(service.release(7, 1))
    }

    @Test
    fun `a sale takes from what is available`() {
        service.adjust(7, 2, StockReason.PURCHASE, null, null)
        service.reserve(7, 1)

        service.sell(7, 1)
        assertEquals(1, product.stock.onHand)
        assertEquals(0, product.stock.available)
        assertEquals("out_of_stock", assertThrows<ApiException> { service.sell(7, 1) }.code)
        assertThrows<ApiException> { service.sell(7, 0) }
    }
}
