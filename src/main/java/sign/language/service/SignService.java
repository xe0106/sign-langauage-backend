package sign.language.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import sign.language.errorcode.ErrorStatus;
import sign.language.domain.User;
import sign.language.exception.SignException;
import sign.language.repository.UserRepository;
import sign.language.request.ProfileModifyRequest;
import sign.language.request.SignUpRequest;
import sign.language.response.SignResponse;
import sign.language.util.JwtTokenProvider;

import java.time.Instant;

@Service
public class SignService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public SignService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    // 회원가입
    @Transactional
    public String signUp(SignUpRequest request) {
        // 이메일 중복 검약
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new SignException(ErrorStatus.DUPLICATE_EMAIL);
        }

        // 닉네임 중복 검사
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new SignException(ErrorStatus.DUPLICATE_NICKNAME);
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
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
        return user.getName();
    }

    // 로그인
    @Transactional(readOnly = true)
    public SignResponse.SignInResult signIn(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new SignException(ErrorStatus.MEMBER_NOT_FOUND));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new SignException(ErrorStatus.INVALID_PASSWORD);
        }

        String token = jwtTokenProvider.createToken(user.getEmail());
        String userName = (user.getName() != null) ? user.getName() : "";

        return new SignResponse.SignInResult(userName, token);
    }

    // 회원 탈퇴 (소프트 삭제 및 데이터 익명화)
    @Transactional
    public void signOff(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new SignException(ErrorStatus.MEMBER_NOT_FOUND));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new SignException(ErrorStatus.INVALID_PASSWORD);
        }
        // 소프트 삭제 및 익명화 실행
        user.withdraw();
    }

    // 프로필 정보 수정
    @Transactional
    public void signModify(String email, ProfileModifyRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new SignException(ErrorStatus.MEMBER_NOT_FOUND));

        // 닉네임 변경 요청이 있고, 기존 닉네임과 다를 때만 수행
        if (StringUtils.hasText(request.getNickname()) && !request.getNickname().equals(user.getNickname())) {
            if (userRepository.existsByNickname(request.getNickname())) {
                throw new SignException(ErrorStatus.DUPLICATE_NICKNAME);
            }
            user.setNickname(request.getNickname());
        }

        if (request.getGender() != null) user.setGender(request.getGender());
        if (request.getBirthDate() != null) user.setBirthDate(request.getBirthDate());
        if (StringUtils.hasText(request.getPhoneNumber())) user.setPhoneNumber(request.getPhoneNumber());

        user.setUpdatedAt(Instant.now());
    }
}