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
        runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
            ?: throw BusinessException(ErrorCode.INVALID_IMAGE)

        val directory = Path.of(properties.basePath, subDirectory)
        Files.createDirectories(directory)

        val extension = originalFilename?.substringAfterLast('.', "jpg") ?: "jpg"
        val fileName = "${UUID.randomUUID()}.$extension"
        val destination = directory.resolve(fileName)
        Files.write(destination, bytes)

        return "${properties.publicUrlPrefix}/$subDirectory/$fileName"
    }
}
