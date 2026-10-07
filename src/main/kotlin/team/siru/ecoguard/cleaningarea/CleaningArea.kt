package team.siru.ecoguard.cleaningarea

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var semester: CleaningAreaSemester = CleaningAreaSemester.COMMON,

    @Column(nullable = false)
    var isActive: Boolean = false,

    /**
     * AI 서버 쪽에 해당 구역의 인식 모델이 준비되었는지 여부.
     * 명세서에는 이 값을 설정하는 API가 없어 application.yaml의 cleaning-area-seed 에서
     * 관리하며, 서버 시작 시 CleaningAreaSeeder가 DB에 동기화한다.
     */
    @Column(nullable = false)
    var modelReady: Boolean = true,
) : BaseEntity()
