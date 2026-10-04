package team.siru.ecoguard.schoolcalendar

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * NEIS 학사일정으로 방학 기간인지 판단한다.
 * 방학식 다음 날부터 개학일 전날까지를 방학으로 본다 (방학식과 개학식 당일은 등교일).
 *
 * 인증을 막는 쪽이 아니라 허용하는 쪽으로 실패한다: 키/학교 코드가 없거나 NEIS 조회에 실패하면
 * 마지막으로 성공한 결과를 쓰고, 그것도 없으면 방학이 아닌 것으로 본다.
 */
@Service
class VacationService(
    private val neisRestClient: RestClient,
    private val properties: NeisProperties,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private data class Snapshot(val periods: List<ClosedRange<LocalDate>>, val expiresAt: Instant)

    @Volatile
    private var snapshot: Snapshot? = null

    fun isVacation(date: LocalDate): Boolean {
        if (properties.apiKey.isBlank() || properties.educationOfficeCode.isBlank() || properties.schoolCode.isBlank()) {
            return false
        }
        return currentPeriods().any { date in it }
    }

    private fun currentPeriods(): List<ClosedRange<LocalDate>> {
        val now = clock.instant()
        snapshot?.takeIf { now.isBefore(it.expiresAt) }?.let { return it.periods }
        synchronized(this) {
            snapshot?.takeIf { now.isBefore(it.expiresAt) }?.let { return it.periods }
            val stale = snapshot?.periods.orEmpty()
            val refreshed = try {
                Snapshot(fetchPeriods(LocalDate.now(clock)), now.plus(SUCCESS_TTL))
            } catch (e: Exception) {
                // 요청 URL 에 키가 들어 있으므로 예외 메시지는 남기지 않는다.
                log.warn("NEIS 학사일정 조회에 실패해 방학 제한을 건너뜁니다 ({})", e.javaClass.simpleName)
                Snapshot(stale, now.plus(FAILURE_RETRY_DELAY))
            }
            snapshot = refreshed
            return refreshed.periods
        }
    }

    private fun fetchPeriods(today: LocalDate): List<ClosedRange<LocalDate>> {
        val body = neisRestClient.get()
            .uri { uri ->
                uri.path("/hub/SchoolSchedule")
                    .queryParam("KEY", properties.apiKey)
                    .queryParam("Type", "json")
                    .queryParam("pIndex", 1)
                    .queryParam("pSize", 1000)
                    .queryParam("ATPT_OFCDC_SC_CODE", properties.educationOfficeCode)
                    .queryParam("SD_SCHUL_CODE", properties.schoolCode)
                    .queryParam("AA_FROM_YMD", today.minusDays(WINDOW_DAYS).format(YMD))
                    .queryParam("AA_TO_YMD", today.plusDays(WINDOW_DAYS).format(YMD))
                    .build()
            }
            .retrieve()
            .body(String::class.java)
            ?: error("empty NEIS response")
        return toVacationPeriods(parseEvents(objectMapper.readTree(body)))
    }

    private fun parseEvents(root: JsonNode): List<Pair<LocalDate, String>> {
        val code = root.path("RESULT").path("CODE").asString("")
        if (code == NO_DATA) return emptyList()
        if (code.isNotEmpty()) error("NEIS error $code")

        val events = mutableListOf<Pair<LocalDate, String>>()
        root.path("SchoolSchedule").forEach { section ->
            section.path("row").forEach { row ->
                val date = LocalDate.parse(row.path("AA_YMD").asString(), YMD)
                events += date to row.path("EVENT_NM").asString("")
            }
        }
        if (events.isEmpty()) error("unexpected NEIS response shape")
        return events
    }

    companion object {
        private val YMD: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
        private const val NO_DATA = "INFO-200"
        private const val WINDOW_DAYS = 200L
        private val SUCCESS_TTL: Duration = Duration.ofHours(6)
        private val FAILURE_RETRY_DELAY: Duration = Duration.ofMinutes(5)

        /** 방학식 다음 날 ~ 개학일 전날. 개학 일정이 아직 없는 방학식은 끝을 알 수 없으므로 제외한다. */
        internal fun toVacationPeriods(events: List<Pair<LocalDate, String>>): List<ClosedRange<LocalDate>> {
            val sorted = events.sortedBy { it.first }
            val periods = mutableListOf<ClosedRange<LocalDate>>()
            var start: LocalDate? = null
            for ((date, name) in sorted) {
                if (start == null) {
                    if (name.contains("방학식")) start = date.plusDays(1)
                } else if (name.contains("개학")) {
                    if (!date.isBefore(start)) periods += start..date.minusDays(1)
                    start = null
                }
            }
            return periods
        }
    }
}
