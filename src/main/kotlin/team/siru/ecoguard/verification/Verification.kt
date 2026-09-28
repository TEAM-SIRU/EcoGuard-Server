package team.siru.ecoguard.verification

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import team.siru.ecoguard.cleaningarea.CleaningArea
import team.siru.ecoguard.common.BaseEntity
import team.siru.ecoguard.user.User
import java.time.LocalDate

@Entity
@Table(name = "verifications")
class Verification(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id", nullable = false)
    var area: CleaningArea,

    @Column(nullable = false)
    var photoUrl: String,

    @Column(nullable = false)
    var verificationDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: VerificationStatus = VerificationStatus.PROCESSING,

    @ElementCollection
    @CollectionTable(name = "verification_fail_reasons", joinColumns = [JoinColumn(name = "verification_id")])
    @Column(name = "reason")
    var failReasons: MutableList<String> = mutableListOf(),

    var manualReviewReason: String? = null,
) : BaseEntity()
