package team.siru.ecoguard

import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.SendResponse
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import team.siru.ecoguard.notification.FcmPushSender
import team.siru.ecoguard.notification.PushMessage
import kotlin.test.assertEquals

class FcmPushSenderTests {

    private fun failure(code: MessagingErrorCode): SendResponse {
        val error = Mockito.mock(FirebaseMessagingException::class.java)
        Mockito.`when`(error.messagingErrorCode).thenReturn(code)
        val response = Mockito.mock(SendResponse::class.java)
        Mockito.`when`(response.isSuccessful).thenReturn(false)
        Mockito.`when`(response.exception).thenReturn(error)
        return response
    }

    private fun success(): SendResponse {
        val response = Mockito.mock(SendResponse::class.java)
        Mockito.`when`(response.isSuccessful).thenReturn(true)
        return response
    }

    private fun sender(vararg results: SendResponse): FcmPushSender {
        val batch = Mockito.mock(BatchResponse::class.java)
        Mockito.`when`(batch.responses).thenReturn(results.toList())
        val messaging = Mockito.mock(FirebaseMessaging::class.java)
        Mockito.`when`(messaging.sendEachForMulticast(Mockito.any(MulticastMessage::class.java))).thenReturn(batch)
        return FcmPushSender(messaging)
    }

    private val message = PushMessage("제목", "본문", mapOf("type" to "NOTICE_CREATED"))

    @Test
    fun `only unregistered tokens are reported as dead`() {
        val dead = sender(success(), failure(MessagingErrorCode.UNREGISTERED), success())
            .send(listOf("a", "b", "c"), message)

        assertEquals(listOf("b"), dead)
    }

    @Test
    fun `invalid argument is a message problem and does not delete tokens`() {
        val dead = sender(failure(MessagingErrorCode.INVALID_ARGUMENT), failure(MessagingErrorCode.INVALID_ARGUMENT))
            .send(listOf("a", "b"), message)

        assertEquals(emptyList(), dead)
    }

    @Test
    fun `other failures such as quota or server errors keep the tokens`() {
        val dead = sender(failure(MessagingErrorCode.QUOTA_EXCEEDED), failure(MessagingErrorCode.UNAVAILABLE))
            .send(listOf("a", "b"), message)

        assertEquals(emptyList(), dead)
    }
}
