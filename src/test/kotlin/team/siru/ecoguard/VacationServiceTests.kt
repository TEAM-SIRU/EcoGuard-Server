package team.siru.ecoguard

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import team.siru.ecoguard.schoolcalendar.NeisProperties
import team.siru.ecoguard.schoolcalendar.VacationService
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VacationServiceTests {

    private val zone = ZoneId.of("Asia/Seoul")
    private val clock = Clock.fixed(Instant.parse("2026-07-30T00:00:00Z"), zone)

    private fun newService(
        apiKey: String = "neis-key",
        officeCode: String = "F10",
        schoolCode: String = "1234567",
    ): Pair<VacationService, MockRestServiceServer> {
        val builder = RestClient.builder().baseUrl("http://neis.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val properties = NeisProperties(apiKey = apiKey, educationOfficeCode = officeCode, schoolCode = schoolCode)
        return VacationService(builder.build(), properties, JsonMapper.builder().build(), clock) to server
    }

    private fun scheduleBody(vararg events: Pair<String, String>): String {
        val rows = events.joinToString(",") { (date, name) -> """{"AA_YMD":"$date","EVENT_NM":"$name"}""" }
        return """{"SchoolSchedule":[{"head":[{"list_total_count":${events.size}},{"RESULT":{"CODE":"INFO-000"}}]},{"row":[$rows]}]}"""
    }

    private val summerBody = scheduleBody(
        "20260901" to "개학식",
        "20260724" to "여름방학식",
        "20261224" to "겨울방학식",
        "20270302" to "입학식 및 개학식",
    )

    @Test
    fun `days between the break ceremony and the new term are vacation`() {
        val (service, server) = newService()
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://neis.test/hub/SchoolSchedule")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(queryParam("KEY", "neis-key"))
            .andExpect(queryParam("ATPT_OFCDC_SC_CODE", "F10"))
            .andExpect(queryParam("SD_SCHUL_CODE", "1234567"))
            .andRespond(withSuccess(summerBody, MediaType.APPLICATION_JSON))

        assertFalse(service.isVacation(LocalDate.of(2026, 7, 24)), "방학식 당일은 등교일")
        assertTrue(service.isVacation(LocalDate.of(2026, 7, 25)))
        assertTrue(service.isVacation(LocalDate.of(2026, 8, 15)))
        assertTrue(service.isVacation(LocalDate.of(2026, 8, 31)))
        assertFalse(service.isVacation(LocalDate.of(2026, 9, 1)), "개학식 당일은 등교일")
        assertTrue(service.isVacation(LocalDate.of(2027, 1, 20)), "겨울방학은 해를 넘겨 이어진다")
        assertFalse(service.isVacation(LocalDate.of(2027, 3, 2)))
        server.verify()
    }

    @Test
    fun `a break ceremony without a following term start is ignored`() {
        val periods = VacationService.toVacationPeriods(
            listOf(LocalDate.of(2026, 12, 24) to "겨울방학식"),
        )
        assertEquals(emptyList(), periods)
    }

    @Test
    fun `the schedule is fetched once and cached`() {
        val (service, server) = newService()
        server.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.startsWith("http://neis.test/hub/SchoolSchedule")))
            .andRespond(withSuccess(summerBody, MediaType.APPLICATION_JSON))

        repeat(5) { service.isVacation(LocalDate.of(2026, 8, 1)) }

        server.verify()
    }

    @Test
    fun `a failing NEIS call does not block submissions`() {
        val (service, server) = newService()
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://neis.test/hub/SchoolSchedule")))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertFalse(service.isVacation(LocalDate.of(2026, 8, 1)))
        server.verify()
    }

    @Test
    fun `an error result from NEIS does not block submissions`() {
        val (service, server) = newService()
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://neis.test/hub/SchoolSchedule")))
            .andRespond(withSuccess("""{"RESULT":{"CODE":"INFO-300","MESSAGE":"인증키가 유효하지 않습니다."}}""", MediaType.APPLICATION_JSON))

        assertFalse(service.isVacation(LocalDate.of(2026, 8, 1)))
        server.verify()
    }

    @Test
    fun `no data from NEIS means no vacation`() {
        val (service, server) = newService()
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://neis.test/hub/SchoolSchedule")))
            .andRespond(withSuccess("""{"RESULT":{"CODE":"INFO-200","MESSAGE":"해당하는 데이터가 없습니다."}}""", MediaType.APPLICATION_JSON))

        assertFalse(service.isVacation(LocalDate.of(2026, 8, 1)))
        server.verify()
    }

    @Test
    fun `without a key or school code NEIS is never called`() {
        listOf(newService(apiKey = ""), newService(officeCode = ""), newService(schoolCode = "")).forEach { (service, server) ->
            assertFalse(service.isVacation(LocalDate.of(2026, 8, 1)))
            server.verify()
        }
    }
}
