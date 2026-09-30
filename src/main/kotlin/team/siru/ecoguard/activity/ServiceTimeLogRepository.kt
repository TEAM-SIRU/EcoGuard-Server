package team.siru.ecoguard.activity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface ServiceTimeLogRepository : JpaRepository<ServiceTimeLog, Long> {
    fun findByStudentIdOrderByDateDesc(studentId: Long): List<ServiceTimeLog>
    fun findByStudentIdAndDateBetween(studentId: Long, from: LocalDate, to: LocalDate): List<ServiceTimeLog>

    @Query("select coalesce(sum(l.minutes), 0) from ServiceTimeLog l where l.student.id = :studentId")
    fun sumMinutesByStudentId(@Param("studentId") studentId: Long): Long
}
