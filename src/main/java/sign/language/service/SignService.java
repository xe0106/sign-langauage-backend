package sign.language.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sign.language.domain.User;
import sign.language.request.SignUpRequest;
import sign.language.repository.UserRepository;
import sign.language.util.JwtTokenProvider;

import java.time.Instant;

@Service
public class SignService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtTokenProvider jwtTokenProvider;

    public SignService(UserRepository userRepository, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    // 닉네임 중복 확인
    @Transactional(readOnly = true)
    public boolean checkNicknameAvailable(String nickname) {
        return !userRepository.existsByNickname(nickname);
    }

    // 회원가입
    @Transactional
    public boolean signUp(SignUpRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return false; // 이미 가입된 이메일
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword())); // 비밀번호 암호화
        user.setName(request.getName());
        user.setNickname(request.getNickname());
        user.setGender(request.getGender());
        user.setBirthDate(request.getBirthDate());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setLearningDays(0);
        user.setNotificationEnabled(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        userRepository.save(user);
        return true;
    }

    // 로그인 (JWT 토큰 리턴)
    @Transactional(readOnly = true)
    public String signIn(String email, String password) {
        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null && passwordEncoder.matches(password, user.getPasswordHash())) {
            // 로그인 성공 시 JWT 토큰 생성 (Email 기준)
            return jwtTokenProvider.createToken(user.getEmail());
        }
        return null; // 로그인 실패
    }

    // 이메일로 유저 정보 조회
    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }
}