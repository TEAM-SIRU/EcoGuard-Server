package team.siru.ecoguard.cleaningarea

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 학교 도면(청소구역) 초기 데이터. application.yaml의 cleaning-area-seed.areas 에
 * 학교 실제 구역을 정의해두면 서버 시작 시 자동으로 DB에 채워진다 (zoneCode 기준 중복 방지).
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
    val x: Double = 0.0,
    val y: Double = 0.0,
    val active: Boolean = false,
    val modelReady: Boolean = true,
)
