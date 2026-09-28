package team.siru.ecoguard.aireview

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import team.siru.ecoguard.common.BaseEntity
import team.siru.ecoguard.verification.Verification

@Entity
@Table(name = "ai_reviews")
class AiReview(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verification_id", nullable = false, unique = true)
    var verification: Verification,

    @Column(columnDefinition = "TEXT")
    var rawResponse: String,

    var decision: String? = null,

    var isPassed: Boolean? = null,
) : BaseEntity()
