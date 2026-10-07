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
import team.siru.ecoguard.aireview.AiReviewRepository
import team.siru.ecoguard.aireview.AiReviewService
import team.siru.ecoguard.aireview.EvaluateRequest
import team.siru.ecoguard.aireview.ManualReviewReason
import team.siru.ecoguard.cleaningarea.CleaningArea
import team.siru.ecoguard.user.Role
import team.siru.ecoguard.user.User
import team.siru.ecoguard.verification.Verification
import team.siru.ecoguard.verification.VerificationRepository
import team.siru.ecoguard.verification.VerificationStatus
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

    private val request = EvaluateRequest(ByteArray(8), "zone_A", "Hall", null, null)

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
            TransactionTemplate(noOpTransactionManager),
        )
    }

    private fun success(isPassed: Boolean?, failReasons: List<String>? = null) =
        AiEvaluateOutcome.Success(AiEvaluateResponse(decision = "X", isPassed = isPassed, failReasons = failReasons), "{}")

    private fun accumulateCalls() = Mockito.mockingDetails(activityService).invocations.count { it.method.name == "accumulate" }

    @Test
    fun `a clear pass is approved and earns minutes`() {
        val verification = newVerification()

        service(success(isPassed = true, failReasons = emptyList())).processReview(verification.id, request)

        assertEquals(VerificationStatus.APPROVED, verification.status)
        assertEquals(1, accumulateCalls())
    }

    @Test
    fun `a failed verdict goes to manual review instead of being rejected`() {
        val verification = newVerification()

        service(success(isPassed = false, failReasons = listOf("DUSTPAN_NOT_FOUND"))).processReview(verification.id, request)

        assertEquals(VerificationStatus.MANUAL_REVIEW, verification.status)
        assertEquals(ManualReviewReason.AI_FAILED.name, verification.manualReviewReason)
        assertEquals(listOf("DUSTPAN_NOT_FOUND"), verification.failReasons)
        assertEquals(0, accumulateCalls())
    }

    @Test
    fun `a response without a verdict goes to manual review`() {
        val verification = newVerification()

        service(success(isPassed = null)).processReview(verification.id, request)

        assertEquals(VerificationStatus.MANUAL_REVIEW, verification.status)
        assertEquals(ManualReviewReason.AI_ERROR.name, verification.manualReviewReason)
        assertEquals(0, accumulateCalls())
    }

    @Test
    fun `a pass that still lists fail reasons is not approved`() {
        val verification = newVerification()

        service(success(isPassed = true, failReasons = listOf("TRASH_OUTSIDE_DUSTPAN"))).processReview(verification.id, request)

        assertEquals(VerificationStatus.MANUAL_REVIEW, verification.status)
        assertEquals(ManualReviewReason.AI_FAILED.name, verification.manualReviewReason)
        assertEquals(0, accumulateCalls())
    }

    @Test
    fun `an already handled verification is not evaluated or overwritten`() {
        val verification = newVerification(status = VerificationStatus.MANUAL_REVIEW)
        val calls = IntArray(1)

        service(success(isPassed = true, failReasons = emptyList()), calls).processReview(verification.id, request)

        assertEquals(0, calls[0], "이미 처리된 인증은 AI 를 호출하지 않는다")
        assertEquals(VerificationStatus.MANUAL_REVIEW, verification.status)
        assertTrue(accumulateCalls() == 0)
    }
}
