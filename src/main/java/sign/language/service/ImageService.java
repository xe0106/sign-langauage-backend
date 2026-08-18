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

    // Cloudflare R2 퍼블릭 도메인 주소 주입 (기본값 설정)
    @Value("${app.public-s3-url:https://pub-9fe4fb0ab27c4f93b468525639d75c4e.r2.dev}")
    private String publicS3Url;

    /**
     * 이미지 파일을 S3/R2에 업로드하고 외부 접근이 가능한 퍼블릭 URL 반환
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
            // S3/R2에 파일 업로드
            s3Template.upload(bucketName, storeFilename, inputStream);

            // S3 내부 resource URL 대신 브라우저에서 바로 열리는 퍼블릭 URL 조합하여 반환
            return publicS3Url + "/" + storeFilename;

        } catch (IOException e) {
            throw new ImageException(ErrorStatus.FILE_UPLOAD_FAILED);
        }
    }

    private void validateImageExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new ImageException(ErrorStatus.INVALID_FILE_EXTENSION);
        }
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();

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