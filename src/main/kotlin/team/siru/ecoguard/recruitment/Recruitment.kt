package team.siru.ecoguard.recruitment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import team.siru.ecoguard.common.BaseEntity
import team.siru.ecoguard.user.User
import java.time.LocalDateTime

@Entity
@Table(name = "recruitments")
class Recruitment(
    @Column(nullable = false)
    var semester: String,

    @Column(nullable = false)
    var grade: Int,

    @Column(name = "class_no", nullable = false)
    var classNo: Int,

    @Column(nullable = false)
    var maxCount: Int,

    @Column(nullable = false)
    var startDate: LocalDateTime,

    @Column(nullable = false)
    var endDate: LocalDateTime,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    var teacher: User,
) : BaseEntity() {

    fun isClosed(now: LocalDateTime): Boolean = endDate.isBefore(now)

    fun isOpen(now: LocalDateTime): Boolean = !now.isBefore(startDate) && !now.isAfter(endDate)

    fun periodStatus(now: LocalDateTime): RecruitmentPeriodStatus = when {
        now.isBefore(startDate) -> RecruitmentPeriodStatus.UPCOMING
        isClosed(now) -> RecruitmentPeriodStatus.CLOSED
        else -> RecruitmentPeriodStatus.OPEN
    }
}

enum class RecruitmentPeriodStatus {
    UPCOMING,
    OPEN,
    CLOSED,
}
