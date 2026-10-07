package team.siru.ecoguard.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByGsmAccountId(gsmAccountId: Long): User?
    fun findByRole(role: Role): List<User>
    fun findByNameContainingOrStudentNumberContaining(name: String, studentNumber: String): List<User>
}
