package team.siru.ecoguard.recruitment

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "모집 신청 상태 · `APPROVED`: 확정(현재 신청은 모두 즉시 확정) · `PENDING` / `REJECTED`: 예약된 값으로 현재는 쓰이지 않음")
enum class ApplicationStatus {
    PENDING,
    APPROVED,
    REJECTED,
}
