package team.siru.ecoguard.user

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.user.dto.MyProfileResponse

@Service
class UserService(
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    fun getMyProfile(userId: Long): MyProfileResponse {
        val user = userRepository.findById(userId).orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }
        return MyProfileResponse.from(user)
    }
}
