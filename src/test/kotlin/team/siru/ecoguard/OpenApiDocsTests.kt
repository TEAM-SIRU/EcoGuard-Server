package team.siru.ecoguard

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest(properties = ["springdoc.api-docs.enabled=true"])
@AutoConfigureMockMvc
class OpenApiDocsTests @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @Test
    fun `api docs list the public endpoints without a login`() {
        val body = mockMvc.get("/v3/api-docs").andReturn().response.contentAsString

        assertTrue(body.contains("\"/api/v1/auth/login\""), body.take(300))
        assertTrue(body.contains("\"/api/v1/verifications\""), body.take(300))
        assertTrue(body.contains("bearerAuth"), body.take(300))
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDisabledByDefaultTests @Autowired constructor(
    private val mockMvc: MockMvc,
) {

    @Test
    fun `api docs are not served unless SWAGGER_ENABLED is on`() {
        val response = mockMvc.get("/v3/api-docs").andReturn().response

        assertNotEquals(200, response.status)
        assertFalse(response.contentAsString.contains("openapi"))
    }
}
