package team.siru.ecoguard.verification

import java.time.LocalTime

object CleaningTimeWindow {

    private val DEFAULT_START: LocalTime = LocalTime.of(8, 0)
    private val DEFAULT_END: LocalTime = LocalTime.of(8, 10)

    fun isWithin(cleanTime: String?, now: LocalTime): Boolean {
        val (start, end) = parse(cleanTime)
        return !now.isBefore(start) && !now.isAfter(end)
    }

    private fun parse(cleanTime: String?): Pair<LocalTime, LocalTime> {
        val parts = cleanTime?.split("~")
        if (parts == null || parts.size != 2) {
            return DEFAULT_START to DEFAULT_END
        }
        val start = runCatching { LocalTime.parse(parts[0].trim()) }.getOrDefault(DEFAULT_START)
        val end = runCatching { LocalTime.parse(parts[1].trim()) }.getOrDefault(DEFAULT_END)
        return start to end
    }
}
