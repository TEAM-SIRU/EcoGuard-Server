package team.siru.ecoguard.activity

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
import java.time.LocalDate

@Entity
@Table(name = "service_time_logs")
class ServiceTimeLog(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: User,

    @Column(nullable = false)
    var minutes: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var reason: ServiceTimeReason,

    @Column(nullable = false)
    var date: LocalDate,

    var areaName: String? = null,
) : BaseEntity()
