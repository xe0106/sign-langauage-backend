package sign.language.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sign.language.dto.ContactResponse;
import sign.language.request.ContactRequest;
import sign.language.response.ApiResponse;
import sign.language.response.ContactCreateResponse;
import sign.language.service.ContactService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sign/language/contacts")
public class ContactController {

    private final ContactService contactService;

    /**
     * 모든 연락처 조회 (이름순)
     * GET /sign/language/contacts
     */
    @GetMapping
    public ApiResponse<List<ContactResponse>> getAllContacts(
            @AuthenticationPrincipal String email
    ) {
        List<ContactResponse> contacts = contactService.getAllContacts(email);
        return ApiResponse.onSuccess(contacts);
    }

    /**
     * 새로운 연락처(친구) 추가
     * POST /sign/language/contacts/insert/{userId}
     */
    @PostMapping("/insert/{targetUserId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ContactCreateResponse> addContact(
            @AuthenticationPrincipal String email,
            @PathVariable Long targetUserId,
            @Valid @RequestBody ContactRequest request
    ) {
        ContactCreateResponse response = contactService.addContact(email, request, targetUserId);
        return ApiResponse.onSuccess(response);
    }

    /**
     * 등록된 연락처 삭제
     * DELETE /sign/language/contacts/delete/{contactId}
     */
    @DeleteMapping("/delete/{targetUserId}")
    public ApiResponse<Void> deleteContact(
            @AuthenticationPrincipal String email,
            @PathVariable Long targetUserId
    ) {
        contactService.deleteContact(email, targetUserId);
        return ApiResponse.onSuccess();
    }
}