package team.siru.ecoguard.recruitment

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RecruitmentRepository : JpaRepository<Recruitment, Long> {
    fun findByGradeAndClassNoOrderByStartDateDesc(grade: Int, classNo: Int): List<Recruitment>

    fun findAllByOrderByStartDateDescGradeAscClassNoAsc(): List<Recruitment>

    /** 선착순 신청 시 동시 신청으로 정원을 초과하지 않도록 모집 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Recruitment r where r.id = :id")
    fun findWithLockById(@Param("id") id: Long): Recruitment?
}
