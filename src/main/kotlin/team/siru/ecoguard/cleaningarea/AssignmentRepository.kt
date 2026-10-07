package team.siru.ecoguard.cleaningarea

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AssignmentRepository : JpaRepository<Assignment, Long> {
    /** 여러 학생의 배정을 구역과 함께 최신순으로 한 번에 가져온다. 학생별 최신 배정은 호출한 쪽에서 첫 항목을 고른다. */
    @Query(
        "select a from Assignment a join fetch a.area where a.student.id in :studentIds " +
            "order by a.createdAt desc, a.id desc",
    )
    fun findAllWithAreaByStudentIdIn(@Param("studentIds") studentIds: Collection<Long>): List<Assignment>

    fun findFirstByStudentIdOrderByCreatedAtDesc(studentId: Long): Assignment?
    fun findByAreaIdOrderByCreatedAtAsc(areaId: Long): List<Assignment>
    fun findByStudentId(studentId: Long): List<Assignment>
    fun existsByStudentId(studentId: Long): Boolean
    fun deleteByStudentId(studentId: Long)
}
