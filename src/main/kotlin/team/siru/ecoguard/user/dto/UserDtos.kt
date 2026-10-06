package team.siru.ecoguard.user.dto

import team.siru.ecoguard.user.Role
import team.siru.ecoguard.user.User

data class MyProfileResponse(
    val userId: Long,
    val name: String,
    val role: Role,
    /** 학생 계정에만 있다. */
    val studentNumber: String?,
    val grade: Int?,
    val classNo: Int?,
) {
    companion object {
        fun from(user: User) = MyProfileResponse(
            userId = user.id,
            name = user.name,
            role = user.role,
            studentNumber = user.studentNumber,
            grade = user.grade,
            classNo = user.classNo,
        )
    }
}
