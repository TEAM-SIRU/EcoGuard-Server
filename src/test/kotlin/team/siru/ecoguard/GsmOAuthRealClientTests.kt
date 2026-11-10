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
import team.siru.ecoguard.auth.RestClientDataGsmHttpClient
import team.themoment.datagsm.sdk.oauth.DataGsmOAuthClient
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

    /** 토큰 교환과 사용자 정보 조회가 한 RestClient를 쓰므로 가짜 서버 하나로 순서대로 응답한다. */
    private fun newFixture(): Fixture {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val sdk = DataGsmOAuthClient.builder(properties.clientId, properties.clientSecret)
            .authorizationBaseUrl("http://authorization.test")
            .userInfoBaseUrl("http://resource.test")
            .httpClient(RestClientDataGsmHttpClient(builder.build()))
            .build()
        return Fixture(GsmOAuthRealClient(sdk, properties), server, server)
    }

    @Test
    fun `student account logs in as STUDENT with class info`() {
        val f = newFixture()
        f.authorization.expectToken()
        f.resource.expect(requestTo("http://resource.test/userinfo"))
            .andRespond(
                withSuccess(
                    """{"id":3,"email":"s@gsm.hs.kr","objectType":"STUDENT",
                       "student":{"name":"김학생","grade":1,"classNum":2,"number":5,"studentNumber":1205}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val user = f.client.authenticate("code", null)

        assertEquals(Role.STUDENT, user.role)
        assertEquals(3L, user.gsmAccountId)
        assertEquals("김학생", user.name)
        assertEquals("1205", user.studentNumber)
        assertEquals(1, user.grade)
        assertEquals(2, user.classNo)
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
