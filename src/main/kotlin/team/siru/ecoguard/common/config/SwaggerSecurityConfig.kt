package team.siru.ecoguard.common.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

private const val SWAGGER_ROLE = "SWAGGER"

/**
 * Swagger 문서 경로는 앱의 JWT 로그인과 별개로, 이 설정의 아이디·비밀번호(HTTP Basic)로만 열린다.
 * 문서를 켠 경우에만 만들어지며, 꺼져 있으면 이 경로도 다른 API처럼 인증을 요구한다.
 */
@Configuration
@ConditionalOnProperty(prefix = "swagger", name = ["enabled"], havingValue = "true")
class SwaggerSecurityConfig(
    private val properties: SwaggerProperties,
) {

    @Bean
    @Order(1)
    fun swaggerFilterChain(http: HttpSecurity): SecurityFilterChain {
        val user = User.withUsername(properties.username)
            .password(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(properties.password))
            .roles(SWAGGER_ROLE)
            .build()

        http
            .securityMatcher("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().hasRole(SWAGGER_ROLE) }
            .userDetailsService(InMemoryUserDetailsManager(user))
            .httpBasic(Customizer.withDefaults())

        return http.build()
    }
}
