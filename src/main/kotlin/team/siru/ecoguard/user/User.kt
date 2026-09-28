package team.siru.ecoguard.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import team.siru.ecoguard.common.BaseEntity

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, unique = true)
    var studentNumber: String,

    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: Role,

    var grade: Int? = null,

    var classNo: Int? = null,
) : BaseEntity()
