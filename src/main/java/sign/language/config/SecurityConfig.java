package sign.language.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import sign.language.errorcode.ErrorStatus;
import sign.language.exception.SignException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final HandlerExceptionResolver resolver;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) {
        this.resolver = resolver;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/error",
                                "/sign/language/auth/signup",
                                "/sign/language/auth/signin",
                                "/sign/language/auth/check-nickname",
                                "/sign/language/images/upload",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/ws-stomp/**",
                                "/calls/**",
                                "/stomp-test.html"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                // JWT 필터 위치 배치
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // 시큐리티 인가/인증 예외 발생 시 -> GlobalExceptionHandler로 위임
                .exceptionHandling(exception -> exception
                        // 403 Forbidden (권한 부족 / 접근 불가)
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                resolver.resolveException(request, response, null, new SignException(ErrorStatus.FORBIDDEN))
                        )
                        // 401 Unauthorized (인증 정보 없음)
                        .authenticationEntryPoint((request, response, authException) ->
                                resolver.resolveException(request, response, null, new SignException(ErrorStatus.FORBIDDEN))
                        )
                );

        return http.build();
    }
}