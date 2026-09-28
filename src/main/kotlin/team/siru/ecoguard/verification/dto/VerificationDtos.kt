package team.siru.ecoguard.verification.dto

import team.siru.ecoguard.verification.Verification
import team.siru.ecoguard.verification.VerificationStatus
import java.time.LocalDate

data class SubmitVerificationResponse(
    val verificationId: Long,
    val status: VerificationStatus,
)

data class MyVerificationResponse(
    val verificationId: Long,
    val photoUrl: String,
    val date: LocalDate,
    val areaName: String,
    val reviewStatus: VerificationStatus,
    val failReasons: List<String>?,
) {
    companion object {
        fun from(verification: Verification) = MyVerificationResponse(
            verificationId = verification.id,
            photoUrl = verification.photoUrl,
            date = verification.verificationDate,
            areaName = verification.area.name,
            reviewStatus = verification.status,
            failReasons = if (verification.status == VerificationStatus.REJECTED) verification.failReasons else null,
        )
    }
}
