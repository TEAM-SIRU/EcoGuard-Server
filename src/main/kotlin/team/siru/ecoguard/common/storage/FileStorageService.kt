package team.siru.ecoguard.common.storage

import org.springframework.stereotype.Service
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.imageio.ImageIO

@Service
class FileStorageService(
    private val properties: FileStorageProperties,
) {

    fun storeImage(bytes: ByteArray, originalFilename: String?, subDirectory: String): String {
        if (bytes.isEmpty()) {
            throw BusinessException(ErrorCode.INVALID_IMAGE, "이미지 파일이 비어 있습니다.")
        }
        // 확장자는 클라이언트가 보낸 파일명이 아니라 실제로 해석된 이미지 포맷으로 정한다.
        // (인증 없이 /files/** 로 서빙되므로 .html / .svg 같은 확장자가 붙으면 안 된다.)
        val extension = detectExtension(bytes) ?: throw BusinessException(ErrorCode.INVALID_IMAGE)

        val directory = Path.of(properties.basePath, subDirectory)
        Files.createDirectories(directory)

        val fileName = "${UUID.randomUUID()}.$extension"
        Files.write(directory.resolve(fileName), bytes)

        return "${properties.publicUrlPrefix}/$subDirectory/$fileName"
    }

    private fun detectExtension(bytes: ByteArray): String? {
        val formatName = runCatching {
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
                val readers = ImageIO.getImageReaders(input)
                if (!readers.hasNext()) return@use null
                val reader = readers.next()
                try {
                    reader.input = input
                    // 파일 크기는 작아도 해상도가 매우 큰 이미지는 디코딩 때 메모리를 크게 쓰므로, 디코딩 전에 해상도부터 막는다.
                    if (reader.getWidth(0).toLong() * reader.getHeight(0) > MAX_PIXELS) return@use null
                    // 헤더만이 아니라 실제 디코딩이 되는 파일인지 확인한다.
                    reader.read(0)
                    reader.formatName.lowercase()
                } finally {
                    reader.dispose()
                }
            }
        }.getOrNull() ?: return null
        return ALLOWED_EXTENSIONS[formatName]
    }

    companion object {
        /** 최신 스마트폰 사진(최대 약 5천만 화소)까지 허용하고 그보다 큰 이미지는 거부한다. */
        private const val MAX_PIXELS = 50_000_000L

        /**
         * 받는 사진 형식은 JPG, PNG 두 가지뿐이다. AI 검수에 사진을 보낼 때 형식(mime type)을 JPEG/PNG 둘 중 하나로
         * 알려 주므로, 그 밖의 형식(GIF, BMP 등)을 받으면 AI 에 잘못된 형식을 알려 줄 수 있다.
         */
        private val ALLOWED_EXTENSIONS = mapOf(
            "jpeg" to "jpg",
            "jpg" to "jpg",
            "png" to "png",
        )
    }
}
