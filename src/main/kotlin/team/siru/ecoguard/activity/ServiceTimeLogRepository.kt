package team.siru.ecoguard.activity

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ServiceTimeLogRepository : JpaRepository<ServiceTimeLog, Long> {
    fun findByStudentIdOrderByDateDesc(studentId: Long): List<ServiceTimeLog>

    @Query("select coalesce(sum(l.minutes), 0) from ServiceTimeLog l where l.student.id = :studentId")
    fun sumMinutesByStudentId(@Param("studentId") studentId: Long): Long
}
