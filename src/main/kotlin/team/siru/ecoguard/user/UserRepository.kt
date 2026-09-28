package team.siru.ecoguard.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByStudentNumber(studentNumber: String): User?
    fun existsByNameContainingOrStudentNumberContaining(name: String, studentNumber: String): Boolean
    fun findByNameContainingOrStudentNumberContaining(name: String, studentNumber: String): List<User>
}
