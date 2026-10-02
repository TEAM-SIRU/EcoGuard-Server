package team.siru.ecoguard.appeal

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AppealRepository : JpaRepository<Appeal, Long> {
    fun findByStatusOrderByCreatedAtAsc(status: AppealStatus): List<Appeal>
    fun findAllByOrderByCreatedAtAscIdAsc(): List<Appeal>
    fun findByStudentIdOrderByCreatedAtDescIdDesc(studentId: Long): List<Appeal>
    fun countByVerificationId(verificationId: Long): Long
    /** 처리 중복(봉사시간 이중 적립)을 막기 위해 결정 시점에 이의신청 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appeal a where a.id = :id")
    fun findWithLockById(@Param("id") id: Long): Appeal?

    fun existsByVerificationIdAndStatus(verificationId: Long, status: AppealStatus): Boolean
}
