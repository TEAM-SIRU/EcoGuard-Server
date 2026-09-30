package team.siru.ecoguard.common.security

import org.springframework.security.core.context.SecurityContextHolder
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.user.Role

object SecurityUtils {

    fun currentUserId(): Long {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
        return authentication.principal as? Long ?: throw BusinessException(ErrorCode.UNAUTHORIZED)
    }

    fun hasRole(role: Role): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication ?: return false
        return authentication.authorities.any { it.authority == "ROLE_${role.name}" }
    }
}
