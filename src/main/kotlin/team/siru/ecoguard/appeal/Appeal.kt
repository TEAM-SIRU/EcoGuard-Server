package team.siru.ecoguard.appeal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import team.siru.ecoguard.common.BaseEntity
import team.siru.ecoguard.user.User
import team.siru.ecoguard.verification.Verification

@Entity
@Table(name = "appeals")
class Appeal(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verification_id", nullable = false)
    var verification: Verification,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: User,

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AppealStatus = AppealStatus.PENDING,
) : BaseEntity()
