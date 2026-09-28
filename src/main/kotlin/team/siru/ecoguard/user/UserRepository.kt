package team.siru.ecoguard.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByGsmAccountId(gsmAccountId: Long): User?
    fun findByNameContainingOrStudentNumberContaining(name: String, studentNumber: String): List<User>
}
