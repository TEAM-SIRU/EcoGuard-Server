package team.siru.ecoguard.appeal

import org.springframework.data.jpa.repository.JpaRepository

interface AppealRepository : JpaRepository<Appeal, Long> {
    fun findByStatusOrderByCreatedAtAsc(status: AppealStatus): List<Appeal>
    fun findAllByOrderByCreatedAtAscIdAsc(): List<Appeal>
    fun findByStudentIdOrderByCreatedAtDescIdDesc(studentId: Long): List<Appeal>
    fun countByVerificationId(verificationId: Long): Long
    fun existsByVerificationIdAndStatus(verificationId: Long, status: AppealStatus): Boolean
}
