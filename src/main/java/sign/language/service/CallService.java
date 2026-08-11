package sign.language.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sign.language.domain.CallSession;
import sign.language.domain.CallSubtitle;
import sign.language.domain.User;
import sign.language.errorcode.ErrorStatus;
import sign.language.exception.CallException;
import sign.language.repository.CallRepository;
import sign.language.repository.SubtitleRepository;
import sign.language.repository.UserRepository;
import sign.language.request.CallCreateRequest;
import sign.language.request.CallSessionRequest;
import sign.language.request.CallSubtitleRequest;
import sign.language.response.CallSessionResponse;
import sign.language.response.CallSubtitleResponse;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CallService {

    private final CallRepository callRepository;
    private final UserRepository userRepository;
    private final SubtitleRepository subtitleRepository;

    /**
     * 1. 통화 세션 생성 (전화 걸기) 및 DB 저장
     */
    @Transactional
    public CallSessionResponse createCall(CallCreateRequest request) {
        // ⭐️ 자기 자신에게 통화 시도 시 예외 발생
        if (request.getCallerId().equals(request.getReceiverId())) {
            throw new CallException(ErrorStatus.CANNOT_CALL_SELF); // 커스텀 에러 상태 추가
        }

        User caller = userRepository.findById(request.getCallerId())
                .orElseThrow(() -> new CallException(ErrorStatus.CALLER_NOT_FOUND));
        User receiver = userRepository.findById(request.getReceiverId())
                .orElseThrow(() -> new CallException(ErrorStatus.RECEIVER_NOT_FOUND));

        CallSession session = CallSession.create(caller, receiver);
        CallSession savedSession = callRepository.save(session);

        return CallSessionResponse.from(savedSession);
    }

    /**
     * 2. 화상 통화 상태 변경 (수락: CONNECTED, 거절: REJECTED, 종료: ENDED)
     */
    @Transactional
    public CallSessionResponse updateCallStatus(String callId, CallSessionRequest request) {
        CallSession session = callRepository.findById(callId)
                .orElseThrow(() -> new CallException(ErrorStatus.SESSION_NOT_FOUND));

        CallSession.Status newStatus;
        try {
            // 문자열을 Enum으로 변환 (REJECTED, ENDED, RINGING, CONNECTED 외의 값이면 예외 발생)
            newStatus = CallSession.Status.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            // 유효하지 않은 통화 상태값 전달 시 커스텀 예외 던지기
            throw new CallException(ErrorStatus.INVALID_CALL_STATUS); // 사용하시는 에러상태 객체로 지정
        }
        session.updateStatus(newStatus);

        return CallSessionResponse.from(session);
    }

    /**
     * 3. 통화 자막 전송 및 DB 영구 저장
     */
    @Transactional
    public CallSubtitleResponse saveSubtitle(String callId, CallSubtitleRequest request) {
        CallSession session = callRepository.findById(callId)
                .orElseThrow(() -> new CallException(ErrorStatus.SESSION_NOT_FOUND));

        User sender = userRepository.findById(request.getSenderId())
                .orElseThrow(() -> new CallException(ErrorStatus.RECEIVER_NOT_FOUND));

        // ⭐️ 통화가 연결된 상태가 아니면(종료/거절/대기 중 등) 자막 저장 불가
        if (session.getStatus() != CallSession.Status.CONNECTED) {
            throw new CallException(ErrorStatus.INVALID_CALL_STATUS);
        }

        CallSubtitle subtitle = CallSubtitle.create(session, sender, request.getTextContent());
        CallSubtitle savedSubtitle = subtitleRepository.save(subtitle);

        return CallSubtitleResponse.from(savedSubtitle);
    }

    /**
     * 4. 특정 통화 세션의 전체 자막 기록 DB 조회
     */
    public List<CallSubtitleResponse> getSubtitles(String callId) {
        List<CallSubtitle> subtitles = subtitleRepository.findByCall_CallIdOrderByCreatedAtAsc(callId);

        CallSession session = callRepository.findById(callId)
                .orElseThrow(() -> new CallException(ErrorStatus.SESSION_NOT_FOUND));

        return subtitles.stream()
                .map(CallSubtitleResponse::from)
                .collect(Collectors.toList());
    }
}