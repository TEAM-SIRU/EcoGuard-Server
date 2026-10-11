package team.siru.ecoguard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import team.siru.ecoguard.common.config.SwaggerProperties
import tools.jackson.databind.ObjectMapper

private const val DOCS_USER = "docs"
private const val DOCS_PASSWORD = "docs-password-1234"

class SwaggerPropertiesTests {

    @Test
    fun `docs can stay off without any credentials`() {
        SwaggerProperties(enabled = false)
    }

    @Test
    fun `turning docs on without a username or a long enough password stops the server from starting`() {
        assertThrows<IllegalStateException> { SwaggerProperties(enabled = true, username = "", password = DOCS_PASSWORD) }
        assertThrows<IllegalStateException> { SwaggerProperties(enabled = true, username = DOCS_USER, password = "short") }
        assertThrows<IllegalStateException> { SwaggerProperties(enabled = true, username = DOCS_USER, password = "") }
    }

    @Test
    fun `docs turn on with a username and a long password`() {
        SwaggerProperties(enabled = true, username = DOCS_USER, password = DOCS_PASSWORD)
    }
}

@SpringBootTest(
    properties = ["swagger.enabled=true", "swagger.username=$DOCS_USER", "swagger.password=$DOCS_PASSWORD"],
)
@AutoConfigureMockMvc
class OpenApiDocsTests @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) {

    private fun docs(user: String = DOCS_USER, password: String = DOCS_PASSWORD) =
        mockMvc.get("/v3/api-docs") { headers { setBasicAuth(user, password) } }.andReturn().response

    @Test
    fun `api docs ask for a password`() {
        assertEquals(401, mockMvc.get("/v3/api-docs").andReturn().response.status)
        assertEquals(401, mockMvc.get("/swagger-ui.html").andReturn().response.status)
    }

    @Test
    fun `a wrong password does not open the docs`() {
        assertEquals(401, docs(password = "not-the-password").status)
        assertEquals(401, docs(user = "someone-else").status)
    }

    @Test
    fun `the docs password does not open the regular api`() {
        val response = mockMvc.get("/api/v1/users/me") { headers { setBasicAuth(DOCS_USER, DOCS_PASSWORD) } }
            .andReturn().response

        assertEquals(401, response.status)
    }

    @Test
    fun `api docs list the public endpoints with the password`() {
        val response = docs()
        val body = response.contentAsString

        assertEquals(200, response.status)
        assertTrue(body.contains("\"/api/v1/auth/login\""), body.take(300))
        assertTrue(body.contains("\"/api/v1/verifications\""), body.take(300))
        assertTrue(body.contains("bearerAuth"), body.take(300))
    }

    @Test
    fun `error codes from ApiErrors are listed per status`() {
        val root = objectMapper.readTree(docs().contentAsString)
        val apply = root.at("/paths/~1api~1v1~1recruitments~1{recruitmentId}~1applications/post/responses")

        assertTrue(apply.has("201"), apply.toString())
        assertFalse(apply.has("200"), apply.toString())
        assertTrue(apply.at("/409/description").asText().contains("ALREADY_APPLIED"), apply.toString())
        assertTrue(apply.at("/409/description").asText().contains("RECRUITMENT_FULL"), apply.toString())
        assertTrue(apply.at("/403/description").asText().contains("CLASS_MISMATCH"), apply.toString())
        assertTrue(apply.at("/403/description").asText().contains("FORBIDDEN"), apply.toString())
        assertTrue(apply.at("/400/description").asText().contains("VALIDATION_ERROR"), apply.toString())
    }

    @Test
    fun `descriptions and examples match what the server really returns`() {
        val body = docs().contentAsString
        val root = objectMapper.readTree(body)
        val apply = root.at("/paths/~1api~1v1~1recruitments~1{recruitmentId}~1applications/post/responses")
        val applyResponse = root.at("/components/schemas/ApplyResponse/properties")

        assertEquals("생성 성공", apply.at("/201/description").asString())
        assertTrue(apply.at("/201/content").has("application/json"), apply.toString())
        assertFalse(body.contains("\"*/*\""), "응답 형식이 */* 로 표시됩니다")
        assertEquals("APPROVED", applyResponse.at("/status/example").asString())
        assertEquals("2026-10-07T07:30:00", applyResponse.at("/appliedAt/example").asString())
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

        assertEquals(401, response.status)
        assertFalse(response.contentAsString.contains("openapi"))
    }

    @Test
    fun `docs credentials do nothing while docs are off`() {
        val response = mockMvc.get("/v3/api-docs") { headers { setBasicAuth(DOCS_USER, DOCS_PASSWORD) } }
            .andReturn().response

        assertEquals(401, response.status)
    }
}
