package team.siru.ecoguard.cleaningarea

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 학교 도면(청소구역) 초기 데이터. application.yaml의 cleaning-area-seed.areas 에
 * 학교 실제 구역을 정의해두면 서버 시작 시 자동으로 DB에 채워진다 (zoneCode 기준 중복 방지).
 * 이미 있는 구역은 활성 여부(교사가 API로 관리)를 제외한 나머지 값이 설정값으로 갱신된다.
 */
@ConfigurationProperties(prefix = "cleaning-area-seed")
data class CleaningAreaSeedProperties(
    val areas: List<CleaningAreaSeedItem> = emptyList(),
)

data class CleaningAreaSeedItem(
    val zoneCode: String,
    val name: String,
    val description: String? = null,
    val cleanTime: String? = null,
    val semester: CleaningAreaSemester = CleaningAreaSemester.COMMON,
    /** 최초 생성 시에만 적용된다. 이후 활성 여부는 교사가 API로 관리한다. */
    val active: Boolean = false,
    /** AI 모델 학습이 끝난 구역만 true로 바꾼다. 준비되지 않은 구역은 활성화할 수 없다. */
    val modelReady: Boolean = false,
)
