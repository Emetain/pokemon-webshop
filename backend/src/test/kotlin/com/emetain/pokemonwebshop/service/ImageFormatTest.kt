package com.emetain.pokemonwebshop.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageFormatTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `recognizes the first bytes of each format`() {
        assertEquals(ImageFormat.JPEG, ImageFormat.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals(ImageFormat.PNG, ImageFormat.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0)))
        assertEquals(ImageFormat.WEBP, ImageFormat.detect("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray()))
    }

    @Test
    fun `refuses everything else, whatever it's called`() {
        assertNull(ImageFormat.detect(ByteArray(0)))
        assertNull(ImageFormat.detect(bytes(0xFF, 0xD8)))
        assertNull(ImageFormat.detect("<svg xmlns='http://www.w3.org/2000/svg'><script/></svg>".toByteArray()))
        assertNull(ImageFormat.detect("<html><script>alert(1)</script>".toByteArray()))
        assertNull(ImageFormat.detect("RIFF\u0000\u0000\u0000\u0000WAVEfmt ".toByteArray()))
    }
}
