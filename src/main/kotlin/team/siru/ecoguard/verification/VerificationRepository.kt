package team.siru.ecoguard.verification

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface VerificationRepository : JpaRepository<Verification, Long> {
    fun existsByStudentIdAndVerificationDate(studentId: Long, verificationDate: LocalDate): Boolean
    fun findByStudentIdOrderByVerificationDateDesc(studentId: Long): List<Verification>
    fun findByStatusOrderByCreatedAtAsc(status: VerificationStatus): List<Verification>
    fun findByStudentIdAndVerificationDateBetweenOrderByVerificationDateAsc(
        studentId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<Verification>
    fun findFirstByStudentIdOrderByVerificationDateAsc(studentId: Long): Verification?
    fun countByStudentIdAndStatus(studentId: Long, status: VerificationStatus): Long
}
