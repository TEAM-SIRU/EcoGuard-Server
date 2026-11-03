package team.siru.ecoguard.user

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository : JpaRepository<User, Long> {
    /** 같은 학생에 대한 동시 요청(더블클릭 등)을 직렬화하기 위해 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    fun findWithLockById(@Param("id") id: Long): User?

    fun findByGsmAccountId(gsmAccountId: Long): User?
    fun findByRole(role: Role): List<User>
    fun findByNameContainingOrStudentNumberContaining(name: String, studentNumber: String): List<User>
}
