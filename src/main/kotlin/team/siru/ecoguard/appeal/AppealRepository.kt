package team.siru.ecoguard.appeal

import org.springframework.data.jpa.repository.JpaRepository

interface AppealRepository : JpaRepository<Appeal, Long> {
    fun findByStatusOrderByCreatedAtAsc(status: AppealStatus): List<Appeal>
}
