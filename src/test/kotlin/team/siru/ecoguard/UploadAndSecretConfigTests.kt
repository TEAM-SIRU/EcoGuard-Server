package team.siru.ecoguard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.multipart.MaxUploadSizeExceededException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.common.exception.GlobalExceptionHandler
import team.siru.ecoguard.common.security.JwtProperties

class UploadAndSecretConfigTests {

    @Test
    fun `an oversized upload is a 400 image error, not a server error`() {
        val response = GlobalExceptionHandler().handleUploadTooLarge(MaxUploadSizeExceededException(10L * 1024 * 1024))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.INVALID_IMAGE.name, response.body?.code)
        assertTrue(response.body?.message?.contains("10MB") == true)
    }

    @Test
    fun `a jwt secret shorter than 32 bytes stops the server from starting`() {
        val ex = assertThrows<IllegalStateException> { JwtProperties(secret = "too-short-secret") }

        assertTrue(ex.message!!.contains("32"), ex.message)
    }

    @Test
    fun `a 32 byte jwt secret is accepted`() {
        JwtProperties(secret = "a".repeat(32))
    }
}
