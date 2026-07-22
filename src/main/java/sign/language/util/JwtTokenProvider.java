package sign.language.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final Key key;

    // Access Token 유효기간: 1시간
    private static final long ACCESS_TOKEN_VALIDITY_IN_MS = 1000L * 60 * 60;

    // Refresh Token 유효기간: 14일
    public static final long REFRESH_TOKEN_VALIDITY_IN_MS = 1000L * 60 * 60 * 24 * 14;

    // application.yml에서 jwt.secret 값을 가져옴
    public JwtTokenProvider(@Value("${jwt.secret}") String secretKey) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Access Token 생성 (유효기간: 1시간)
     */
    public String createAccessToken(String email) {
        return createToken(email, ACCESS_TOKEN_VALIDITY_IN_MS);
    }

    /**
     * Refresh Token 생성 (유효기간: 14일)
     */
    public String createRefreshToken(String email) {
        return createToken(email, REFRESH_TOKEN_VALIDITY_IN_MS);
    }

    /**
     * 공통 토큰 생성 메서드
     */
    private String createToken(String email, long validityInMilliseconds) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key)
                .compact();
    }

    /**
     * 토큰에서 유저 이메일 추출
     */
    public String getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    /**
     * 토큰 유효성 검증
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 토큰의 남은 만료시간(ms) 계산
     */
    public long getExpiration(String token) {
        Date expiration = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration();

        long now = new Date().getTime();
        return (expiration.getTime() - now);
    }
}