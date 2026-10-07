package team.siru.ecoguard

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.core.task.TaskRejectedException
import team.siru.ecoguard.aireview.AiReviewDispatcher
import team.siru.ecoguard.aireview.AiReviewExecutorConfig
import team.siru.ecoguard.aireview.AiReviewQueueProperties
import team.siru.ecoguard.aireview.AiReviewService
import team.siru.ecoguard.aireview.GeminiModelSpec
import team.siru.ecoguard.aireview.GeminiProperties
import team.siru.ecoguard.aireview.ManualReviewReason
import team.siru.ecoguard.aireview.ReviewJob
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiReviewQueueTests {

    // ---- 모델 목록 설정 ----

    private fun bindGemini(vararg pairs: Pair<String, String>): GeminiProperties =
        Binder(MapConfigurationPropertySource(mapOf(*pairs))).bind("gemini", GeminiProperties::class.java).get()

    @Test
    fun `without a model list the single configured model is used`() {
        val specs = bindGemini("gemini.model" to "only-model").modelSpecs()

        assertEquals(listOf(GeminiModelSpec("only-model", 0)), specs)
    }

    @Test
    fun `an empty model list setting falls back to the single model`() {
        // 운영 설정은 GEMINI_MODELS 를 비워 두면 빈 문자열이 된다.
        val specs = bindGemini("gemini.model" to "only-model", "gemini.models" to "").modelSpecs()

        assertEquals(listOf(GeminiModelSpec("only-model", 0)), specs)
    }

    @Test
    fun `a model list keeps its order and reads per model limits`() {
        val specs = bindGemini("gemini.models" to "model-a:15, model-b:10 ,model-c").modelSpecs()

        assertEquals(listOf(GeminiModelSpec("model-a", 15), GeminiModelSpec("model-b", 10), GeminiModelSpec("model-c", 0)), specs)
    }

    @Test
    fun `the default limit applies to models without their own and duplicates are dropped`() {
        val specs = bindGemini("gemini.models" to "a:15,b,a:99", "gemini.rpm-limit" to "7").modelSpecs()

        assertEquals(listOf(GeminiModelSpec("a", 15), GeminiModelSpec("b", 7)), specs)
    }

    @Test
    fun `a negative or unreadable limit never turns into a negative limit`() {
        val specs = GeminiProperties(models = listOf("a:-3", "weird:abc")).modelSpecs()

        assertEquals(listOf(GeminiModelSpec("a", 0), GeminiModelSpec("weird:abc", 0)), specs)
    }

    @Test
    fun `queue settings are validated when the server starts`() {
        assertThrows<IllegalStateException> { AiReviewQueueProperties(workers = 0) }
        assertThrows<IllegalStateException> { AiReviewQueueProperties(capacity = 0) }
        assertThrows<IllegalStateException> { AiReviewQueueProperties(maxWaitSeconds = 0) }
    }

    // ---- 대기열(전용 작업 실행기) ----

    @Test
    fun `the review executor rejects new work once its queue is full`() {
        val executor = AiReviewExecutorConfig(AiReviewQueueProperties(workers = 1, capacity = 1)).aiReviewExecutor()
        executor.initialize()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            executor.execute {
                started.countDown()
                release.await()
            }
            assertTrue(started.await(5, TimeUnit.SECONDS), "작업 스레드가 첫 작업을 잡아야 한다")

            executor.execute { } // 대기열 한 칸을 채운다
            assertThrows<TaskRejectedException> { executor.execute { } }
        } finally {
            release.countDown()
            executor.shutdown()
        }
    }

    // ---- 대기열에 넣는 쪽 ----

    private val job = ReviewJob("/files/verifications/a.jpg", "zone_A", "Hall", null, null, Instant.parse("2026-10-07T07:30:00Z"))

    private fun calls(service: AiReviewService) = Mockito.mockingDetails(service).invocations.map { it.method.name }

    @Test
    fun `a job is handed to the review service`() {
        val service = Mockito.mock(AiReviewService::class.java)

        AiReviewDispatcher(service).dispatch(7L, job)

        assertEquals(listOf("processReview"), calls(service))
    }

    @Test
    fun `a full queue sends the verification to manual review instead of failing the submission`() {
        val service = Mockito.mock(AiReviewService::class.java)
        Mockito.doThrow(TaskRejectedException("queue is full")).`when`(service).processReview(7L, job)

        AiReviewDispatcher(service).dispatch(7L, job)

        assertEquals(listOf("processReview", "sendToManualReview"), calls(service))
        val sent = Mockito.mockingDetails(service).invocations.last { it.method.name == "sendToManualReview" }
        assertEquals(7L, sent.arguments[0])
        assertEquals(ManualReviewReason.QUEUE_FULL, sent.arguments[1])
    }

    @Test
    fun `failing to reach manual review does not break the submission either`() {
        val service = Mockito.mock(AiReviewService::class.java)
        Mockito.doThrow(TaskRejectedException("queue is full")).`when`(service).processReview(7L, job)
        Mockito.doThrow(RuntimeException("db is down")).`when`(service).sendToManualReview(7L, ManualReviewReason.QUEUE_FULL)

        AiReviewDispatcher(service).dispatch(7L, job)
    }

    @Test
    fun `an unexpected error is logged and leaves recovery to the stale review job`() {
        val service = Mockito.mock(AiReviewService::class.java)
        Mockito.doThrow(IllegalStateException("boom")).`when`(service).processReview(7L, job)

        AiReviewDispatcher(service).dispatch(7L, job)

        assertEquals(listOf("processReview"), calls(service))
    }
}
