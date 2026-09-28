package team.siru.ecoguard.cleaningarea

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 서버 시작 시 cleaning-area-seed.areas 에 정의된 구역 중 아직 없는 것만 생성한다.
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
            if (!cleaningAreaRepository.existsByZoneCode(item.zoneCode)) {
                cleaningAreaRepository.save(
                    CleaningArea(
                        zoneCode = item.zoneCode,
                        name = item.name,
                        description = item.description,
                        cleanTime = item.cleanTime,
                        x = item.x,
                        y = item.y,
                        isActive = item.active,
                        modelReady = item.modelReady,
                    ),
                )
                created++
            }
        }
        if (created > 0) {
            log.info("청소구역 {}개 자동 등록 완료", created)
        }
    }
}
