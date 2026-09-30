package team.siru.ecoguard.recruitment

import org.springframework.data.jpa.repository.JpaRepository

interface RecruitmentApplicationRepository : JpaRepository<RecruitmentApplication, Long> {
    fun existsByRecruitmentIdAndStudentId(recruitmentId: Long, studentId: Long): Boolean
    fun countByRecruitmentId(recruitmentId: Long): Long
    fun countByRecruitmentIdAndIdLessThanEqual(recruitmentId: Long, id: Long): Long
    fun findByRecruitmentIdOrderByCreatedAtAscIdAsc(recruitmentId: Long): List<RecruitmentApplication>
    fun findFirstByStudentIdOrderByCreatedAtDesc(studentId: Long): RecruitmentApplication?
}
