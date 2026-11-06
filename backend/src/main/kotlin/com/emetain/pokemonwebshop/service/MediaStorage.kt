package com.emetain.pokemonwebshop.service

import com.emetain.pokemonwebshop.config.AppProperties
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** The image files we accept. SVG is left out on purpose: it can hold scripts. */
enum class ImageFormat(val extension: String, val contentType: String) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp"),
    ;

    companion object {
        /** Goes by the first bytes of the file, because a file name or content type can say anything. */
        fun detect(bytes: ByteArray): ImageFormat? = when {
            bytes.startsWith(0xFF, 0xD8, 0xFF) -> JPEG
            bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> PNG
            bytes.size >= 12 && bytes.text(0, 4) == "RIFF" && bytes.text(8, 12) == "WEBP" -> WEBP
            else -> null
        }

        fun fromExtension(extension: String): ImageFormat? = entries.firstOrNull { it.extension == extension }

        private fun ByteArray.startsWith(vararg start: Int): Boolean =
            size >= start.size && start.indices.all { this[it] == start[it].toByte() }

        private fun ByteArray.text(from: Int, to: Int) = String(this, from, to - from, Charsets.ISO_8859_1)
    }
}

data class StoredMedia(val resource: Resource, val format: ImageFormat)

/** Keeps uploaded images. Behind an interface, so cloud storage can take over later. */
interface MediaStorage {
    /** Returns the public URL of the file. */
    fun store(bytes: ByteArray, format: ImageFormat): String

    fun read(fileName: String): StoredMedia?

    /** Does nothing for a URL that isn't one of ours. */
    fun delete(url: String)
}

@Component
class DiskMediaStorage(properties: AppProperties) : MediaStorage {

    private val directory: Path = Path.of(properties.media.directory).toAbsolutePath()

    override fun store(bytes: ByteArray, format: ImageFormat): String {
        Files.createDirectories(directory)
        val fileName = "${UUID.randomUUID().toString().replace("-", "")}.${format.extension}"
        Files.write(directory.resolve(fileName), bytes)
        return PUBLIC_PATH + fileName
    }

    override fun read(fileName: String): StoredMedia? {
        if (!SAFE_FILE_NAME.matches(fileName)) return null
        val path = directory.resolve(fileName)
        val format = ImageFormat.fromExtension(fileName.substringAfterLast('.'))
        return if (format != null && Files.isRegularFile(path)) StoredMedia(FileSystemResource(path), format) else null
    }

    override fun delete(url: String) {
        val fileName = url.removePrefix(PUBLIC_PATH)
        if (SAFE_FILE_NAME.matches(fileName)) Files.deleteIfExists(directory.resolve(fileName))
    }

    private companion object {
        const val PUBLIC_PATH = "/api/v1/media/"

        // Only names we made ourselves, so a request can never reach outside the folder.
        val SAFE_FILE_NAME = Regex("[a-f0-9]{32}\\.(jpg|png|webp)")
    }
}
