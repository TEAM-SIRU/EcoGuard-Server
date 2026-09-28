package team.siru.ecoguard.aireview

import org.springframework.data.jpa.repository.JpaRepository

interface AiReviewRepository : JpaRepository<AiReview, Long> {
    fun findByVerificationId(verificationId: Long): AiReview?
}
