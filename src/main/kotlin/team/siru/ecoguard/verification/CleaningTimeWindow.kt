package team.siru.ecoguard.verification

import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

object CleaningTimeWindow {

    private val log = LoggerFactory.getLogger(CleaningTimeWindow::class.java)

    private val DEFAULT_START: LocalTime = LocalTime.of(7, 20)
    private val DEFAULT_END: LocalTime = LocalTime.of(8, 10)

    /** 인증은 평일(월~금)에만 가능하다. 공휴일은 아직 반영하지 않는다. */
    fun isCertificationDay(date: LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY

    fun isWithin(cleanTime: String?, now: LocalTime): Boolean {
        val (start, end) = parse(cleanTime)
        return !now.isBefore(start) && !now.isAfter(end)
    }

    /** 해당 날짜의 인증 가능 시간이 이미 지났는지 여부 */
    fun hasEnded(cleanTime: String?, now: LocalTime): Boolean = now.isAfter(parse(cleanTime).second)

    /** 인증 가능 시간(시작, 끝). 값이 없거나 형식이 잘못되면 기본 시간을 돌려준다. */
    fun parse(cleanTime: String?): Pair<LocalTime, LocalTime> {
        val parts = cleanTime?.split("~")
        if (parts == null || parts.size != 2) {
            // 값이 아예 없으면 기본 시간을 쓰지만, 형식이 잘못된 경우는 설정 오류이므로 남겨 둔다.
            if (cleanTime != null) log.warn("청소 시간 형식이 올바르지 않아 기본값을 사용합니다: '{}'", cleanTime)
            return DEFAULT_START to DEFAULT_END
        }
        val start = runCatching { LocalTime.parse(parts[0].trim()) }.getOrElse {
            log.warn("청소 시작 시간을 해석할 수 없어 기본값을 사용합니다: '{}'", cleanTime)
            DEFAULT_START
        }
        val end = runCatching { LocalTime.parse(parts[1].trim()) }.getOrElse {
            log.warn("청소 종료 시간을 해석할 수 없어 기본값을 사용합니다: '{}'", cleanTime)
            DEFAULT_END
        }
        return start to end
    }
}
