package team.siru.ecoguard.recruitment

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface RecruitmentRepository : JpaRepository<Recruitment, Long> {
    fun findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
        start: LocalDateTime,
        end: LocalDateTime,
    ): Recruitment?
}
