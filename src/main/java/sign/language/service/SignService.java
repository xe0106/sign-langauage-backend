package sign.language.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import sign.language.domain.User;
import sign.language.util.JwtTokenProvider;

import java.util.ArrayList;
import java.util.List;

@Service
public class SignService {

    private final List<User> userList = new ArrayList<>();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(); // 🔐 암호화 도구
    private final JwtTokenProvider jwtTokenProvider; // 🔑 토큰 공급 도구

    // 더미 데이터
    public SignService(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
        userList.add(new User("testId", passwordEncoder.encode("password123"), "홍길동"));
    }

    // 회원가입 로직
    public boolean signUp(String id, String password, String name) {
        // ID 중복 검사
        for (User user : userList) {
            if (user.getId().equals(id)) {
                return false;
            }
        }
        // 비밀번호 암호화
        String encryptedPassword = passwordEncoder.encode(password);
        userList.add(new User(id, encryptedPassword, name));
        return true;
    }

    // 토큰 발행 성공 시 토큰값 반환, 실패 시 null 반환
    public String signIn(String id, String password) {
        for (User user : userList) {
            if (user.getId().equals(id)) {
                // 입력된 평문 비밀번호와 암호화되어 저장된 비밀번호 대조
                if (passwordEncoder.matches(password, user.getPassword())) {
                    // 로그인 성공 시 JWT 토큰을 발행
                    return jwtTokenProvider.createToken(id);
                }
            }
        }
        return null; // 로그인 실패
    }

    // ID로 유저 정보 조회 (추후 로그인 유지 확인 목적)
    public User findById(String id) {
        return userList.stream()
                .filter(u -> u.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}