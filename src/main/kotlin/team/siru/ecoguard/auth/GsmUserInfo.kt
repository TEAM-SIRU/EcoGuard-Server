package team.siru.ecoguard.auth

import team.siru.ecoguard.user.Role

data class GsmUserInfo(
    val studentNumber: String,
    val name: String,
    val role: Role,
    val grade: Int?,
    val classNo: Int?,
)
