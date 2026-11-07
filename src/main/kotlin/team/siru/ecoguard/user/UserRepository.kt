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

    @Query("select u.id from User u where u.role = :role")
    fun findIdsByRole(@Param("role") role: Role): List<Long>

    @Query("select u.id from User u where u.role = :role and u.grade = :grade and u.classNo = :classNo")
    fun findIdsByRoleAndClass(@Param("role") role: Role, @Param("grade") grade: Int, @Param("classNo") classNo: Int): List<Long>
    fun findByNameContainingOrStudentNumberContaining(name: String, studentNumber: String): List<User>
}
