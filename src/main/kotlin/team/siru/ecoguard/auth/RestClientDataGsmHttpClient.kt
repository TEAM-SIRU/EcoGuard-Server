package team.siru.ecoguard.auth

import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import team.themoment.datagsm.sdk.oauth.http.ErrorResponseParser
import team.themoment.datagsm.sdk.oauth.http.HttpClient
import java.nio.charset.StandardCharsets

/**
 * DataGSM SDK가 HTTP 요청을 보낼 때 우리 RestClient를 쓰게 하는 연결부.
 * SDK 기본 HTTP 클라이언트(OkHttp, 타임아웃 30초) 대신 우리 타임아웃 설정을 그대로 쓰고,
 * 테스트에서는 MockRestServiceServer로 DataGSM 응답을 흉내 낼 수 있다.
 * 실패 응답은 SDK와 똑같이 ErrorResponseParser로 SDK 예외(ForbiddenException 등)로 바꾼다.
 */
class RestClientDataGsmHttpClient(
    private val restClient: RestClient,
) : HttpClient {

    override fun get(url: String, headers: Map<String, String>?, queryParams: Map<String, String>?): String {
        val uri = UriComponentsBuilder.fromUriString(url)
            .apply { queryParams?.forEach { (key, value) -> queryParam(key, value) } }
            .encode()
            .build()
            .toUri()
        return send(restClient.get().uri(uri), headers)
    }

    override fun post(url: String, headers: Map<String, String>?, body: String): String =
        send(restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body), headers)

    override fun put(url: String, headers: Map<String, String>?, body: String): String =
        send(restClient.put().uri(url).contentType(MediaType.APPLICATION_JSON).body(body), headers)

    private fun send(request: RestClient.RequestHeadersSpec<*>, headers: Map<String, String>?): String =
        request
            .headers { h -> headers?.forEach { (name, value) -> h.set(name, value) } }
            .exchange { _, response ->
                val body = response.body.readAllBytes().toString(StandardCharsets.UTF_8)
                if (!response.statusCode.is2xxSuccessful) {
                    throw ErrorResponseParser.toException(response.statusCode.value(), body)
                }
                body
            }

    /** RestClient는 스프링이 관리하므로 따로 닫을 것이 없다. */
    override fun close() = Unit
}
