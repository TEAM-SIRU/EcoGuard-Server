package team.siru.ecoguard

import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import team.siru.ecoguard.aireview.AiReviewDispatcher
import team.siru.ecoguard.aireview.PendingReviewRequeuer
import team.siru.ecoguard.aireview.ReviewJob
import team.siru.ecoguard.cleaningarea.CleaningArea
import team.siru.ecoguard.user.Role
import team.siru.ecoguard.user.User
import team.siru.ecoguard.verification.Verification
import team.siru.ecoguard.verification.VerificationRepository
import team.siru.ecoguard.verification.VerificationStatus
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals

class PendingReviewRequeuerTests {

    private val verificationRepository = Mockito.mock(VerificationRepository::class.java)
    private val dispatcher = Mockito.mock(AiReviewDispatcher::class.java)
    private val clock = MutableClock(Instant.parse("2026-10-07T22:05:00Z"))

    private val noOpTransactionManager = object : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus = SimpleTransactionStatus()
        override fun commit(status: TransactionStatus) {}
        override fun rollback(status: TransactionStatus) {}
    }

    private fun requeuer() = PendingReviewRequeuer(
        verificationRepository,
        dispatcher,
        TransactionTemplate(noOpTransactionManager),
        clock,
    )

    @Test
    fun `verifications left processing are put back on the queue with a fresh wait window`() {
        val student = User(gsmAccountId = 1, email = "s@test.local", name = "Student", role = Role.STUDENT, studentNumber = "1101")
        val area = CleaningArea(zoneCode = "zone_A", name = "Hall", description = "복도")
        val verification = Verification(
            student = student,
            area = area,
            photoUrl = "/files/verifications/a.jpg",
            verificationDate = LocalDate.of(2026, 10, 7),
        )
        Mockito.`when`(verificationRepository.findByStatusOrderByCreatedAtAsc(VerificationStatus.PROCESSING))
            .thenReturn(listOf(verification))

        requeuer().requeue()

        val expected = ReviewJob("/files/verifications/a.jpg", "zone_A", "Hall", "복도", "1101", clock.instant())
        Mockito.verify(dispatcher).dispatch(verification.id, expected)
    }

    @Test
    fun `nothing is dispatched when no verification is left processing`() {
        Mockito.`when`(verificationRepository.findByStatusOrderByCreatedAtAsc(VerificationStatus.PROCESSING))
            .thenReturn(emptyList())

        requeuer().requeue()

        assertEquals(0, Mockito.mockingDetails(dispatcher).invocations.size)
    }
}
