package team.siru.ecoguard.notification

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import org.slf4j.LoggerFactory

class FcmPushSender(
    private val messaging: FirebaseMessaging,
) : PushSender {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(tokens: List<String>, message: PushMessage): List<String> {
        val invalid = mutableListOf<String>()
        // FCM 멀티캐스트는 한 번에 500개까지다.
        tokens.chunked(MAX_BATCH).forEach { batch ->
            val multicast = MulticastMessage.builder()
                .setNotification(
                    com.google.firebase.messaging.Notification.builder()
                        .setTitle(message.title)
                        .setBody(message.body)
                        .build(),
                )
                .putAllData(message.data)
                .addAllTokens(batch)
                .build()
            try {
                val response = messaging.sendEachForMulticast(multicast)
                response.responses.forEachIndexed { index, result ->
                    val error = result.exception ?: return@forEachIndexed
                    if (error.messagingErrorCode in DEAD_TOKEN_CODES) {
                        invalid += batch[index]
                    } else {
                        log.warn("푸시 발송에 실패했습니다 ({}): {}", error.messagingErrorCode, error.message)
                    }
                }
            } catch (e: FirebaseMessagingException) {
                log.warn("푸시 일괄 발송에 실패했습니다 ({}건): {}", batch.size, e.message)
            }
        }
        return invalid
    }

    private companion object {
        const val MAX_BATCH = 500
        val DEAD_TOKEN_CODES = setOf(MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT)
    }
}
