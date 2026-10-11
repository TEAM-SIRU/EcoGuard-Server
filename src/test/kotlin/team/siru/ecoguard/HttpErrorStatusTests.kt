package team.siru.ecoguard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = ["TEACHER"])
class HttpErrorStatusTests @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) {

    private fun code(body: String) = objectMapper.readTree(body).get("code").asString()

    @Test
    fun `a request method the path does not support is a 405, not a server error`() {
        val response = mockMvc.delete("/api/v1/recruitments").andReturn().response

        assertEquals(405, response.status)
        assertEquals("METHOD_NOT_ALLOWED", code(response.contentAsString))
        assertTrue(response.getHeader("Allow")!!.contains("POST"), response.getHeader("Allow"))
    }

    @Test
    fun `a body in an unsupported content type is a 415, not a server error`() {
        val response = mockMvc.post("/api/v1/recruitments") {
            contentType = MediaType.TEXT_PLAIN
            content = "hello"
        }.andReturn().response

        assertEquals(415, response.status)
        assertEquals("UNSUPPORTED_MEDIA_TYPE", code(response.contentAsString))
    }

    @Test
    fun `an unknown path is still a 404`() {
        val response = mockMvc.get("/api/v1/does-not-exist").andReturn().response

        assertEquals(404, response.status)
        assertEquals("NOT_FOUND", code(response.contentAsString))
    }
}
