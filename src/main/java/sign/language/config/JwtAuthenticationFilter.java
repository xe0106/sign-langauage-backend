package sign.language.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import sign.language.errorcode.ErrorStatus;
import sign.language.exception.SignException;
import sign.language.util.JwtTokenProvider;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final HandlerExceptionResolver resolver;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim().replace("\"", "");

            try {
                // 토큰 유효성 검사
                if (jwtTokenProvider.validateToken(token)) {
                    String email = jwtTokenProvider.getUserIdFromToken(token);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    email,
                                    null,
                                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                            );

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    // 토큰 무효 시 SignException(FORBIDDEN) 예외를 GlobalExceptionHandler로 처리
                    resolver.resolveException(request, response, null, new SignException(ErrorStatus.FORBIDDEN));
                    return;
                }
            } catch (Exception e) {
                // 토큰 파싱 중 에러 발생 시 처리
                resolver.resolveException(request, response, null, new SignException(ErrorStatus.FORBIDDEN));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}