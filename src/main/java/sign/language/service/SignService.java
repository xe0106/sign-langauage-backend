package sign.language.service;

import org.springframework.beans.factory.annotation.Value;
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

import static sign.language.util.JwtTokenProvider.REFRESH_TOKEN_VALIDITY_IN_MS;

@Service
public class SignService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisService redisService;

    @Value("${app.default-profile-image}")
    private String defaultProfileImage;

    public SignService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider, RedisService redisService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisService = redisService;
    }

    // 닉네임 중복 검사
    @Transactional(readOnly = true)
    public String checkNicknameDuplicate(String nickname) {
        if (userRepository.existsByNickname(nickname)) {
            throw new SignException(ErrorStatus.DUPLICATE_NICKNAME);
        }
        return "사용 가능한 닉네임입니다."; // 사용 가능
    }

    // 회원가입
    @Transactional
    public String signUp(SignUpRequest request) {
        // 이메일 중복 검사
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new SignException(ErrorStatus.DUPLICATE_EMAIL);
        }

        // 전화번호 중복 검사
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new SignException(ErrorStatus.DUPLICATE_PHONE_NUMBER);
        }

        // 이중 검사
        checkNicknameDuplicate(request.getNickname());

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        String targetProfileImage = StringUtils.hasText(request.getProfileImageUrl())
                ? request.getProfileImageUrl()
                : defaultProfileImage;

        User user = User.create(
                request.getEmail(),
                encodedPassword,
                request.getName(),
                request.getNickname(),
                request.getGender(),
                request.getBirthDate(),
                request.getPhoneNumber(),
                targetProfileImage
        );

        userRepository.save(user);
        return user.getName();
    }

    // 로그인
    @Transactional(readOnly = true)
    public SignResponse.SignInResult signIn(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new SignException(ErrorStatus.DELETED_MEMBER));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new SignException(ErrorStatus.INVALID_PASSWORD);
        }

        // 1. Access Token & Refresh Token 생성
        String accessToken = jwtTokenProvider.createAccessToken(user.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getEmail());

        // 2. Redis에 Refresh Token 저장 (만료시간 14일 설정)
        redisService.setRefreshToken(user.getEmail(), refreshToken, REFRESH_TOKEN_VALIDITY_IN_MS);

        String userName = (user.getName() != null) ? user.getName() : "";
        user.setLogin();

        // 3. DTO 반환 (grantType, accessToken, refreshToken 전달)
        return new SignResponse.SignInResult(userName, "Bearer", user.getId(), accessToken, refreshToken);
    }

    @Transactional
    public void signOut(String email) {
        redisService.deleteRefreshToken(email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new SignException(ErrorStatus.DELETED_MEMBER));
        user.setLogOut();
    }

    // 회원 탈퇴 (소프트 삭제 및 데이터 익명화)
    @Transactional
    public void signOff(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new SignException(ErrorStatus.DELETED_MEMBER));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new SignException(ErrorStatus.INVALID_PASSWORD);
        }
        // 소프트 삭제 및 익명화 실행
        user.withdraw();
        redisService.deleteRefreshToken(email);
    }

    // 프로필 정보 수정
    @Transactional
    public void signModify(String email, ProfileModifyRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new SignException(ErrorStatus.DELETED_MEMBER));

        // 닉네임 변경 요청이 있고, 기존 닉네임과 다를 때만 수행
        String newNickname = null;
        if (StringUtils.hasText(request.getNickname()) && !request.getNickname().equals(user.getNickname())) {
            if (userRepository.existsByNickname(request.getNickname())) {
                throw new SignException(ErrorStatus.DUPLICATE_NICKNAME);
            }
            newNickname = request.getNickname();
        }

        String targetProfileImage = user.getProfileImageUrl(); // 기본값은 기존 이미지 유지

        if (StringUtils.hasText(request.getProfileImageUrl())) {
            if ("DEFAULT".equalsIgnoreCase(request.getProfileImageUrl())) {
                targetProfileImage = defaultProfileImage; // 기본 이미지로 초기화 요청 시
            } else {
                targetProfileImage = request.getProfileImageUrl(); // 새 이미지로 변경
            }
        }

        user.updateProfile(
                newNickname,
                request.getGender(),
                request.getBirthDate(),
                StringUtils.hasText(request.getPhoneNumber()) ? request.getPhoneNumber() : null,
                targetProfileImage
        );

        user.setUpdatedAt(Instant.now());
    }
}