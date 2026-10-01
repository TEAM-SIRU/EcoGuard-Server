package team.siru.ecoguard

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.FileSystemResource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import team.siru.ecoguard.cleaningarea.CleaningArea
import team.siru.ecoguard.cleaningarea.CleaningAreaRepository
import team.siru.ecoguard.cleaningarea.CleaningAreaSeedItem
import team.siru.ecoguard.cleaningarea.CleaningAreaSeedProperties
import team.siru.ecoguard.cleaningarea.CleaningAreaSeeder
import team.siru.ecoguard.cleaningarea.CleaningAreaSemester

@SpringBootTest
class CleaningAreaSeederIntegrationTests @Autowired constructor(
    private val cleaningAreaRepository: CleaningAreaRepository,
    private val transactionManager: PlatformTransactionManager,
) {
    @BeforeEach
    @AfterEach
    fun cleanDatabase() {
        cleaningAreaRepository.deleteAll()
    }

    @Test
    fun `production seed defines the 18 Notion cleaning areas`() {
        val environment = StandardEnvironment()
        YamlPropertySourceLoader().load("main", FileSystemResource("src/main/resources/application.yaml"))
            .forEach(environment.propertySources::addLast)
        val properties = Binder.get(environment)
            .bind("cleaning-area-seed", CleaningAreaSeedProperties::class.java)
            .get()

        val codes = properties.areas.map { it.zoneCode }
        assertEquals(18, codes.size)
        assertEquals(codes.size, codes.toSet().size)
        assertEquals(12, properties.areas.count { it.semester == CleaningAreaSemester.COMMON })
        assertEquals(4, properties.areas.count { it.semester == CleaningAreaSemester.FIRST })
        assertEquals(2, properties.areas.count { it.semester == CleaningAreaSemester.SECOND })
        assertTrue(properties.areas.all { it.cleanTime == "07:20~08:10" })
    }

    @Test
    fun `seeder creates missing areas and syncs existing ones without touching teacher activation`() {
        cleaningAreaRepository.save(
            CleaningArea(zoneCode = "main_stair_a", name = "old", isActive = true, modelReady = true),
        )

        seed(
            CleaningAreaSeedItem(zoneCode = "main_stair_a", name = "본관 계단 A", modelReady = true),
            CleaningAreaSeedItem(
                zoneCode = "main_corridor_f3",
                name = "본관 3층 복도 + 홈베이스",
                semester = CleaningAreaSemester.SECOND,
            ),
        )

        val stairA = cleaningAreaRepository.findByZoneCode("main_stair_a")!!
        assertEquals("본관 계단 A", stairA.name)
        assertTrue(stairA.isActive)

        val corridor = cleaningAreaRepository.findByZoneCode("main_corridor_f3")!!
        assertEquals(CleaningAreaSemester.SECOND, corridor.semester)
        assertFalse(corridor.modelReady)
        assertFalse(corridor.isActive)
    }

    @Test
    fun `seeder deactivates an active area whose AI model is no longer ready`() {
        cleaningAreaRepository.save(
            CleaningArea(zoneCode = "connector_f2", name = "연결통로 2층", isActive = true, modelReady = true),
        )

        seed(CleaningAreaSeedItem(zoneCode = "connector_f2", name = "본관-금봉관 연결통로 2층", modelReady = false))

        val area = cleaningAreaRepository.findByZoneCode("connector_f2")!!
        assertFalse(area.modelReady)
        assertFalse(area.isActive)
    }

    private fun seed(vararg items: CleaningAreaSeedItem) {
        val seeder = CleaningAreaSeeder(cleaningAreaRepository, CleaningAreaSeedProperties(items.toList()))
        TransactionTemplate(transactionManager).executeWithoutResult { seeder.run(DefaultApplicationArguments()) }
    }
}
