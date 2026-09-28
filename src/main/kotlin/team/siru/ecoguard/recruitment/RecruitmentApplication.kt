package team.siru.ecoguard.recruitment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import team.siru.ecoguard.common.BaseEntity
import team.siru.ecoguard.user.User

@Entity
@Table(
    name = "recruitment_applications",
    uniqueConstraints = [UniqueConstraint(columnNames = ["recruitment_id", "student_id"])],
)
class RecruitmentApplication(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruitment_id", nullable = false)
    var recruitment: Recruitment,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: User,

    @Column(nullable = false, columnDefinition = "TEXT")
    var motivation: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ApplicationStatus = ApplicationStatus.PENDING,
) : BaseEntity()
