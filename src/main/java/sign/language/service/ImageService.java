package sign.language.service;

import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sign.language.errorcode.ErrorStatus;
import sign.language.exception.ImageException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final S3Template s3Template;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    /**
     * 이미지 파일을 S3에 업로드하고 저장된 URL 반환
     */
    public String uploadImage(MultipartFile file) {
        // 파일이 비어있는지 검증
        if (file == null || file.isEmpty()) {
            throw new ImageException(ErrorStatus.INVALID_FILE);
        }

        // 확장자 검증 (이미지 파일 여부 체크)
        validateImageExtension(file.getOriginalFilename());

        // 파일명 중복 방지를 위한 UUID 생성
        String originalFilename = file.getOriginalFilename();
        String storeFilename = createStoreFilename(originalFilename);

        try (InputStream inputStream = file.getInputStream()) {
            // S3에 파일 업로드
            var resource = s3Template.upload(bucketName, storeFilename, inputStream);

            // 업로드된 파일의 S3 URL 반환
            return resource.getURL().toString();
        } catch (IOException e) {
            throw new ImageException(ErrorStatus.FILE_UPLOAD_FAILED);
        }
    }

    // 💡 확장자 검증 메서드 예시
    private void validateImageExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new ImageException(ErrorStatus.INVALID_FILE_EXTENSION);
        }
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();

        // 허용할 이미지 확장자 목록
        List<String> allowedExtensions = List.of("jpg", "jpeg", "png", "gif", "webp");
        if (!allowedExtensions.contains(extension)) {
            throw new ImageException(ErrorStatus.INVALID_FILE_EXTENSION);
        }
    }

    private String createStoreFilename(String originalFilename) {
        String ext = extractExt(originalFilename);
        String uuid = UUID.randomUUID().toString();
        return uuid + "." + ext;
    }

    private String extractExt(String originalFilename) {
        if (originalFilename == null) return "png";
        int pos = originalFilename.lastIndexOf(".");
        return originalFilename.substring(pos + 1);
    }
}