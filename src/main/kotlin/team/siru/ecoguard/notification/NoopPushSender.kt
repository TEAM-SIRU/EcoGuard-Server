package team.siru.ecoguard.notification

import org.slf4j.LoggerFactory

/** Firebase 자격증명이 없을 때 쓰는 발송기. 아무것도 보내지 않는다. */
class NoopPushSender : PushSender {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(tokens: List<String>, message: PushMessage): List<String> {
        log.debug("FCM 이 꺼져 있어 푸시를 보내지 않습니다 (대상 {}건)", tokens.size)
        return emptyList()
    }
}
