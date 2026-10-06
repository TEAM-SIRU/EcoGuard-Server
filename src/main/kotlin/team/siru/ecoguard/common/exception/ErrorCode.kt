package team.siru.ecoguard.common.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(val status: HttpStatus, val defaultMessage: String) {
    // common
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // auth
    OAUTH_FAILED(HttpStatus.UNAUTHORIZED, "OAuth 인증에 실패했습니다."),

    // recruitments / applications
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "모집 기간이 올바르지 않습니다."),
    INVALID_MAX_COUNT(HttpStatus.BAD_REQUEST, "모집 인원이 올바르지 않습니다. (최대 6명)"),
    OUT_OF_PERIOD(HttpStatus.BAD_REQUEST, "모집 기간이 아닙니다."),
    ALREADY_APPLIED(HttpStatus.CONFLICT, "이미 신청했습니다."),
    RECRUITMENT_FULL(HttpStatus.CONFLICT, "모집이 마감되었습니다."),
    NO_APPLICATION(HttpStatus.NOT_FOUND, "신청 내역이 없습니다."),
    NO_ACTIVE_RECRUITMENT(HttpStatus.NOT_FOUND, "진행 중인 모집 공고가 없습니다."),
    RECRUITMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "모집 공고를 찾을 수 없습니다."),
    RECRUITMENT_CLOSED(HttpStatus.CONFLICT, "이미 종료된 모집입니다."),
    CLASS_MISMATCH(HttpStatus.FORBIDDEN, "본인 학년/반의 모집에만 신청할 수 있습니다."),
    MAX_COUNT_BELOW_APPLICANTS(HttpStatus.CONFLICT, "모집 인원을 현재 신청 인원보다 적게 설정할 수 없습니다."),

    // cleaning-areas
    INACTIVE_AREA(HttpStatus.BAD_REQUEST, "비활성 구역에는 배정할 수 없습니다."),
    STUDENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 학생입니다."),
    NO_ASSIGNMENT(HttpStatus.NOT_FOUND, "배정된 구역이 없습니다."),
    AREA_NOT_FOUND(HttpStatus.NOT_FOUND, "구역 정보를 찾을 수 없습니다."),
    ZONE_MODEL_NOT_READY(HttpStatus.CONFLICT, "AI 모델이 준비되지 않은 구역은 활성화할 수 없습니다."),

    // verifications
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "읽을 수 없는 이미지입니다."),
    OUT_OF_CERTIFICATION_TIME(HttpStatus.FORBIDDEN, "인증 시간이 아닙니다."),
    VACATION_PERIOD(HttpStatus.FORBIDDEN, "방학 기간에는 인증할 수 없습니다."),
    ALREADY_SUBMITTED_TODAY(HttpStatus.CONFLICT, "오늘 이미 제출했습니다."),
    NOT_ASSIGNED_AREA(HttpStatus.FORBIDDEN, "배정된 구역에서만 인증할 수 있습니다."),
    VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "인증 내역을 찾을 수 없습니다."),

    // ai-review
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "검수 대상을 찾을 수 없습니다."),
    NOT_MANUAL_REVIEW(HttpStatus.CONFLICT, "수동 검토 상태가 아니거나 이미 처리되었습니다."),

    // activities
    NO_SEARCH_RESULT(HttpStatus.NOT_FOUND, "검색 결과가 없습니다."),

    // appeals
    APPEAL_NOT_ALLOWED(HttpStatus.CONFLICT, "반려된 본인 인증에만 이의신청할 수 있습니다."),
    APPEAL_ALREADY_PENDING(HttpStatus.CONFLICT, "검토 중인 이의신청이 있습니다."),
    INVALID_DECISION(HttpStatus.BAD_REQUEST, "승인 또는 반려만 선택할 수 있습니다."),
    ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 신청입니다."),
    TOO_MANY_APPEAL_PHOTOS(HttpStatus.BAD_REQUEST, "이의신청 사진은 최대 3장까지 첨부할 수 있습니다."),
    APPEAL_NOT_FOUND(HttpStatus.NOT_FOUND, "이의신청을 찾을 수 없습니다."),

    // notices
    TITLE_OR_CONTENT_EMPTY(HttpStatus.BAD_REQUEST, "제목 또는 내용을 입력해야 합니다."),
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 공지입니다."),
}
