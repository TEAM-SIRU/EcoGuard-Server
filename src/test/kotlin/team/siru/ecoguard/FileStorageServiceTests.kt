package team.siru.ecoguard

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.storage.FileStorageProperties
import team.siru.ecoguard.common.storage.FileStorageService
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
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

    private fun fileOf(url: String): Path = tempDir.resolve(url.removePrefix("/files/"))

    /** 트랜잭션 안에서 [block] 을 실행한 뒤, 그 트랜잭션이 [status] 로 끝난 것처럼 완료 콜백을 부른다. */
    private fun inTransactionEndingWith(status: Int, block: () -> Unit) {
        TransactionSynchronizationManager.initSynchronization()
        try {
            block()
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCompletion(status) }
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `delete removes the stored file and tolerates a missing one`() {
        val url = service().storeImage(imageBytes("png"), "a.png", "verifications")
        assertTrue(Files.exists(fileOf(url)))

        service().delete(url)
        service().delete(url)

        assertFalse(Files.exists(fileOf(url)))
    }

    @Test
    fun `delete never touches files outside the storage folder`() {
        val outside = Files.createFile(tempDir.resolve("secret.txt"))
        val storage = FileStorageService(FileStorageProperties(basePath = tempDir.resolve("store").toString()))

        storage.delete("/files/../secret.txt")
        storage.delete("/etc/passwd")

        assertTrue(Files.exists(outside))
    }

    @Test
    fun `a rolled back transaction deletes the photo saved in it`() {
        lateinit var url: String

        inTransactionEndingWith(TransactionSynchronization.STATUS_ROLLED_BACK) {
            url = service().storeImage(imageBytes("jpg"), "a.jpg", "verifications")
            service().deleteOnRollback(url)
        }

        assertFalse(Files.exists(fileOf(url)))
    }

    @Test
    fun `a committed or unknown transaction keeps the photo`() {
        for (status in listOf(TransactionSynchronization.STATUS_COMMITTED, TransactionSynchronization.STATUS_UNKNOWN)) {
            lateinit var url: String

            inTransactionEndingWith(status) {
                url = service().storeImage(imageBytes("jpg"), "a.jpg", "verifications")
                service().deleteOnRollback(url)
            }

            assertTrue(Files.exists(fileOf(url)), "status=$status 에서는 사진을 남겨야 한다")
        }
    }

    @Test
    fun `deleteOnRollback outside a transaction does nothing`() {
        val url = service().storeImage(imageBytes("png"), "a.png", "verifications")

        service().deleteOnRollback(url)

        assertTrue(Files.exists(fileOf(url)))
    }

    @Test
    fun `only jpg and png are accepted`() {
        // GIF, BMP 는 읽을 수 있는 이미지여도 AI 에 형식을 잘못 알려 줄 수 있어 받지 않는다.
        for (format in listOf("gif", "bmp")) {
            val ex = assertThrows<BusinessException>("$format 은 거부해야 한다") {
                service().storeImage(imageBytes(format), "photo.$format", "verifications")
            }
            assertTrue(ex.errorCode == ErrorCode.INVALID_IMAGE)
        }
        assertTrue(service().storeImage(imageBytes("jpg"), "a.jpg", "verifications").endsWith(".jpg"))
        assertTrue(service().storeImage(imageBytes("png"), "a.png", "verifications").endsWith(".png"))
    }

    @Test
    fun `non image content is rejected even with an image extension`() {
        val ex = assertThrows<BusinessException> {
            service().storeImage("<html><script>alert(1)</script></html>".toByteArray(), "photo.jpg", "verifications")
        }
        assertTrue(ex.errorCode == ErrorCode.INVALID_IMAGE)
    }
}
