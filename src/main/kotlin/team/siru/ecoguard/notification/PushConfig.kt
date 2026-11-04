package team.siru.ecoguard.notification

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream

@Configuration
class PushConfig {

    /** `fcm.credentials-path` 가 비어 있으면 [NoopPushSender], 있으면 Firebase 로 보낸다. 키 파일이 잘못되면 시작 시 실패한다. */
    @Bean
    fun pushSender(properties: FcmProperties): PushSender {
        if (properties.credentialsPath.isBlank()) return NoopPushSender()
        val credentials = FileInputStream(properties.credentialsPath).use { GoogleCredentials.fromStream(it) }
        val options = FirebaseOptions.builder().setCredentials(credentials).build()
        val app = FirebaseApp.getApps().firstOrNull() ?: FirebaseApp.initializeApp(options)
        return FcmPushSender(FirebaseMessaging.getInstance(app))
    }
}
