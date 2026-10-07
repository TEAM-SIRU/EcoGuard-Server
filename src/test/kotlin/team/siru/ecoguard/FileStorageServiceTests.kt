package team.siru.ecoguard

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.storage.FileStorageProperties
import team.siru.ecoguard.common.storage.FileStorageService
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO

class FileStorageServiceTests {

    @TempDir
    lateinit var tempDir: Path

    private fun service() = FileStorageService(FileStorageProperties(basePath = tempDir.toString()))

    private fun imageBytes(format: String): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), format, out)
        return out.toByteArray()
    }

    @Test
    fun `extension comes from the real image format, not the client filename`() {
        val url = service().storeImage(imageBytes("png"), "evil.html", "verifications")

        assertTrue(url.endsWith(".png"), url)
    }

    @Test
    fun `path characters in the filename are never used`() {
        val url = service().storeImage(imageBytes("jpg"), "a.b/../../x", "verifications")

        assertTrue(url.endsWith(".jpg"), url)
        assertTrue(url.startsWith("/files/verifications/"), url)
    }

    @Test
    fun `image with too many pixels is rejected before decoding`() {
        // 단색 PNG 는 파일이 작지만 8000x8000(6400만 화소)이라 디코딩하면 메모리를 많이 쓴다.
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(8000, 8000, BufferedImage.TYPE_BYTE_GRAY), "png", out)

        val ex = assertThrows<BusinessException> {
            service().storeImage(out.toByteArray(), "huge.png", "verifications")
        }
        assertTrue(ex.errorCode == ErrorCode.INVALID_IMAGE)
    }

    @Test
    fun `non image content is rejected even with an image extension`() {
        val ex = assertThrows<BusinessException> {
            service().storeImage("<html><script>alert(1)</script></html>".toByteArray(), "photo.jpg", "verifications")
        }
        assertTrue(ex.errorCode == ErrorCode.INVALID_IMAGE)
    }
}
