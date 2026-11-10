package team.siru.ecoguard.common.alert

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "discord.alert")
data class DiscordAlertProperties(
    /** 디스코드 채널의 웹훅 주소. 비워 두면 알림을 보내지 않는다. */
    val webhookUrl: String = "",
    /** 같은 에러(같은 예외 종류 + 같은 API)는 이 시간 안에 한 번만 보낸다. 장애 때 채널이 도배되지 않게 한다. */
    val cooldownSeconds: Long = 60,
    val timeoutMillis: Long = 3000,
)
