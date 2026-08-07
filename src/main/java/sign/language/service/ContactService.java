package sign.language.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import sign.language.domain.Contact;
import sign.language.domain.User;
import sign.language.dto.ContactResponse;
import sign.language.errorcode.ErrorStatus;
import sign.language.exception.ContactException;
import sign.language.repository.ContactRepository;
import sign.language.repository.UserRepository;
import sign.language.request.ContactRequest;
import sign.language.response.ContactCreateResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactService {

    private final UserRepository userRepository;
    private final ContactRepository contactRepository;

    @Value("${app.default-profile-image}")
    private String defaultProfileImage;

    /**
     * 모든 연락처 조회 (이름순)
     */
    public List<ContactResponse> getAllContacts(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ContactException(ErrorStatus.DELETED_MEMBER));

        List<Contact> contacts = contactRepository.findAllByUserIdWithTargetUser(user.getId());

        // 등록된 연락처가 하나도 없는 경우
        if (contacts.isEmpty()) {
            throw new ContactException(ErrorStatus.CONTACT_NOT_FOUND);
        }

        return contacts.stream()
                .map(ContactResponse::from)
                .toList();
    }

    /**
     * 최근 연락처 조회 (기본 상위 5개 or 지정한 limit)
     */
    public List<ContactResponse> getRecentContacts(String email, int limit) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ContactException(ErrorStatus.DELETED_MEMBER));

        PageRequest pageRequest = PageRequest.of(0, limit);
        List<Contact> recentContacts = contactRepository.findRecentContactsByUserId(user.getId(), pageRequest);

        // 최근 연락처(통화 기록)가 하나도 없는 경우
        if (recentContacts.isEmpty()) {
            throw new ContactException(ErrorStatus.CONTACT_NOT_FOUND);
        }

        return recentContacts.stream()
                .map(ContactResponse::from)
                .toList();
    }

    /**
     * 새로운 연락처(친구) 추가
     */
    @Transactional
    public ContactCreateResponse addContact(String email, ContactRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ContactException(ErrorStatus.DELETED_MEMBER));

        User targetUser = userRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseThrow(() -> new ContactException(ErrorStatus.MEMBER_NOT_FOUND));

        if (user.getId().equals(targetUser.getId())) {
            throw new ContactException(ErrorStatus.CANNOT_ADD_SELF);
        }

        if (contactRepository.existsByUserIdAndTargetUserId(user.getId(), targetUser.getId())) {
            throw new ContactException(ErrorStatus.CONTACT_ALREADY_EXISTS);
        }

        String targetProfileImage = StringUtils.hasText(request.getProfileImageUrl())
                ? request.getProfileImageUrl()
                : defaultProfileImage;

        Contact contact = Contact.createContact(
                user,
                targetUser,
                request.getContactName(),
                targetProfileImage
        );

        Contact savedContact = contactRepository.save(contact);

        return ContactCreateResponse.from(savedContact);
    }

    /**
     * 등록된 연락처 삭제
     */
    @Transactional
    public void deleteContact(String email, Long contactId) {
        // 로그인한 유저 확인
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ContactException(ErrorStatus.DELETED_MEMBER));

        // 해당 유저의 연락처 존재 여부 검증 (없으면 404 CONTACT404)
        Contact contact = contactRepository.findByUserIdAndTargetUserId(user.getId(), contactId)
                .orElseThrow(() -> new ContactException(ErrorStatus.CONTACT_NOT_FOUND));

        // 연락처 삭제
        contactRepository.delete(contact);
    }
}