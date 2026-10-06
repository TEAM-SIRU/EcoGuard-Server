package team.siru.ecoguard.user

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.siru.ecoguard.cleaningarea.AssignmentRepository
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.recruitment.RecruitmentApplicationRepository
import team.siru.ecoguard.user.dto.MyProfileResponse

private const val WITHDRAWN_NAME = "탈퇴한 사용자"

@Service
class UserService(
    private val userRepository: UserRepository,
    private val assignmentRepository: AssignmentRepository,
    private val applicationRepository: RecruitmentApplicationRepository,
) {

    @Transactional(readOnly = true)
    fun getMyProfile(userId: Long): MyProfileResponse {
        val user = userRepository.findById(userId).orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }
        return MyProfileResponse.from(user)
    }

    /**
     * 회원 탈퇴. 개인정보(이름, 이메일, 학번, 학년/반)를 익명화하고 모든 토큰을 무효화한다.
     * 인증/이의신청/봉사 시간 기록은 학교 기록이라 그대로 남기고, 청소 구역 배정과 모집 신청은 정원을 비우기 위해 지운다.
     * dataGSM 계정 ID 를 음수(-사용자 ID)로 바꿔 두므로, 같은 계정으로 다시 로그인하면 새 사용자로 가입된다.
     */
    @Transactional
    fun withdraw(userId: Long) {
        val user = userRepository.findById(userId).orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }

        assignmentRepository.deleteByStudentId(userId)
        applicationRepository.deleteByStudentId(userId)

        user.gsmAccountId = -user.id
        user.email = "withdrawn-${user.id}@withdrawn.invalid"
        user.name = WITHDRAWN_NAME
        user.studentNumber = null
        user.grade = null
        user.classNo = null
        user.tokensValidAfter = System.currentTimeMillis()
    }
}
