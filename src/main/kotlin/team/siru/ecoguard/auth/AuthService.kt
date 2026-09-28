package team.siru.ecoguard.auth

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.siru.ecoguard.auth.dto.LoginResponse
import team.siru.ecoguard.auth.dto.UserSummaryResponse
import team.siru.ecoguard.common.security.JwtTokenProvider
import team.siru.ecoguard.user.User
import team.siru.ecoguard.user.UserRepository

@Service
class AuthService(
    private val gsmOAuthClient: GsmOAuthClient,
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
) {

    @Transactional
    fun login(authCode: String): LoginResponse {
        val userInfo = gsmOAuthClient.authenticate(authCode)

        val user = userRepository.findByStudentNumber(userInfo.studentNumber)
            ?.apply {
                name = userInfo.name
                role = userInfo.role
                grade = userInfo.grade
                classNo = userInfo.classNo
            }
            ?: userRepository.save(
                User(
                    studentNumber = userInfo.studentNumber,
                    name = userInfo.name,
                    role = userInfo.role,
                    grade = userInfo.grade,
                    classNo = userInfo.classNo,
                ),
            )

        val tokenPair = jwtTokenProvider.generateTokenPair(user.id, user.role)
        return LoginResponse(tokenPair.accessToken, tokenPair.refreshToken, UserSummaryResponse.from(user))
    }

    fun logout() {
        // 서버는 상태를 저장하지 않는 JWT를 사용하므로 별도의 무효화 처리가 없다.
        // 추후 리프레시 토큰 블랙리스트가 도입되면 이 지점에서 처리한다.
    }
}
