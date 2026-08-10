package sign.language.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sign.language.request.CallCreateRequest;
import sign.language.request.CallSessionRequest;
import sign.language.request.CallSubtitleRequest;
import sign.language.response.CallSessionResponse;
import sign.language.response.CallSubtitleResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 화상 통화 비즈니스 로직 서비스
 * 
 * 통화 세션 생성, 통화 상태(수락/거절/종료) 변경,
 * 자막 저장 및 조회 비즈니스 처리
 */
@Service
@RequiredArgsConstructor
public class CallService {

    // private final CallRepository callRepository; // 실제 DB 연결 시 의존성 주입

    /**
     * 1. 통화 세션 생성 (전화 걸기)
     * 
     * @param request 발신자 및 수신자 정보
     * @return 고유한 callId가 포함된 초기 통화 세션 응답 객체 (초기 상태: RINGING)
     */
    public CallSessionResponse createCall(CallCreateRequest request) {
        // 랜덤한 통화 고유 UUID 생성
        String newCallId = UUID.randomUUID().toString();

        return CallSessionResponse.builder()
                .callId(newCallId)
                .callerId(request.getCallerId())
                .receiverId(request.getReceiverId())
                .status("RINGING") // 전화 발신 중 상태
                .startedAt(LocalDateTime.now())
                .endedAt(null)
                .build();
    }

    /**
     * 2. 화상 통화 상태 변경 (수락: CONNECTED, 거절: REJECTED, 종료: ENDED)
     * 
     * @param callId 통화 고유 ID
     * @param request 변경 요청할 상태 정보
     * @return 업데이트된 통화 세션 응답 객체
     */
    public CallSessionResponse updateCallStatus(String callId, CallSessionRequest request) {
        // TODO: DB 연결 시 callId로 통화 세션 조회 후 존재하지 않을 경우 예외 발생 처리
        // if (세션이 없다면) {
        //     throw new CallException(ErrorStatus.CALL_NOT_FOUND);
        // }

        LocalDateTime endedAt = null;
        // 통화 종료(ENDED) 또는 거절(REJECTED) 시 종료 시간 기록
        if ("ENDED".equalsIgnoreCase(request.getStatus()) || "REJECTED".equalsIgnoreCase(request.getStatus())) {
            endedAt = LocalDateTime.now();
        }

        return CallSessionResponse.builder()
                .callId(callId)
                .callerId(1L)   // 테스트용 임시 기본 값
                .receiverId(2L) // 테스트용 임시 기본 값
                .status(request.getStatus())
                .startedAt(LocalDateTime.now().minusMinutes(5))
                .endedAt(endedAt)
                .build();
    }

    /**
     * 3. 통화 자막 전송 및 저장
     * 
     * @param callId 통화 고유 ID
     * @param request 자막 송신자 및 번역 텍스트 내용
     * @return 저장된 자막 정보 응답 객체
     */
    public CallSubtitleResponse saveSubtitle(String callId, CallSubtitleRequest request) {
        // TODO: 이미 종료된 통화인지 여부 검증
        // if (이미 종료된 통화라면) {
        //     throw new CallException(ErrorStatus.CALL_ALREADY_ENDED);
        // }

        return CallSubtitleResponse.builder()
                .subtitleId(105L) // 테스트용 임시 ID
                .callId(callId)
                .senderId(request.getSenderId())
                .textContent(request.getTextContent())
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 4. 특정 통화 세션의 자막 목록 조회
     * 
     * @param callId 통화 고유 ID
     * @return 해당 통화에서 기록된 자막 목록 리스트
     */
    public List<CallSubtitleResponse> getSubtitles(String callId) {
        // TODO: DB에서 callId로 자막 엔티티 리스트를 조회하는 로직 구현
        CallSubtitleResponse sample = CallSubtitleResponse.builder()
                .subtitleId(101L)
                .callId(callId)
                .senderId(1L)
                .textContent("안녕하세요 반갑습니다!")
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .build();

        return List.of(sample);
    }
}
