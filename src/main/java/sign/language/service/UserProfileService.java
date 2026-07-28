package sign.language.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sign.language.domain.User;
import sign.language.errorcode.ErrorStatus;
import sign.language.exception.UserProfileException;
import sign.language.repository.UserRepository;
import sign.language.response.UserProfileResponse;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;

    @Value("${app.default-profile-image}")
    private String defaultProfileImage;

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfileByEmail(String email) {
        // 이메일로 회원 조회 (없을 경우 404 예외)
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserProfileException(ErrorStatus.MEMBER_NOT_FOUND)); // USER404

        // 프로필 이미지가 없으면 yml의 기본 이미지 URL 사용
        String imageUrl = (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isBlank())
                ? user.getProfileImageUrl()
                : defaultProfileImage;

        // DTO 반환
        return UserProfileResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .profileImageUrl(imageUrl)
                .learningDays(user.getLearningDays()) // 연속 학습 일수
                .build();
    }

    // 특정 사용자 프로필 조회 (userId 기반)
    public UserProfileResponse getUserProfileByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserProfileException(ErrorStatus.MEMBER_NOT_FOUND));

        // 프로필 이미지가 없으면 yml의 기본 이미지 URL 사용
        String imageUrl = (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isBlank())
                ? user.getProfileImageUrl()
                : defaultProfileImage;

        if (user.getStatus() == User.Status.DELETED) {
            throw new UserProfileException(ErrorStatus.MEMBER_NOT_FOUND);
        }

        return UserProfileResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .profileImageUrl(imageUrl)
                .learningDays(user.getLearningDays()) // 연속 학습 일수
                .build();
    }
}