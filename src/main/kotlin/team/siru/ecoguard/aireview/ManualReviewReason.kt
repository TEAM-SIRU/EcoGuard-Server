package team.siru.ecoguard.aireview

enum class ManualReviewReason {
    MODEL_NOT_READY,
    ZONE_MODEL_NOT_READY,
    TIMEOUT,
    UNKNOWN_ZONE,

    /** AI(Gemini)가 통과시키지 않은 인증. 자동 반려하지 않고 교사가 확인한다. */
    AI_FAILED,

    /** AI 호출 한도(429) 초과 */
    RATE_LIMITED,

    /** AI 호출 거부나 해석할 수 없는 응답 */
    AI_ERROR,
}
