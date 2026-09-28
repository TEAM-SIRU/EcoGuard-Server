package team.siru.ecoguard.auth

import team.siru.ecoguard.user.Role

data class GsmUserInfo(
    val gsmAccountId: Long,
    val email: String,
    val name: String,
    val role: Role,
    val studentNumber: String?,
    val grade: Int?,
    val classNo: Int?,
)
