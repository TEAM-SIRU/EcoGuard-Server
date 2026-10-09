package team.siru.ecoguard.verification

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalDateTime

interface VerificationRepository : JpaRepository<Verification, Long> {
    /** 검수 결과 반영·수동 승인·이의신청처럼 인증 상태를 바꾸거나 판단 근거로 쓰는 곳에서 동시 처리를 막기 위해 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Verification v where v.id = :id")
    fun findWithLockById(@Param("id") id: Long): Verification?

    /** 학생들의 가장 최근 인증(날짜 기준)을 한 번에 가져온다. */
    @Query(
        "select v from Verification v where v.student.id in :studentIds and v.verificationDate = " +
            "(select max(v2.verificationDate) from Verification v2 where v2.student.id = v.student.id)",
    )
    fun findLatestByStudentIdIn(@Param("studentIds") studentIds: Collection<Long>): List<Verification>

    @Query(
        "select v.student.id, count(v) from Verification v " +
            "where v.student.id in :studentIds and v.status = :status group by v.student.id",
    )
    fun countByStudentIdInAndStatus(
        @Param("studentIds") studentIds: Collection<Long>,
        @Param("status") status: VerificationStatus,
    ): List<Array<Any>>

    fun existsByStudentIdAndVerificationDate(studentId: Long, verificationDate: LocalDate): Boolean
    fun findByStudentIdAndIdempotencyKey(studentId: Long, idempotencyKey: String): Verification?
    fun findByStudentIdAndVerificationDate(studentId: Long, verificationDate: LocalDate): Verification?
    fun findByStudentIdOrderByVerificationDateDesc(studentId: Long): List<Verification>
    /** 수동 검토 목록은 학생·구역 이름을 보여 주므로 한 번에 가져온다. */
    @EntityGraph(attributePaths = ["student", "area"])
    fun findByStatusOrderByCreatedAtAsc(status: VerificationStatus): List<Verification>
    fun findByStatusAndCreatedAtBefore(status: VerificationStatus, createdAt: LocalDateTime): List<Verification>

    /**
     * 아직 [from] 상태인 건만 조건부로 바꾼다. 검수 결과 반영(행 잠금)과 동시에 일어나도, 이미 처리된 건은 덮어쓰지 않는다.
     * 바뀐 행 수를 돌려준다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update Verification v set v.status = :to, v.manualReviewReason = :reason, v.updatedAt = :now " +
            "where v.id in :ids and v.status = :from",
    )
    fun transitionStatus(
        @Param("ids") ids: Collection<Long>,
        @Param("from") from: VerificationStatus,
        @Param("to") to: VerificationStatus,
        @Param("reason") reason: String,
        @Param("now") now: LocalDateTime,
    ): Int
    fun findByStudentIdAndVerificationDateBetweenOrderByVerificationDateAsc(
        studentId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<Verification>
    fun findFirstByStudentIdOrderByVerificationDateAsc(studentId: Long): Verification?
    fun countByStudentIdAndStatus(studentId: Long, status: VerificationStatus): Long
}
