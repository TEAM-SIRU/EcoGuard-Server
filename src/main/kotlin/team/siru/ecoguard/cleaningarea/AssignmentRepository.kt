package team.siru.ecoguard.cleaningarea

import org.springframework.data.jpa.repository.JpaRepository

interface AssignmentRepository : JpaRepository<Assignment, Long> {
    fun findFirstByStudentIdOrderByCreatedAtDesc(studentId: Long): Assignment?
}
