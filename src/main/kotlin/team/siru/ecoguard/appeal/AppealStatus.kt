package team.siru.ecoguard.appeal

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "이의신청 상태 · `PENDING`: 교사 검토 중 · `APPROVED`: 승인(인증 승인 + 10분 적립) · `REJECTED`: 반려")
enum class AppealStatus {
    PENDING,
    APPROVED,
    REJECTED,
}
