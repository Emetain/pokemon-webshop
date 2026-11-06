package com.emetain.pokemonwebshop.controller

import com.emetain.pokemonwebshop.exception.ApiException
import com.emetain.pokemonwebshop.service.MediaStorage
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/** Public: serves uploaded images. A new upload gets a new name, so a browser can keep these for a long time. */
@RestController
@RequestMapping("/v1/media")
class MediaController(
    private val mediaStorage: MediaStorage,
) {

    @GetMapping("/{fileName}")
    fun media(@PathVariable fileName: String): ResponseEntity<Resource> {
        val media = mediaStorage.read(fileName) ?: throw ApiException.notFound()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(media.format.contentType))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
            .header("X-Content-Type-Options", "nosniff")
            .body(media.resource)
    }
}
