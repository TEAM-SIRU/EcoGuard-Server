package team.siru.ecoguard.schoolcalendar

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "neis")
data class NeisProperties(
    /** 비어 있으면 학사일정을 조회하지 않고 방학 제한을 적용하지 않는다. */
    val apiKey: String = "",
    /** 시도교육청 코드 (ATPT_OFCDC_SC_CODE) */
    val educationOfficeCode: String = "",
    /** 표준학교코드 (SD_SCHUL_CODE) */
    val schoolCode: String = "",
    val baseUrl: String = "https://open.neis.go.kr",
    val timeoutMillis: Long = 5000,
)
