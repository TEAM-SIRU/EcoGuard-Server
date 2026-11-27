package team.siru.ecoguard.user

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "역할 · `STUDENT`: 학생(앱) · `TEACHER`: 교사(웹). dataGSM 계정 정보로 정해진다")
enum class Role {
    STUDENT,
    TEACHER,
}
