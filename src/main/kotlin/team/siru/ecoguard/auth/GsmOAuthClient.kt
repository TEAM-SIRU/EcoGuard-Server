package team.siru.ecoguard.auth

/**
 * dataGSM OAuth 인가 코드를 사용자 정보로 교환하는 클라이언트.
 *
 * dataGSM의 실제 토큰 교환 API 스펙(엔드포인트, 요청/응답 필드)이 명세서에
 * 포함되어 있지 않아, 실제 연동 구현체는 별도로 채워 넣어야 한다.
 * 기본적으로는 [GsmOAuthMockClient]가 동작하여 로컬 개발/테스트가 가능하다.
 */
interface GsmOAuthClient {
    fun authenticate(authCode: String): GsmUserInfo
}
