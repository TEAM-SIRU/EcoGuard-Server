package team.siru.ecoguard

import org.junit.jupiter.api.Test
import team.siru.ecoguard.aireview.GeminiModelPool
import team.siru.ecoguard.aireview.GeminiModelPool.Pick
import team.siru.ecoguard.aireview.GeminiModelSpec
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GeminiModelPoolTests {

    private val clock = MutableClock(Instant.parse("2026-10-07T07:30:00Z"))

    private fun pool(vararg specs: GeminiModelSpec) = GeminiModelPool(specs.toList(), clock)

    private fun advanceSeconds(seconds: Long) = clock.set(clock.instant().plusSeconds(seconds))

    private fun GeminiModelPool.nextModel(): String = assertIs<Pick.Use>(acquire()).model

    private fun GeminiModelPool.nextWait(): Duration = assertIs<Pick.WaitFor>(acquire()).duration

    @Test
    fun `models are used in the configured order while all of them have room`() {
        val pool = pool(GeminiModelSpec("a", 0), GeminiModelSpec("b", 0))

        assertEquals(listOf("a", "a", "a"), List(3) { pool.nextModel() })
    }

    @Test
    fun `a model close to its limit is avoided while another model has room`() {
        val pool = pool(GeminiModelSpec("a", 10), GeminiModelSpec("b", 10))

        // a 가 분당 한도의 80%(8번)에 닿기 전까지는 우선순위대로 a, 닿은 뒤에는 여유 있는 b 를 먼저 쓴다.
        assertEquals(List(8) { "a" }, List(8) { pool.nextModel() })
        assertEquals(List(8) { "b" }, List(8) { pool.nextModel() })
        // 둘 다 한도에 가까우면 다시 우선순위 순서
        assertEquals("a", pool.nextModel())
    }

    @Test
    fun `it waits when every model has reached its limit and succeeds once the window moves`() {
        val pool = pool(GeminiModelSpec("a", 2))
        pool.nextModel()
        advanceSeconds(10)
        pool.nextModel()

        // 가장 오래된 요청이 1분 창에서 빠질 때(첫 요청 후 60초)까지, 지금은 첫 요청 후 10초라 50초 남았다.
        assertEquals(Duration.ofSeconds(50), pool.nextWait())

        advanceSeconds(50)
        assertEquals("a", pool.nextModel())
    }

    @Test
    fun `a cooled down model is skipped until its cooldown ends`() {
        val pool = pool(GeminiModelSpec("a", 0), GeminiModelSpec("b", 0))

        pool.cooldown("a", Duration.ofSeconds(30))
        assertEquals("b", pool.nextModel())

        advanceSeconds(31)
        assertEquals("a", pool.nextModel())
    }

    @Test
    fun `when every model is cooling down it reports the shortest wait`() {
        val pool = pool(GeminiModelSpec("a", 0), GeminiModelSpec("b", 0))
        pool.cooldown("a", Duration.ofSeconds(30))
        pool.cooldown("b", Duration.ofSeconds(10))

        assertEquals(Duration.ofSeconds(10), pool.nextWait())
    }

    @Test
    fun `a shorter cooldown never shortens a longer one`() {
        val pool = pool(GeminiModelSpec("a", 0))
        pool.cooldown("a", Duration.ofSeconds(60))
        pool.cooldown("a", Duration.ofSeconds(5))

        advanceSeconds(10)

        assertEquals(Duration.ofSeconds(50), pool.nextWait())
    }

    @Test
    fun `requests older than a minute stop counting`() {
        val pool = pool(GeminiModelSpec("a", 2))
        pool.nextModel()
        pool.nextModel()
        assertEquals(2, pool.status().single().requestsInLastMinute)

        advanceSeconds(61)

        assertEquals(0, pool.status().single().requestsInLastMinute)
        assertEquals("a", pool.nextModel())
    }

    @Test
    fun `cooling down an unknown model changes nothing`() {
        val pool = pool(GeminiModelSpec("a", 0))

        pool.cooldown("not-registered", Duration.ofMinutes(5))

        assertEquals("a", pool.nextModel())
    }
}
