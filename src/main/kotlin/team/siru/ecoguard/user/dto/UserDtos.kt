package team.siru.ecoguard.user.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.siru.ecoguard.user.Role
import team.siru.ecoguard.user.User

@Schema(description = "내 정보")
data class MyProfileResponse(
    @field:Schema(description = "EcoGuard 사용자 ID", example = "12")
    val userId: Long,
    @field:Schema(description = "이름", example = "홍길동")
    val name: String,
    @field:Schema(description = "역할")
    val role: Role,
    /** 학생 계정에만 있다. */
    @field:Schema(description = "학번(학년·반·번호 4자리). 학생만 있고 교사는 null", example = "2104", nullable = true)
    val studentNumber: String?,
    @field:Schema(description = "학년. 학생만 있고 교사는 null", example = "2", nullable = true)
    val grade: Int?,
    @field:Schema(description = "반. 학생만 있고 교사는 null", example = "1", nullable = true)
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
