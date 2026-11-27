package team.siru.ecoguard.verification

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "인증 상태 · `PROCESSING`: 제출 직후 AI 검수 중 · `APPROVED`: 승인(봉사시간 10분 적립) · " +
        "`MANUAL_REVIEW`: AI 가 통과시키지 못했거나 검수 오류로 교사가 확인 중 · `REJECTED`: 교사가 반려(이의신청 가능). " +
        "흐름: PROCESSING → APPROVED 또는 MANUAL_REVIEW → (교사) APPROVED / REJECTED → (이의신청 승인 시) APPROVED. " +
        "AI 가 바로 REJECTED 로 만드는 경우는 없다",
)
enum class VerificationStatus {
    PROCESSING,
    APPROVED,
    REJECTED,
    MANUAL_REVIEW,
}
