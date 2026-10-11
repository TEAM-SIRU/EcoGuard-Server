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
import tools.jackson.databind.ObjectMapper

@SpringBootTest(properties = ["springdoc.api-docs.enabled=true"])
@AutoConfigureMockMvc
class OpenApiDocsTests @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) {

    @Test
    fun `api docs list the public endpoints without a login`() {
        val body = mockMvc.get("/v3/api-docs").andReturn().response.contentAsString

        assertTrue(body.contains("\"/api/v1/auth/login\""), body.take(300))
        assertTrue(body.contains("\"/api/v1/verifications\""), body.take(300))
        assertTrue(body.contains("bearerAuth"), body.take(300))
    }

    @Test
    fun `error codes from ApiErrors are listed per status`() {
        val root = objectMapper.readTree(mockMvc.get("/v3/api-docs").andReturn().response.contentAsString)
        val apply = root.at("/paths/~1api~1v1~1recruitments~1{recruitmentId}~1applications/post/responses")

        assertTrue(apply.has("201"), apply.toString())
        assertFalse(apply.has("200"), apply.toString())
        assertTrue(apply.at("/409/description").asText().contains("ALREADY_APPLIED"), apply.toString())
        assertTrue(apply.at("/409/description").asText().contains("RECRUITMENT_FULL"), apply.toString())
        assertTrue(apply.at("/403/description").asText().contains("CLASS_MISMATCH"), apply.toString())
        assertTrue(apply.at("/403/description").asText().contains("FORBIDDEN"), apply.toString())
        assertTrue(apply.at("/400/description").asText().contains("VALIDATION_ERROR"), apply.toString())
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
