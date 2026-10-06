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
import jakarta.persistence.UniqueConstraint
import team.siru.ecoguard.cleaningarea.CleaningArea
import team.siru.ecoguard.common.BaseEntity
import team.siru.ecoguard.user.User
import java.time.LocalDate

@Entity
@Table(
    name = "verifications",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["student_id", "verification_date"]),
        UniqueConstraint(columnNames = ["student_id", "idempotency_key"]),
    ],
)
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

    /** 앱이 재전송할 때 중복 접수를 막기 위한 `Idempotency-Key`. 보내지 않은 요청은 null. */
    @Column(length = 64)
    var idempotencyKey: String? = null,
) : BaseEntity()
