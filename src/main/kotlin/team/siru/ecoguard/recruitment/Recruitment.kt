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
import java.time.LocalTime

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

    /** 청소 활동 시작/종료 시각. null이면 기본값(07:20~08:10)을 쓴다. */
    var activityStartTime: LocalTime? = null,

    var activityEndTime: LocalTime? = null,
) : BaseEntity() {

    fun effectiveActivityStart(): LocalTime = activityStartTime ?: DEFAULT_ACTIVITY_START

    fun effectiveActivityEnd(): LocalTime = activityEndTime ?: DEFAULT_ACTIVITY_END

    fun isClosed(now: LocalDateTime): Boolean = endDate.isBefore(now)

    fun isOpen(now: LocalDateTime): Boolean = !now.isBefore(startDate) && !now.isAfter(endDate)

    fun periodStatus(now: LocalDateTime): RecruitmentPeriodStatus = when {
        now.isBefore(startDate) -> RecruitmentPeriodStatus.UPCOMING
        isClosed(now) -> RecruitmentPeriodStatus.CLOSED
        else -> RecruitmentPeriodStatus.OPEN
    }

    companion object {
        val DEFAULT_ACTIVITY_START: LocalTime = LocalTime.of(7, 20)
        val DEFAULT_ACTIVITY_END: LocalTime = LocalTime.of(8, 10)
    }
}

enum class RecruitmentPeriodStatus {
    UPCOMING,
    OPEN,
    CLOSED,
}
