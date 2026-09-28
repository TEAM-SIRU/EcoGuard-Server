package team.siru.ecoguard.cleaningarea

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import team.siru.ecoguard.common.BaseEntity
import team.siru.ecoguard.user.User

@Entity
@Table(name = "assignments")
class Assignment(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id", nullable = false)
    var area: CleaningArea,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    var student: User,
) : BaseEntity()
