package sign.language.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import sign.language.request.CallCreateRequest;
import sign.language.request.CallSessionRequest;
import sign.language.request.CallSubtitleRequest;
import sign.language.response.ApiResponse;
import sign.language.response.CallSessionResponse;
import sign.language.response.CallSubtitleResponse;
import sign.language.service.CallService;

import java.util.List;

/**
 * 영상 통화 관련 REST API 컨트롤러
 * 
 * - 통화 생성 (전화 걸기)
 * - 통화 상태 변경 (수락, 거절, 종료)
 * - 자막 저장 및 조회
 */
@RestController
@RequestMapping("/sign/language/call")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;

    /**
     * 1. 통화 요청 / 전화 걸기 API
     * 
     * @param request 발신자 ID(callerId)와 수신자 ID(receiverId) 정보
     * @return 생성된 통화 세션 정보 (callId, RINGING 상태 등)
     * HTTP Method: POST /calls
     */
    @PostMapping
    public ApiResponse<CallSessionResponse> createCall(@RequestBody CallCreateRequest request) {
        CallSessionResponse response = callService.createCall(request);
        return ApiResponse.onSuccess(response);
    }

    /**
     * 2. 화상 통화 상태 변경 API
     * 
     * @param callId 통화 세션 고유 ID
     * @param request 변경할 통화 상태 (CONNECTED, REJECTED, ENDED 등)
     * @return 상태가 업데이트된 통화 세션 정보
     * HTTP Method: PUT /calls/{callId}/status
     */
    @PutMapping("/{callId}/status")
    public ApiResponse<CallSessionResponse> updateCallStatus(
            @PathVariable String callId,
            @RequestBody CallSessionRequest request) {
        
        CallSessionResponse response = callService.updateCallStatus(callId, request);
        return ApiResponse.onSuccess(response);
    }

    /**
     * 3. 자막 전송 및 DB 저장 API
     * 
     * @param callId 통화 세션 고유 ID
     * @param request 자막 발신자 ID 및 수어 번역 텍스트 내용
     * @return 저장 완료된 자막 객체 정보
     * HTTP Method: POST /calls/{callId}/subtitles
     */
    @PostMapping("/{callId}/subtitles")
    public ApiResponse<CallSubtitleResponse> saveSubtitle(
            @PathVariable String callId,
            @RequestBody CallSubtitleRequest request) {
        
        CallSubtitleResponse response = callService.saveSubtitle(callId, request);
        return ApiResponse.onSuccess(response);
    }

    /**
     * 4. 특정 통화의 자막 목록 조회 API
     * 
     * @param callId 통화 세션 고유 ID
     * @return 해당 통화에서 발생한 자막 리스트
     * HTTP Method: GET /calls/{callId}/subtitles
     */
    @GetMapping("/{callId}/subtitles")
    public ApiResponse<List<CallSubtitleResponse>> getSubtitles(
            @PathVariable String callId) {
        
        List<CallSubtitleResponse> responses = callService.getSubtitles(callId);
        return ApiResponse.onSuccess(responses);
    }
}
