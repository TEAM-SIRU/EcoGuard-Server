package team.siru.ecoguard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import team.siru.ecoguard.auth.DemoAccountAuthenticator
import team.siru.ecoguard.auth.DemoAccountProperties
import team.siru.ecoguard.user.Role
import tools.jackson.databind.ObjectMapper

class DemoAccountUnitTests {

    @Test
    fun `disabled demo account never authenticates`() {
        val authenticator = DemoAccountAuthenticator(DemoAccountProperties(enabled = false, authCode = "x".repeat(32)))

        assertNull(authenticator.authenticate("x".repeat(32)))
    }

    @Test
    fun `enabled demo account requires an exact code and always yields a student`() {
        val code = "demo-review-code-1234567890"
        val authenticator = DemoAccountAuthenticator(DemoAccountProperties(enabled = true, authCode = code))

        assertNull(authenticator.authenticate("wrong"))
        assertNull(authenticator.authenticate(""))
        assertEquals(Role.STUDENT, authenticator.authenticate(code)!!.role)
    }

    @Test
    fun `enabling the demo account with a short code fails at startup`() {
        assertThrows<IllegalStateException> { DemoAccountProperties(enabled = true, authCode = "short") }
        assertThrows<IllegalStateException> { DemoAccountProperties(enabled = true, authCode = "") }
    }
}

@SpringBootTest(properties = ["demo-account.enabled=true", "demo-account.auth-code=demo-review-code-1234567890"])
@AutoConfigureMockMvc
class DemoAccountLoginTests @Autowired constructor(
    private val mockMvc: MockMvc,
    private val objectMapper: ObjectMapper,
) {

    private fun login(code: String) = mockMvc.post("/api/v1/auth/login") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(mapOf("authCode" to code))
    }

    @Test
    fun `demo code logs in as a student who cannot use teacher apis`() {
        val body = objectMapper.readTree(
            login("demo-review-code-1234567890").andReturn().response.contentAsString,
        )
        assertEquals("STUDENT", body.get("user").get("role").asText())
        val token = body.get("accessToken").asText()

        mockMvc.get("/api/v1/notices") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }
        mockMvc.get("/api/v1/appeals") { header("Authorization", "Bearer $token") }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `a wrong code is rejected`() {
        login("demo-review-code-0000000000").andExpect { status { isUnauthorized() } }
    }
}
