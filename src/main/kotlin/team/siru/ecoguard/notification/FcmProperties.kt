package team.siru.ecoguard.notification

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @param credentialsPath Firebase 서비스 계정 키(JSON) 파일 경로. 비어 있으면 푸시를 보내지 않고
 *   인앱 알림함에만 기록한다(로컬·테스트 기본값).
 */
@ConfigurationProperties(prefix = "fcm")
data class FcmProperties(
    val credentialsPath: String = "",
)
