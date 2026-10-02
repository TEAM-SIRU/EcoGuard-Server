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
    /**
     * dataGSM의 계정 ID(AccountInfoResDto.id). 학생/교사 모두에 존재하는
     * 유일한 식별자라서 로그인 시 사용자를 찾는 키로 사용한다.
     */
    @Column(nullable = false, unique = true)
    var gsmAccountId: Long,

    @Column(nullable = false)
    var email: String,

    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: Role,

    /** 학번. 학생 계정에만 존재한다. */
    var studentNumber: String? = null,

    var grade: Int? = null,

    var classNo: Int? = null,

    /**
     * 이 시각(epoch ms) 이전에 발급된 토큰은 모두 무효다. 로그아웃이나 분실/유출 대응 시 현재 시각으로 갱신한다.
     * null이면 제한이 없다.
     */
    var tokensValidAfter: Long? = null,
) : BaseEntity()
