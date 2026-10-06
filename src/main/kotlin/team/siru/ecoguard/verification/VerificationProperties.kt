package team.siru.ecoguard.verification

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "verification")
data class VerificationProperties(
    /**
     * 인증 가능 시간이 끝나기 전에 전송을 시작한 요청(`X-Submit-Started-At`)을 마감 뒤에도 받아 주는 최대 시간(분).
     * 0이면 마감 뒤 재시도를 받지 않는다.
     */
    val lateRetryGraceMinutes: Long = 5,
)
