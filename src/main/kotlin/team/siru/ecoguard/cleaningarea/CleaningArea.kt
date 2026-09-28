package team.siru.ecoguard.cleaningarea

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import team.siru.ecoguard.common.BaseEntity

@Entity
@Table(name = "cleaning_areas")
class CleaningArea(
    @Column(nullable = false, unique = true)
    var zoneCode: String,

    @Column(nullable = false)
    var name: String,

    var description: String? = null,

    var cleanTime: String? = null,

    @Column(nullable = false)
    var x: Double = 0.0,

    @Column(nullable = false)
    var y: Double = 0.0,

    @Column(nullable = false)
    var isActive: Boolean = false,

    /**
     * AI 서버 쪽에 해당 구역의 인식 모델이 준비되었는지 여부.
     * 명세서에는 이 값을 설정하는 API가 없어 기본값을 true로 두되,
     * 운영자가 필요 시 DB에서 직접 관리해야 한다.
     */
    @Column(nullable = false)
    var modelReady: Boolean = true,
) : BaseEntity()
