package team.siru.ecoguard

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import team.siru.ecoguard.auth.GsmOAuthProperties
import team.siru.ecoguard.auth.GsmOAuthRealClient
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.user.Role
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GsmOAuthRealClientTests {

    private val properties = GsmOAuthProperties(
        clientId = "client",
        clientSecret = "secret",
        redirectUri = "https://server.test/api/v1/auth/callback",
    )

    private class Fixture(
        val client: GsmOAuthRealClient,
        val authorization: MockRestServiceServer,
        val resource: MockRestServiceServer,
    )

    private fun newFixture(): Fixture {
        val authorizationBuilder = RestClient.builder().baseUrl("http://authorization.test")
        val resourceBuilder = RestClient.builder().baseUrl("http://resource.test")
        val authorization = MockRestServiceServer.bindTo(authorizationBuilder).build()
        val resource = MockRestServiceServer.bindTo(resourceBuilder).build()
        val client = GsmOAuthRealClient(authorizationBuilder.build(), resourceBuilder.build(), properties)
        return Fixture(client, authorization, resource)
    }

    private fun MockRestServiceServer.expectToken() {
        expect(requestTo("http://authorization.test/v1/oauth/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""{"access_token":"token","token_type":"Bearer","expires_in":3600}""", MediaType.APPLICATION_JSON))
    }

    @Test
    fun `teacher account logs in as TEACHER`() {
        val f = newFixture()
        f.authorization.expectToken()
        f.resource.expect(requestTo("http://resource.test/userinfo"))
            .andExpect(header("Authorization", "Bearer token"))
            .andRespond(
                withSuccess(
                    """{"id":7,"email":"t@gsm.hs.kr","objectType":"TEACHER","teacher":{"name":"김선생","department":"GRADE"}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val user = f.client.authenticate("code", null)

        assertEquals(Role.TEACHER, user.role)
        assertEquals("김선생", user.name)
    }

    @Test
    fun `403 on token exchange is reported with the forbidden message`() {
        val f = newFixture()
        f.authorization.expect(requestTo("http://authorization.test/v1/oauth/token"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN))

        val e = assertFailsWith<BusinessException> { f.client.authenticate("code", null) }

        assertEquals(ErrorCode.OAUTH_FAILED, e.errorCode)
        assertTrue(e.message!!.contains("관리자 승인 대기"))
    }

    @Test
    fun `403 on userinfo is reported with the forbidden message`() {
        val f = newFixture()
        f.authorization.expectToken()
        f.resource.expect(requestTo("http://resource.test/userinfo"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN))

        val e = assertFailsWith<BusinessException> { f.client.authenticate("code", null) }

        assertEquals(ErrorCode.OAUTH_FAILED, e.errorCode)
        assertTrue(e.message!!.contains("관리자 승인 대기"))
    }

    @Test
    fun `other failures keep the default message`() {
        val f = newFixture()
        f.authorization.expect(requestTo("http://authorization.test/v1/oauth/token"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        val e = assertFailsWith<BusinessException> { f.client.authenticate("code", null) }

        assertEquals(ErrorCode.OAUTH_FAILED, e.errorCode)
        assertEquals(ErrorCode.OAUTH_FAILED.defaultMessage, e.message)
    }
}
