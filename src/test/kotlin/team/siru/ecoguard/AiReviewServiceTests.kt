package team.siru.ecoguard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import team.siru.ecoguard.activity.ActivityService
import team.siru.ecoguard.aireview.AiEvaluateOutcome
import team.siru.ecoguard.aireview.AiEvaluateResponse
import team.siru.ecoguard.aireview.AiEvaluator
import team.siru.ecoguard.aireview.AiReview
import team.siru.ecoguard.aireview.AiReviewRepository
import team.siru.ecoguard.aireview.AiReviewService
import team.siru.ecoguard.aireview.AiReviewQueueProperties
import team.siru.ecoguard.aireview.EvaluateRequest
import team.siru.ecoguard.aireview.ReviewJob
import team.siru.ecoguard.aireview.ManualReviewReason
import team.siru.ecoguard.cleaningarea.CleaningArea
import team.siru.ecoguard.common.storage.FileStorageService
import team.siru.ecoguard.user.Role
import team.siru.ecoguard.user.User
import team.siru.ecoguard.verification.Verification
import team.siru.ecoguard.verification.VerificationRepository
import team.siru.ecoguard.verification.VerificationStatus
import java.time.Instant
import java.time.LocalDate
import java.util.Optional

/**
 * AI 검수 결과를 인증에 반영하는 규칙을 검증한다. 어떤 검수 구현(Gemini, 자체 AI 서버)이든
 * "통과"로 확실히 말한 경우에만 자동 승인하고, 그 외에는 자동 반려하지 않고 교사 수동 검토로 보내야 한다.
 */
class AiReviewServiceTests {

    private val verificationRepository = Mockito.mock(VerificationRepository::class.java)
    private val aiReviewRepository = Mockito.mock(AiReviewRepository::class.java)
    private val activityService = Mockito.mock(ActivityService::class.java)

    private val noOpTransactionManager = object : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()
        override fun commit(status: TransactionStatus) {}
        override fun rollback(status: TransactionStatus) {}
    }

    private val clock = MutableClock(Instant.parse("2026-10-07T07:30:00Z"))
    private val photoUrl = "/files/verifications/a.jpg"
    private val job = ReviewJob(photoUrl, "zone_A", "Hall", null, null, queuedAt = clock.instant())

    /** 대기열 차례가 오면 파일에서 읽는 사진. 기본으로는 읽을 수 있다. */
    private val fileStorageService = Mockito.mock(FileStorageService::class.java).also {
        Mockito.`when`(it.read(photoUrl)).thenReturn(ByteArray(8))
    }

    private fun newVerification(status: VerificationStatus = VerificationStatus.PROCESSING): Verification {
        val student = User(gsmAccountId = 1, email = "s@test.local", name = "Student", role = Role.STUDENT)
        val area = CleaningArea(zoneCode = "zone_A", name = "Hall")
        val verification = Verification(
            student = student,
            area = area,
            photoUrl = "/files/a.jpg",
            verificationDate = LocalDate.of(2026, 10, 7),
            status = status,
        )
        Mockito.`when`(verificationRepository.findById(verification.id)).thenReturn(Optional.of(verification))
        Mockito.`when`(verificationRepository.findWithLockById(verification.id)).thenReturn(verification)
        return verification
    }

    /** 평가기가 몇 번 호출됐는지 세면서 [outcome]을 돌려주는 서비스를 만든다. */
    private fun service(outcome: AiEvaluateOutcome, calls: IntArray = IntArray(1)): AiReviewService {
        val evaluator = object : AiEvaluator {
            override fun evaluate(request: EvaluateRequest): AiEvaluateOutcome {
                calls[0]++
                return outcome
            }
        }
        return AiReviewService(
            evaluator,
            verificationRepository,
            aiReviewRepository,
            activityService,
            fileStorageService,
            TransactionTemplate(noOpTransactionManager),
            AiReviewQueueProperties(maxWaitSeconds = 300),
            clock,
        )
    }

    private fun success(isPassed: Boolean?, failReasons: List<String>? = null) =
        AiEvaluateOutcome.Success(AiEvaluateResponse(decision = "X", isPassed = isPassed, failReasons = failReasons), "{}")

    private fun accumulateCalls() = Mockito.mockingDetails(activityService).invocations.count { it.method.name == "accumulate" }

    @Test
    fun `a clear pass is approved and earns minutes`() {
        val verification = newVerification()

        service(success(isPassed = true, failReasons = emptyList())).processReview(verification.id, job)

        assertEquals(VerificationStatus.APPROVED, verification.status)
        assertEquals(1, accumulateCalls())
    }

    @Test
    fun `a failed verdict goes to manual review instead of being rejected`() {
        val verification = newVerification()

        service(success(isPassed = false, failReasons = listOf("DUSTPAN_NOT_FOUND"))).processReview(verification.id, job)

        assertEquals(VerificationStatus.MANUAL_REVIEW, verification.status)
        assertEquals(ManualReviewReason.AI_FAILED.name, verification.manualReviewReason)
        assertEquals(listOf("DUSTPAN_NOT_FOUND"), verification.failReasons)
        assertEquals(0, accumulateCalls())
    }

    @Test
    fun `a response without a verdict goes to manual review`() {
        val verification = newVerification()

        service(success(isPassed = null)).processReview(verification.id, job)

        assertEquals(VerificationStatus.MANUAL_REVIEW, verification.status)
        assertEquals(ManualReviewReason.AI_ERROR.name, verification.manualReviewReason)
        assertEquals(0, accumulateCalls())
    }

    @Test
    fun `a pass that still lists fail reasons is not approved`() {
        val verification = newVerification()

        service(success(isPassed = true, failReasons = listOf("TRASH_OUTSIDE_DUSTPAN"))).processReview(verification.id, job)

        assertEquals(VerificationStatus.MANUAL_REVIEW, verification.status)
        assertEquals(ManualReviewReason.AI_FAILED.name, verification.manualReviewReason)
        assertEquals(0, accumulateCalls())
    }

    private fun savedReviews() = Mockito.mockingDetails(aiReviewRepository).invocations
        .filter { it.method.name == "save" }
        .map { it.arguments[0] as AiReview }

    @Test
    fun `manual review keeps the verdict the AI actually gave instead of a fixed FAIL`() {
        val verification = newVerification()
        // AI 는 통과라고 했지만 사유가 함께 와서 서버가 수동 검토로 보낸 경우
        val outcome = AiEvaluateOutcome.NeedsManualReview(
            ManualReviewReason.AI_FAILED,
            rawResponse = """{"is_passed":true}""",
            failReasons = listOf("TRASH_OUTSIDE_DUSTPAN"),
            decision = "PASS",
            isPassed = true,
        )

        service(outcome).processReview(verification.id, job)

        val saved = savedReviews().single()
        assertEquals("PASS", saved.decision)
        assertEquals(true, saved.isPassed)
        assertEquals("""{"is_passed":true}""", saved.rawResponse)
        // 서버의 최종 처리와 이유는 인증 쪽에 따로 남는다.
        assertEquals(VerificationStatus.MANUAL_REVIEW, verification.status)
        assertEquals(ManualReviewReason.AI_FAILED.name, verification.manualReviewReason)
    }

    @Test
    fun `an unreadable response is stored with its raw text and no verdict`() {
        val verification = newVerification()

        service(AiEvaluateOutcome.NeedsManualReview(ManualReviewReason.AI_ERROR, rawResponse = "not json")).processReview(verification.id, job)

        val saved = savedReviews().single()
        assertEquals("not json", saved.rawResponse)
        assertEquals(null, saved.decision)
        assertEquals(null, saved.isPassed)
        assertEquals(ManualReviewReason.AI_ERROR.name, verification.manualReviewReason)
    }

    @Test
    fun `no AI review row is stored when the AI gave no response at all`() {
        val verification = newVerification()

        service(AiEvaluateOutcome.NeedsManualReview(ManualReviewReason.TIMEOUT)).processReview(verification.id, job)

        assertEquals(0, savedReviews().size)
        assertEquals(VerificationStatus.MANUAL_REVIEW, verification.status)
    }

    @Test
    fun `the photo is read from the file only when the job gets its turn`() {
        val verification = newVerification()
        var received: EvaluateRequest? = null
        val evaluator = object : AiEvaluator {
            override fun evaluate(request: EvaluateRequest): AiEvaluateOutcome {
                received = request
                return success(isPassed = true, failReasons = emptyList())
            }
        }
        val service = AiReviewService(
            evaluator, verificationRepository, aiReviewRepository, activityService, fileStorageService,
            TransactionTemplate(noOpTransactionManager), AiReviewQueueProperties(), clock,
        )

        service.processReview(verification.id, job)

        assertEquals(8, received!!.imageBytes.size)
        assertEquals("zone_A", received.zoneId)
        assertEquals(job.queuedAt, received.queuedAt)
    }

    @Test
    fun `a job that waited longer than the limit goes to manual review without calling the AI`() {
        val verification = newVerification()
        val calls = IntArray(1)
        val oldJob = job.copy(queuedAt = clock.instant().minusSeconds(301))

        service(success(isPassed = true, failReasons = emptyList()), calls).processReview(verification.id, oldJob)

        assertEquals(0, calls[0])
        assertEquals(VerificationStatus.MANUAL_REVIEW, verification.status)
        assertEquals(ManualReviewReason.TIMEOUT.name, verification.manualReviewReason)
        assertEquals(0, accumulateCalls())
    }

    @Test
    fun `an unreadable photo goes to manual review as an AI error`() {
        val verification = newVerification()
        val calls = IntArray(1)
        Mockito.`when`(fileStorageService.read(photoUrl)).thenThrow(java.io.UncheckedIOException("gone", java.io.IOException("no such file")))

        service(success(isPassed = true, failReasons = emptyList()), calls).processReview(verification.id, job)

        assertEquals(0, calls[0])
        assertEquals(VerificationStatus.MANUAL_REVIEW, verification.status)
        assertEquals(ManualReviewReason.AI_ERROR.name, verification.manualReviewReason)
    }

    @Test
    fun `a full queue sends the verification to manual review`() {
        val verification = newVerification()

        service(success(isPassed = true, failReasons = emptyList())).sendToManualReview(verification.id, ManualReviewReason.QUEUE_FULL)

        assertEquals(VerificationStatus.MANUAL_REVIEW, verification.status)
        assertEquals(ManualReviewReason.QUEUE_FULL.name, verification.manualReviewReason)
        assertEquals(0, accumulateCalls())
    }

    @Test
    fun `sending to manual review never overwrites a verification that was already handled`() {
        val verification = newVerification(status = VerificationStatus.APPROVED)

        service(success(isPassed = true)).sendToManualReview(verification.id, ManualReviewReason.QUEUE_FULL)

        assertEquals(VerificationStatus.APPROVED, verification.status)
    }

    @Test
    fun `an already handled verification is not evaluated or overwritten`() {
        val verification = newVerification(status = VerificationStatus.MANUAL_REVIEW)
        val calls = IntArray(1)

        service(success(isPassed = true, failReasons = emptyList()), calls).processReview(verification.id, job)

        assertEquals(0, calls[0], "이미 처리된 인증은 AI 를 호출하지 않는다")
        assertEquals(VerificationStatus.MANUAL_REVIEW, verification.status)
        assertTrue(accumulateCalls() == 0)
    }
}
