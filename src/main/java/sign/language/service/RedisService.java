package sign.language.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;

    // Refresh Token 저장 (만료시간 포함)
    public void setRefreshToken(String email, String refreshToken, long timeoutMilliseconds) {
        redisTemplate.opsForValue().set(
                "RT:" + email,
                refreshToken,
                timeoutMilliseconds,
                TimeUnit.MILLISECONDS
        );
    }

    // Refresh Token 조회
    public String getRefreshToken(String email) {
        return redisTemplate.opsForValue().get("RT:" + email);
    }

    // Refresh Token 삭제 (로그아웃 / 탈퇴 시 사용)
    public void deleteRefreshToken(String email) {
        redisTemplate.delete("RT:" + email);
    }
}