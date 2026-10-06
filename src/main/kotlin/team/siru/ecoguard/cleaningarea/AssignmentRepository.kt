package team.siru.ecoguard.cleaningarea

import org.springframework.data.jpa.repository.JpaRepository

interface AssignmentRepository : JpaRepository<Assignment, Long> {
    fun findFirstByStudentIdOrderByCreatedAtDesc(studentId: Long): Assignment?
    fun findByAreaIdOrderByCreatedAtAsc(areaId: Long): List<Assignment>
    fun findByStudentId(studentId: Long): List<Assignment>
    fun existsByStudentId(studentId: Long): Boolean
    fun deleteByStudentId(studentId: Long)
}
