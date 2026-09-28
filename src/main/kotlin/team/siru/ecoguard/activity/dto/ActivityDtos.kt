package team.siru.ecoguard.activity.dto

import team.siru.ecoguard.activity.ServiceTimeLog

data class ServiceTimeLogItem(
    val date: String,
    val area: String?,
    val result: String,
) {
    companion object {
        fun from(log: ServiceTimeLog) = ServiceTimeLogItem(
            date = log.date.toString(),
            area = log.areaName,
            result = "APPROVED",
        )
    }
}

data class MyActivityResponse(
    val totalMinutes: Long,
    val logs: List<ServiceTimeLogItem>,
)

data class StudentActivityResponse(
    val studentId: Long,
    val name: String,
    val area: String?,
    val photoUrl: String?,
    val totalMinutes: Long,
)
