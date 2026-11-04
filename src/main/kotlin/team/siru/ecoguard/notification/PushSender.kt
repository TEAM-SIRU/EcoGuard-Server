package team.siru.ecoguard.notification

/** 푸시로 보낼 내용. [data]는 앱이 알림을 눌렀을 때 화면을 고르는 데 쓴다(값은 문자열만 가능). */
data class PushMessage(
    val title: String,
    val body: String,
    val data: Map<String, String> = emptyMap(),
)

interface PushSender {
    /**
     * [tokens] 전체에 [message]를 보낸다.
     * @return 더 이상 쓸 수 없어 지워야 하는 토큰(앱 삭제·토큰 만료 등)
     */
    fun send(tokens: List<String>, message: PushMessage): List<String>
}
