package team.siru.ecoguard.cleaningarea

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 기존 학교 청소 구역이 어느 학기에 있던 구역인지. 1차 운영 범위는 1학기 + 2학기 구역을 합친 범위다.
 */
@Schema(description = "구역의 학기 구분 · `COMMON`: 1·2학기 공통 · `FIRST`: 1학기에만 · `SECOND`: 2학기에만 (현재 모든 구역은 COMMON)")
enum class CleaningAreaSemester {
    /** 1·2학기 공통 구역 */
    COMMON,

    /** 1학기에만 있던 구역 */
    FIRST,

    /** 2학기에만 있던 구역 */
    SECOND,
}
