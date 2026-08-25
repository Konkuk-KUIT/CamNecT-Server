package CamNecT.server.domain.activity.service;

import CamNecT.server.domain.activity.dto.request.RecruitmentApplyRequest;
import CamNecT.server.domain.activity.dto.request.RecruitmentRequest;
import CamNecT.server.domain.activity.dto.response.RecruitmentDetailResponse;
import CamNecT.server.domain.activity.model.enums.ActivityCategory;
import CamNecT.server.domain.activity.model.enums.RecruitStatus;
import CamNecT.server.domain.activity.model.external_activity.ExternalActivity;
import CamNecT.server.domain.activity.model.recruitment.RecruitmentBookmark;
import CamNecT.server.domain.activity.model.recruitment.TeamApplication;
import CamNecT.server.domain.activity.model.recruitment.TeamRecruitment;
import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityRepository;
import CamNecT.server.domain.activity.repository.recruitment.RecruitmentBookmarkRepository;
import CamNecT.server.domain.activity.repository.recruitment.TeamApplicationRepository;
import CamNecT.server.domain.activity.repository.recruitment.TeamRecruitmentRepository;
import CamNecT.server.domain.chat.model.ChatRequest;
import CamNecT.server.domain.chat.repository.ChatRequestRepository;
import CamNecT.server.domain.community.dto.AuthorDto;
import CamNecT.server.domain.community.service.AuthorAssembler;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.ErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.ActivityErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.CoffeeChatErrorCode;
import CamNecT.server.global.notification.event.SimpleNotifiableEvent;
import CamNecT.server.global.notification.model.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecruitmentService {

    private final TeamRecruitmentRepository recruitmentRepository;
    private final ExternalActivityRepository activityRepository;
    private final RecruitmentBookmarkRepository bookmarkRepository;
    private final TeamApplicationRepository teamApplicationRepository;
    private final UserRepository userRepository;
    private final ChatRequestRepository chatRequestRepository;

    private final ApplicationEventPublisher eventPublisher;
    private final AuthorAssembler authorAssembler;

    @Transactional
    public TeamRecruitment createRecruitment(Long userId, RecruitmentRequest request) {
        requireAuthenticatedUser(userId);
        if (request == null || request.activityId() == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        //대외활동 검증
        ExternalActivity activity = activityRepository.findByIdForUpdate(request.activityId()).orElseThrow(
                ()-> new CustomException(ActivityErrorCode.ACTIVITY_NOT_FOUND)
        );

        if(activity.getCategory() == ActivityCategory.CLUB || activity.getCategory() == ActivityCategory.STUDY)
            throw new CustomException(ActivityErrorCode.INVALID_ACTIVITY_CATEGORY);

        TeamRecruitment recruitment = TeamRecruitment.builder()
                .activityId(request.activityId())
                .userId(userId)
                .title(request.title())
                .content(request.content())
                .recruitCount(request.recruitCount())
                .recruitDeadline(request.recruitDeadline())
                .createdAt(LocalDateTime.now())
                .build();

        return recruitmentRepository.save(recruitment);
    }

    public RecruitmentDetailResponse getRecruitmentDetail(Long currentUserId, Long recruitmentId) {
        requireAuthenticatedUser(currentUserId);

        //모집글 조회
        TeamRecruitment recruitment = recruitmentRepository.findById(recruitmentId)
                .orElseThrow(() -> new CustomException(ActivityErrorCode.RECRUITMENT_NOT_FOUND));
        // 글쓴이 프로필
        AuthorDto author = authorAssembler
                .buildAuthorMap(List.of(recruitment.getUserId()))
                .get(recruitment.getUserId());

        //북마크 여부 및 본인 글 여부 확인
        String activityTitle = activityRepository.findTitleByActivityId(recruitment.getActivityId()).orElseThrow(
                () -> new CustomException(ActivityErrorCode.ACTIVITY_NOT_FOUND)
        );
        boolean isBookmarked = bookmarkRepository.existsByUserIdAndRecruitId(currentUserId, recruitmentId);
        boolean isMine = recruitment.getUserId().equals(currentUserId);

        return new RecruitmentDetailResponse(
                author,
                recruitment,
                activityTitle,
                isMine,
                isBookmarked
        );
    }

    @Transactional
    public void updateRecruitment(Long userId, Long recruitmentId, RecruitmentRequest request) {
        requireAuthenticatedUser(userId);
        // 1. 모집글 조회
        TeamRecruitment recruitment = recruitmentRepository.findByIdForUpdate(recruitmentId)
                .orElseThrow(() -> new CustomException(ActivityErrorCode.RECRUITMENT_NOT_FOUND));

        // 2. 작성자 본인 확인
        if (!Objects.equals(recruitment.getUserId(), userId)) {
            throw new CustomException(ActivityErrorCode.NOT_AUTHOR);
        }

        // 3. 마감된 모집글은 수정 불가
        if (recruitment.getRecruitStatus() == RecruitStatus.CLOSED) {
            throw new CustomException(ActivityErrorCode.ALREADY_CLOSED);
        }

        // 4. 수정 적용
        recruitment.update(request);
    }

    @Transactional
    public boolean toggleRecruitmentBookmark(Long userId, Long recruitId) {
        requireAuthenticatedUser(userId);
        //모집글 조회 (북마크 카운트 업데이트를 위해 엔티티 조회)
        TeamRecruitment recruitment = recruitmentRepository.findByIdForUpdate(recruitId)
                .orElseThrow(() -> new CustomException(ActivityErrorCode.RECRUITMENT_NOT_FOUND));

        userRepository.lockUserRow(userId);

        //북마크 존재 여부 확인
        Optional<RecruitmentBookmark> bookmarkOpt = bookmarkRepository.findByUserIdAndRecruitId(userId, recruitId);

        if (bookmarkOpt.isPresent()) {
            // 이미 존재하면 삭제 (북마크 취소)
            bookmarkRepository.delete(bookmarkOpt.get());
            recruitment.decrementBookmarkCount(); // 북마크 수 감소
            return false; // 북마크 해제됨을 의미
        } else {
            // 존재하지 않으면 생성 (북마크 등록)
            RecruitmentBookmark newBookmark = RecruitmentBookmark.builder()
                    .userId(userId)
                    .recruitId(recruitId)
                    .build();
            bookmarkRepository.save(newBookmark);
            recruitment.incrementBookmarkCount(); // 북마크 수 증가
            return true;
        }
    }

    @Transactional
    public Long applyToTeam(Long userId, Long recruitId, RecruitmentApplyRequest request) {
        Users requester = requireAuthenticatedUser(userId);

        //공고 존재 여부 확인
        TeamRecruitment recruitment = recruitmentRepository.findByIdForUpdate(recruitId)
                .orElseThrow(() -> new CustomException(ActivityErrorCode.RECRUITMENT_NOT_FOUND));

        userRepository.lockUserRow(userId);

        //본인이 작성한 글인지 확인 (본인 글에는 신청 불가)
        if (recruitment.getUserId().equals(userId)) {
            throw new CustomException(ActivityErrorCode.SELF_APPLY_NOT_ALLOWED);
        }

        //중복 신청 확인
        if (teamApplicationRepository.existsByRecruitIdAndUserId(recruitId, userId)) {
            throw new CustomException(ActivityErrorCode.ALREADY_APPLIED);
        }

        //요청 가능 상태인지 확인
        if (recruitment.getRecruitStatus() == RecruitStatus.CLOSED)
            throw new CustomException(ActivityErrorCode.RECRUITMENT_CLOSED);

        //신청 객체 생성 및 저장
        TeamApplication application = TeamApplication.builder()
                .recruitId(recruitId)
                .userId(userId)
                .content(request.content())
                .build();



        // 커피챗 요청 로직
        Users receiver = userRepository.findById(recruitment.getUserId())
                .orElseThrow(() -> new CustomException(AuthErrorCode.USER_NOT_FOUND));

        if (requester.equals(receiver)) {
            throw new CustomException(CoffeeChatErrorCode.SELF_REQUEST_NOT_ALLOWED);
        }

        if (chatRequestRepository.existsByRequester_UserIdAndReceiver_UserIdAndStatusAndTypeAndRecruitmentId(
                userId, recruitment.getUserId(), ChatRequest.RequestStatus.WAITING, ChatRequest.RequestType.TEAM_RECRUIT, recruitId)) {
            throw new CustomException(CoffeeChatErrorCode.DUPLICATE_REQUEST);
        }

        if (chatRequestRepository.existsByRequester_UserIdAndReceiver_UserIdAndStatusAndTypeAndRecruitmentId(
                userId, recruitment.getUserId(), ChatRequest.RequestStatus.ACCEPTED, ChatRequest.RequestType.TEAM_RECRUIT, recruitId)
                || chatRequestRepository.existsByRequester_UserIdAndReceiver_UserIdAndStatusAndTypeAndRecruitmentId(
                recruitment.getUserId(), userId, ChatRequest.RequestStatus.ACCEPTED, ChatRequest.RequestType.TEAM_RECRUIT, recruitId)) {
            throw new CustomException(CoffeeChatErrorCode.CHATROOM_ALREADY_EXISTS);
        }

        ChatRequest chatRequest = ChatRequest.builder()
                .requester(requester)
                .receiver(receiver)
                .content(request.content())
                .type(ChatRequest.RequestType.TEAM_RECRUIT) //팀원 모집으로 타입 설정하기
                .activityId(recruitment.getActivityId())
                .recruitmentId(recruitId)
                .build();



        Long chatRequestId = chatRequestRepository.save(chatRequest).getId();

        // 모집글 작성자에게 알림
        eventPublisher.publishEvent(SimpleNotifiableEvent.of(
                recruitment.getUserId(),                 // receiver = 모집글 작성자
                userId,                                  // actor = 지원자
                NotificationType.TEAM_APPLICATION_RECEIVED,
                "팀원 모집에 지원이 도착했습니다.",
                null,
                null,
                chatRequestId,
                null
        ));


        return teamApplicationRepository.save(application).getApplicationId();
    }

    @Transactional
    public void closeRecruitment(Long userId, Long recruitId) {
        requireAuthenticatedUser(userId);
        // 1. 모집글 조회
        TeamRecruitment recruitment = recruitmentRepository.findByIdForUpdate(recruitId)
                .orElseThrow(() -> new CustomException(ActivityErrorCode.RECRUITMENT_NOT_FOUND));

        // 2. 작성자 본인 확인
        if (!Objects.equals(recruitment.getUserId(), userId)) {
            throw new CustomException(ActivityErrorCode.NOT_AUTHOR);
        }

        // 3. 이미 마감된 경우
        if (recruitment.getRecruitStatus() == RecruitStatus.CLOSED) {
            throw new CustomException(ActivityErrorCode.ALREADY_CLOSED);
        }

        // 4. 상태를 CLOSED로 변경 (더티 체킹으로 자동 업데이트)
        recruitment.close();

        // 5. 응답과 경합하지 않도록 대기 요청을 잠근 뒤 거절한다.
        List<ChatRequest> targets = chatRequestRepository.findAllByRecruitmentIdAndTypeAndStatusForUpdate(
                ChatRequest.RequestType.TEAM_RECRUIT,
                recruitId,
                ChatRequest.RequestStatus.WAITING
        );

        targets.forEach(ChatRequest::reject);
    }

    /**
     * 팀원 모집글 삭제
     * - 작성자 또는 관리자만 삭제 가능
     * - 모집글은 삭제됨
     * - 모집글과 연관된 지원서와 북마크는 함께 삭제됨
     * - WAITING ChatRequest는 거절됨
     * - ACCEPTED/REJECTED ChatRequest와 ChatRoom의 recruitmentId는 이력으로 유지됨
     */
    @Transactional
    public void deleteRecruitment(Long userId, Long recruitId) {
        requireAuthenticatedUser(userId);
        
        // 1. 모집글 조회
        TeamRecruitment recruitment = recruitmentRepository.findByIdForUpdate(recruitId)
                .orElseThrow(() -> new CustomException(ActivityErrorCode.RECRUITMENT_NOT_FOUND));

        // 2. 작성자 본인 또는 관리자 확인
        boolean isAdmin = userRepository.existsByUserIdAndRole(userId, UserRole.ADMIN);
        boolean isAuthor = Objects.equals(recruitment.getUserId(), userId);

        if (!isAdmin && !isAuthor) {
            throw new CustomException(ActivityErrorCode.NOT_AUTHOR);
        }

        // 3. 수락 처리와 경합하지 않도록 대기 요청을 잠근 뒤 거절
        List<ChatRequest> waitingRequests = chatRequestRepository.findAllByRecruitmentIdAndTypeAndStatusForUpdate(
                ChatRequest.RequestType.TEAM_RECRUIT,
                recruitId,
                ChatRequest.RequestStatus.WAITING
        );
        waitingRequests.forEach(ChatRequest::reject);

        // 4. 모집글 연관 지원서와 북마크 삭제
        teamApplicationRepository.deleteByRecruitId(recruitId);
        bookmarkRepository.deleteByRecruitId(recruitId);

        // 5. 모집글 삭제 (처리 완료된 ChatRequest/ChatRoom과 연결 ID는 유지)
        recruitmentRepository.delete(recruitment);
    }

    private Users requireAuthenticatedUser(Long userId) {
        if (userId == null) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN);
        }
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }
        return user;
    }
}
