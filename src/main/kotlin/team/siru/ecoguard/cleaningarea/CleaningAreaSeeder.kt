package team.siru.ecoguard.cleaningarea

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 서버 시작 시 cleaning-area-seed.areas 에 정의된 구역을 DB에 반영한다.
 * 없는 구역은 생성하고, 이미 있는 구역은 활성 여부를 제외한 값(이름, 설명, AI 모델 준비 여부 등)을 갱신한다.
 * API로 구역을 만드는 엔드포인트는 명세에 없어서, 학교 도면처럼 자주 안 바뀌는 데이터는
 * 배포 설정(application.yaml)으로 관리하는 방식을 택했다.
 */
@Component
class CleaningAreaSeeder(
    private val cleaningAreaRepository: CleaningAreaRepository,
    private val properties: CleaningAreaSeedProperties,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        var created = 0
        properties.areas.forEach { item ->
            val existing = cleaningAreaRepository.findByZoneCode(item.zoneCode)
            if (existing == null) {
                cleaningAreaRepository.save(
                    CleaningArea(
                        zoneCode = item.zoneCode,
                        name = item.name,
                        description = item.description,
                        cleanTime = item.cleanTime,
                        semester = item.semester,
                        isActive = item.active && item.modelReady,
                        modelReady = item.modelReady,
                    ),
                )
                created++
            } else {
                existing.name = item.name
                existing.description = item.description
                existing.cleanTime = item.cleanTime
                existing.semester = item.semester
                existing.modelReady = item.modelReady
                // AI 모델이 준비되지 않은 구역은 인증 대상에서 제외해야 하므로 활성 상태를 유지할 수 없다.
                if (existing.isActive && !item.modelReady) {
                    existing.isActive = false
                    log.warn("AI 모델이 준비되지 않은 구역 {}을(를) 비활성화했습니다", item.zoneCode)
                }
            }
        }
        if (created > 0) {
            log.info("청소구역 {}개 자동 등록 완료", created)
        }
    }
}
