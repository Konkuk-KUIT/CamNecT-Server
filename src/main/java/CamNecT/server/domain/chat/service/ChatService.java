package CamNecT.server.domain.chat.service;


import CamNecT.server.domain.activity.model.recruitment.TeamRecruitment;
import CamNecT.server.domain.activity.repository.recruitment.TeamRecruitmentRepository;
import CamNecT.server.domain.alumni.dto.ProfileCardDto;
import CamNecT.server.domain.chat.dto.message.ChatMessageAckResponseDto;
import CamNecT.server.domain.chat.dto.message.ChatMessageResponseDto;
import CamNecT.server.domain.chat.dto.message.ChatMessageSendRequestDto;
import CamNecT.server.domain.chat.dto.message.ChatReadEvent;
import CamNecT.server.domain.chat.event.ChatMessageCommittedEvent;
import CamNecT.server.domain.chat.event.ChatReadCommittedEvent;
import CamNecT.server.domain.chat.dto.request.response.ChatRequestDetailDto;
import CamNecT.server.domain.chat.dto.request.response.ChatRequestListDetailDto;
import CamNecT.server.domain.chat.dto.request.response.ChatRequestListResponseDto;
import CamNecT.server.domain.chat.dto.room.ChatRoomListDetailDto;
import CamNecT.server.domain.chat.dto.room.ChatRoomWithDetailDto;
import CamNecT.server.domain.chat.model.Chat;
import CamNecT.server.domain.chat.model.ChatRequest;
import CamNecT.server.domain.chat.model.ChatRoom;
import CamNecT.server.domain.chat.repository.ChatRepository;
import CamNecT.server.domain.chat.repository.ChatRequestRepository;
import CamNecT.server.domain.chat.repository.ChatRoomRepository;
import CamNecT.server.domain.home.dto.HomeResponse;
import CamNecT.server.domain.profile.dto.ProfileGlobalDto;
import CamNecT.server.domain.users.model.UserProfile;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserProfileRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.domain.users.repository.UserTagMapRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.CoffeeChatErrorCode;
import CamNecT.server.domain.profile.components.majors.model.Majors;
import CamNecT.server.global.notification.event.*;
import CamNecT.server.global.notification.event.CoffeeChatAcceptedEvent;
import CamNecT.server.global.notification.event.CoffeeChatRequestedEvent;
import CamNecT.server.global.notification.event.NewChatMessageEvent;
import CamNecT.server.global.notification.event.TeamRecruitAcceptedEvent;
import CamNecT.server.global.point.model.PointEvent;
import CamNecT.server.global.point.service.PointService;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import CamNecT.server.global.tag.model.Tag;
import CamNecT.server.domain.profile.components.majors.repository.MajorRepository;
import CamNecT.server.global.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChatService {

    @Value("${app.point.reward.coffee-chat-accepted:500}")
    private int rewardCoffeeChatAccepted;
    private static final long LEGACY_TAG_ID = 111L;
    private static final long CANONICAL_TAG_ID = 53L;

    private final UserRepository userRepository;
    private final ChatRepository chatRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRequestRepository chatRequestRepository;
    private final TagRepository tagRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserTagMapRepository userTagMapRepository;
    private final MajorRepository majorRepository;
    private final TeamRecruitmentRepository recruitmentRepository;

    private final PublicUrlIssuer publicUrlIssuer;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatPresenceService presenceService;
    private final PointService pointService;

    /*
      1. 커피챗 요청 보내기
      Requester -> Receiver임.
     */
    @Transactional
    public Long sendCoffeeChatRequest(Long requesterId, Long receiverId, List<Long> tagIds, String content) {
        if (requesterId == null) {
            throw new CustomException(AuthErrorCode.INVALID_TOKEN);
        }
        if (receiverId == null) {
            throw new CustomException(CoffeeChatErrorCode.RECEIVER_NOT_FOUND);
        }
        if (!StringUtils.hasText(content)) {
            throw new CustomException(CoffeeChatErrorCode.INVALID_CHAT_CONTENT);
        }
        if (Objects.equals(requesterId, receiverId)) {
            throw new CustomException(CoffeeChatErrorCode.SELF_REQUEST_NOT_ALLOWED);
        }

        lockUsersInOrder(requesterId, receiverId);
        Users requester = requireAuthenticatedUser(requesterId);
        Users receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new CustomException(CoffeeChatErrorCode.RECEIVER_NOT_FOUND));

        if (chatRequestRepository.existsByRequester_UserIdAndReceiver_UserIdAndStatusAndType(
                requesterId, receiverId, ChatRequest.RequestStatus.WAITING, ChatRequest.RequestType.COFFEE_CHAT)
                || chatRequestRepository.existsByRequester_UserIdAndReceiver_UserIdAndStatusAndType(
                receiverId, requesterId, ChatRequest.RequestStatus.WAITING, ChatRequest.RequestType.COFFEE_CHAT)
        ) {
            throw new CustomException(CoffeeChatErrorCode.DUPLICATE_REQUEST);
        }

        if (chatRequestRepository.existsByRequester_UserIdAndReceiver_UserIdAndStatusAndType(
                requesterId, receiverId, ChatRequest.RequestStatus.ACCEPTED, ChatRequest.RequestType.COFFEE_CHAT)
                || chatRequestRepository.existsByRequester_UserIdAndReceiver_UserIdAndStatusAndType(
                receiverId, requesterId, ChatRequest.RequestStatus.ACCEPTED, ChatRequest.RequestType.COFFEE_CHAT)) {
            throw new CustomException(CoffeeChatErrorCode.CHATROOM_ALREADY_EXISTS);
        }

        boolean isOpen = userProfileRepository.existsByUserIdAndOpenToCoffeeChatTrue(receiverId);
        if (!isOpen) {
            throw new CustomException(CoffeeChatErrorCode.RECEIVER_COFFEECHAT_DISABLED);
        }


        List<Long> normalizedTagIds = normalizeTagIds(tagIds);

        List<Tag> tags = tagRepository.findAllById(normalizedTagIds);
        if (tags.size() != normalizedTagIds.size()) {
            throw new CustomException(CoffeeChatErrorCode.TAG_NOT_FOUND);
        }

        ChatRequest request = ChatRequest.builder()
                .requester(requester)
                .receiver(receiver)
                .type(ChatRequest.RequestType.COFFEE_CHAT)
                .requestInterest(tags)
                .content(content)
                .build();

        Long requestId = chatRequestRepository.save(request).getId();

        log.info("[coffeechat] txActive(beforePublish)={}, requestId={}",
                TransactionSynchronizationManager.isActualTransactionActive(), requestId);

        eventPublisher.publishEvent(new CoffeeChatRequestedEvent(
                receiverId,
                requesterId,
                requestId
        ));

        log.info("[coffeechat] published event. txActive(afterPublish)={}",
                TransactionSynchronizationManager.isActualTransactionActive());

        return requestId;
    }


    /*
      2. 요청 수락/거절 처리
      Receiver가 수락(ACCEPTED)하면 -> 채팅방(ChatRoom)이 생성
     */
    @Transactional
    public void respondToRequest(Long requestId, Long userId, boolean isAccepted) {
        requireAuthenticatedUser(userId);
        ChatRequest request = chatRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new CustomException(CoffeeChatErrorCode.REQUEST_NOT_FOUND));

        // 본인 요청인지 검증
        if (!request.getReceiver().getUserId().equals(userId))
            throw new CustomException(CoffeeChatErrorCode.REQUEST_ACCESS_DENIED);

        if (request.getStatus() == ChatRequest.RequestStatus.ACCEPTED && isAccepted) {
            return;
        }
        if (request.getStatus() == ChatRequest.RequestStatus.REJECTED && !isAccepted) {
            return;
        }
        if (request.getStatus() != ChatRequest.RequestStatus.WAITING) {
            throw new CustomException(CoffeeChatErrorCode.REQUEST_ALREADY_PROCESSED);
        }

        if (isAccepted) {
            request.accept();
            Long roomId = createChatRoom(request);
            tryRewardCoffeeChatAcceptedPoint(request);
            publishAcceptedNotification(request, roomId);
        } else {
            request.reject();
        }
    }

    @Transactional(readOnly = true)
    public ChatRequestDetailDto getChatRequestDetail(Long requestId, Long userId) {
        requireAuthenticatedUser(userId);
        ChatRequest request = chatRequestRepository.findById(requestId)
                .orElseThrow(() -> new CustomException(CoffeeChatErrorCode.REQUEST_NOT_FOUND));

        if (!request.getReceiver().getUserId().equals(userId) && !request.getRequester().getUserId().equals(userId)) {
            throw new CustomException(CoffeeChatErrorCode.REQUEST_ACCESS_DENIED);
        }

        String title = "커피챗 요청";
        if (request.getType() == ChatRequest.RequestType.TEAM_RECRUIT && request.getRecruitmentId() != null) {
            title = recruitmentRepository.findById(request.getRecruitmentId())
                    .map(TeamRecruitment::getTitle)
                    .orElse("삭제된 모집 공고입니다.");
        }

        boolean isReceiver = request.getReceiver().getUserId().equals(userId);
        Users me = isReceiver ? request.getReceiver() : request.getRequester();
        Users opponent = isReceiver ? request.getRequester() : request.getReceiver();

        UserProfile opProfile = userProfileRepository.findByUserId(opponent.getUserId())
                .orElse(null);

        String majorName = "전공 미입력";
        String studentNo = "학번 미입력";
        String profileImgUrl = "/images/default.png";
        if (opProfile != null) {
            if (opProfile.getMajorId() != null) {
                majorName = majorRepository.findById(opProfile.getMajorId())
                        .map(Majors::getMajorNameKor)
                        .orElse("알 수 없는 전공");
            }
            studentNo = opProfile.getStudentNo();
            profileImgUrl = publicUrlIssuer.issuePublicUrl(opProfile.getProfileImageKey());
        }

        List<String> opTagNames = userTagMapRepository.findAllTagsByUserId(opponent.getUserId())
                .stream()
                .map(Tag::getName)
                .toList();

        return ChatRequestDetailDto.from(me, opponent, request, majorName, studentNo, opTagNames, profileImgUrl, title);
    }

    @Transactional(readOnly = true)
    public ChatRequestListResponseDto getChatRequestList(Long userId, ChatRequest.RequestType type) {
        requireAuthenticatedUser(userId);
        List<ChatRequest> requests = chatRequestRepository.findRequestsWithRequester(
                userId, type, ChatRequest.RequestStatus.WAITING);

        if (requests.isEmpty()) {
            return new ChatRequestListResponseDto(List.of());
        }

        Set<Long> recruitmentIds = requests.stream()
                .map(ChatRequest::getRecruitmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> recruitmentTitleMap = recruitmentIds.isEmpty() ? new HashMap<>() :
                recruitmentRepository.findAllById(recruitmentIds).stream()
                        .collect(Collectors.toMap(TeamRecruitment::getRecruitId, TeamRecruitment::getTitle));

        List<Long> opponentIds = requests.stream()
                .map(req -> req.getRequester().getUserId())
                .distinct()
                .toList();

        Map<Long, ProfileGlobalDto> globalMap = userProfileRepository.findGlobalsByUserIdIn(opponentIds).stream()
                .collect(Collectors.toMap(ProfileGlobalDto::userId, it -> it));

        Map<Long, UserProfile> profileMap = userProfileRepository.findAllByUserIdIn(opponentIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, p -> p));

        List<ChatRequestListDetailDto> dtoList = requests.stream()
                .map(request -> {
                    Users opponent = request.getRequester();
                    ProfileGlobalDto g = globalMap.get(opponent.getUserId());
                    UserProfile opProfile = profileMap.get(opponent.getUserId());

                    String majorName = "전공 미입력";
                    if (g != null && StringUtils.hasText(g.majorName())) {
                        majorName = g.majorName();
                    }

                    String profileImgUrl = "/images/default.png";
                    if (g != null && StringUtils.hasText(g.profileImageKey())) {
                        profileImgUrl = publicUrlIssuer.issuePublicUrl(g.profileImageKey());
                    }

                    String title = recruitmentTitleMap.getOrDefault(request.getRecruitmentId(), "커피챗 요청");

                    return ChatRequestListDetailDto.from(
                            opponent,
                            opProfile,
                            request,
                            majorName,
                            profileImgUrl,
                            title
                    );
                })
                .toList();

        return new ChatRequestListResponseDto(dtoList);
    }

    /*
     2-1. 채팅방 생성 (수락 시 자동 호출)
     */
    private Long createChatRoom(ChatRequest request) {
        // 1) 이미 생성된 방이 있으면 그대로 반환 (중복 호출/재시도 대비)
        Optional<ChatRoom> existing = chatRoomRepository.findByRequest_Id(request.getId());
        if (existing.isPresent()) return existing.get().getId();

        // 2) 없으면 생성 시도 (동시성 대비: 유니크 터지면 다시 조회)
        try {
            ChatRoom chatRoom = ChatRoom.createRoom(request, request.getRequester(), request.getReceiver());
            ChatRoom saved = chatRoomRepository.save(chatRoom);
            return saved.getId();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // request_id UNIQUE에 걸린 케이스 → 이미 누가 만들었음
            return chatRoomRepository.findByRequest_Id(request.getId())
                    .map(ChatRoom::getId)
                    .orElseThrow(() -> e);
        }
    }

    @Transactional
    public List<ChatMessageResponseDto> getChatHistory(Long roomId, Long userId) {
        Users reader = requireAuthenticatedUser(userId);
        ChatRoom room = chatRoomRepository.findByUserIdWithDetails(roomId, userId)
                .orElseThrow(() -> new CustomException(CoffeeChatErrorCode.CHATROOM_NOT_FOUND));

        markAllAsRead(roomId, reader);

        List<Chat> chatHistory = chatRepository.findTop1000ByRoomId(
                roomId, PageRequest.of(0, 1000)
        );

        return chatHistory.stream()
                .sorted(Comparator.comparing(Chat::getId))
                .map(ChatMessageResponseDto::toDto)
                .toList();
    }


    @Transactional
    public ChatRoomWithDetailDto getRoomWithDetails(Long roomId, Long userId) {
        requireAuthenticatedUser(userId);
        ChatRoom room = chatRoomRepository.findByUserIdWithDetails(roomId, userId)
                .orElseThrow(() -> new CustomException(CoffeeChatErrorCode.CHATROOM_NOT_FOUND));

        Users me = (room.getRequester().getUserId().equals(userId)) ? room.getRequester() : room.getReceiver();
        Users opponent = (room.getRequester().getUserId().equals(userId)) ? room.getReceiver() : room.getRequester();

        UserProfile opProfile = userProfileRepository.findByUserId(opponent.getUserId()).orElse(null);
        String majorName = "전공 미입력";
        String profileImgUrl = null;

        if (opProfile != null) {
            if (opProfile.getMajorId() != null) {
                majorName = majorRepository.findById(opProfile.getMajorId())
                        .map(Majors::getMajorNameKor)
                        .orElse("알 수 없는 전공");
            }
            if (StringUtils.hasText(opProfile.getProfileImageKey())) {
                profileImgUrl = publicUrlIssuer.issuePublicUrl(opProfile.getProfileImageKey());
            }
        }

        List<String> tagNames = userTagMapRepository.findAllTagsByUserId(opponent.getUserId())
                .stream()
                .map(Tag::getName)
                .toList();

        List<ChatMessageResponseDto> chatHistory = this.getChatHistory(roomId, userId);

        String title = "커피챗 요청";
        if (room.getRequest().getType() == ChatRequest.RequestType.TEAM_RECRUIT) {
            Long recruitmentId = room.getRequest().getRecruitmentId();
            if (recruitmentId != null) {
                title = recruitmentRepository.findById(recruitmentId)
                        .map(TeamRecruitment::getTitle)
                        .orElse("삭제된 모집 공고입니다.");
            }
        }

        return ChatRoomWithDetailDto.from(room, me, opponent, opProfile, majorName, tagNames, chatHistory, title, profileImgUrl);
    }


    public List<ChatRoomListDetailDto> getChatRoomList(Long userId, ChatRequest.RequestType type) {
        Users me = requireAuthenticatedUser(userId);

//        List<ChatRoom> myRooms = chatRoomRepository.findAllByUserIdWithBasicInfo(userId);
        List<ChatRoom> myRooms;

        if (type == null) {
            // 전체 조회
            myRooms = chatRoomRepository.findAllByUserIdWithBasicInfo(userId);
        } else {
            // 타입별 조회
            myRooms = chatRoomRepository.findAllByUserIdAndType(userId, type);
        }

        if (myRooms.isEmpty()) {
            return List.of();
        }

        List<Long> opponentIds = myRooms.stream()
                .map(room -> {
                    Users opponent = room.getRequester().getUserId().equals(userId)
                            ? room.getReceiver() : room.getRequester();
                    return opponent.getUserId();
                })
                .distinct()
                .toList();

        Map<Long, UserProfile> profileMap = userProfileRepository.findAllByUserIdIn(opponentIds)
                .stream()
                .collect(Collectors.toMap(UserProfile::getUserId, p -> p));

        // 안 읽은 개수 조회
        Map<Long, Long> unreadCounts = chatRepository.countUnreadMessagesByRooms(myRooms, userId)
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        Map<Long, String> lastMessageMap = chatRepository.findLastMessagesByRooms(myRooms)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],  // RoomId
                        row -> (String) row[1] // Message
                ));

        return myRooms.stream()
                .map(room -> {
                    Users opponent = room.getRequester().getUserId().equals(userId)
                            ? room.getReceiver() : room.getRequester();

                    UserProfile opProfile = profileMap.get(opponent.getUserId());

                    String majorName = "전공 미입력";
                    String studentNo = "";
                    String profileImgUrl = "/images/default.png";

                    if (opProfile != null) {
                        studentNo = (opProfile.getStudentNo() != null) ? opProfile.getStudentNo() : "미입력";
                        if (opProfile.getMajorId() != null) {
                            majorName = majorRepository.findById(opProfile.getMajorId())
                                    .map(Majors::getMajorNameKor)
                                    .orElse("알 수 없는 전공");
                        }
                        profileImgUrl = publicUrlIssuer.issuePublicUrl(opProfile.getProfileImageKey());

                    }

                    Long count = unreadCounts.getOrDefault(room.getId(), 0L);
                    String lastMessage = lastMessageMap.getOrDefault(room.getId(), "대화 내용이 없습니다.");

                    return ChatRoomListDetailDto.of(room, me, count, majorName, studentNo, lastMessage, profileImgUrl);
                })
                .toList();
    }


    //   기존 unread 전부 읽음 처리
    @Transactional
    public void markAllAsRead(Long roomId, Users reader) {

        if (reader == null || reader.getUserId() == null
                || !chatRoomRepository.existsAccessibleByUserId(roomId, reader.getUserId())) {
            throw new CustomException(CoffeeChatErrorCode.CHATROOM_ACCESS_DENIED);
        }

        List<Chat> unreadMessages = chatRepository.findUnreadMessages(roomId, reader.getUserId());

        if (unreadMessages.isEmpty()) {
            return;
        }

        unreadMessages.forEach(Chat::markAsRead);
        chatRepository.saveAll(unreadMessages);

        log.info("📚 읽음 처리 완료: {}건 저장됨.", unreadMessages.size());

        // 마지막으로 읽은 메시지 ID 추출 (없으면 0L임)
        Long lastMessageId = unreadMessages.isEmpty() ? 0L : unreadMessages.getLast().getId();

        ChatReadEvent chatReadEvent = ChatReadEvent.of(
                roomId,
                lastMessageId,
                LocalDateTime.now().toString()
                // typee ->  ReadEvent 내부에서 READ로 자동 설정됨
        );

        String lastContent = unreadMessages.getLast().getContent();
        String lastTime = unreadMessages.getLast().getCreatedAt().toString();
        eventPublisher.publishEvent(new ChatReadCommittedEvent(
                chatReadEvent,
                reader.getUserId(),
                lastContent,
                lastTime
        ));

    }

    @Transactional
    public ChatMessageAckResponseDto sendMessage(Long senderId, ChatMessageSendRequestDto request) {
        if (request == null
                || request.roomId() == null
                || !StringUtils.hasText(request.content())
                || request.content().length() > ChatMessageSendRequestDto.MAX_CONTENT_LENGTH) {
            throw new CustomException(CoffeeChatErrorCode.INVALID_CHAT_CONTENT);
        }
        log.info("[CHAT-SEND] === 메세지 전송 시작 === RoomID: {}, SenderID: {}", request.roomId(), senderId);

        //예외처리
        ChatRoom room = chatRoomRepository.findByIdForUpdate(request.roomId())
                .orElseThrow(() -> new CustomException(CoffeeChatErrorCode.CHATROOM_NOT_FOUND));

        if (room.getStatus() == ChatRoom.RoomStatus.CLOSE) {
            log.error("❌ [CHAT-ERROR] 이미 종료된 채팅방입니다. RoomID: {}", request.roomId());
            throw new CustomException(CoffeeChatErrorCode.COFFEE_CHAT_CLOSED);
        }

        //보내는 사람과 받는 사람 분류
        Users sender = requireAuthenticatedUser(senderId);

        boolean isRequester = Objects.equals(room.getRequester().getUserId(), sender.getUserId());
        boolean isReceiver = Objects.equals(room.getReceiver().getUserId(), sender.getUserId());
        if (!isRequester && !isReceiver) {
            throw new CustomException(CoffeeChatErrorCode.CHATROOM_ACCESS_DENIED);
        }

        Users receiver = isRequester
                ? room.getReceiver()
                : room.getRequester();
        log.info("👤 Sender: {} -> Receiver: {}", sender.getUserId(), receiver.getUserId());

        String clientMessageId = normalizeClientMessageId(request.clientMessageId());
        Optional<Chat> existingMessage = chatRepository.findByClientMessageId(
                room.getId(), sender.getUserId(), clientMessageId);
        if (existingMessage.isPresent()) {
            Chat existing = existingMessage.get();
            if (!Objects.equals(existing.getContent(), request.content())) {
                throw new CustomException(CoffeeChatErrorCode.IDEMPOTENCY_KEY_REUSED);
            }

            log.info("[CHAT-SEND] 멱등 재요청 감지. roomId={}, senderId={}, clientMessageId={}, messageId={}",
                    room.getId(), sender.getUserId(), clientMessageId, existing.getId());
            return ChatMessageAckResponseDto.from(ChatMessageResponseDto.toDto(existing), true);
        }

        boolean receiverPresent = presenceService.isPresent(room.getId(), receiver.getUserId());
        log.info("👀 상대방(receiver) 접속 여부: {}", receiverPresent);

        Chat chat = Chat.createChat(room, sender, receiver, request.content(), clientMessageId);

        if (receiverPresent) {
            chat.markAsRead();
            log.info("✅ 상대방이 접속 중이므로 읽음 처리됨");
        }

        chatRepository.save(chat);
        room.updateLastMessageTime();
        log.info("💾 메시지 DB 저장 완료 (ChatID: {})", chat.getId());

        /// 상대방 부대중일때 알림 발송(도메인에서 알림구현)
        if (!receiverPresent) {
            eventPublisher.publishEvent(new NewChatMessageEvent(
                    receiver.getUserId(), // 받는 사람
                    sender.getUserId(),   // 보낸 사람
                    room.getId(),         // 채팅방 ID
                    chat.getContent()     // 메시지 내용 (Event 내부에서 길이 조절됨)
            ));
            log.info("🔔 상대방 부재중 - 알림 이벤트(EventPublisher) 발행");
        }

        ChatMessageResponseDto response = ChatMessageResponseDto.toDto(chat);
        eventPublisher.publishEvent(new ChatMessageCommittedEvent(
                response,
                sender.getUserId(),
                receiver.getUserId(),
                chat.getContent(),
                chat.getCreatedAt().toString()
        ));

        return ChatMessageAckResponseDto.from(response, false);
    }

    @Transactional(readOnly = true)
    public HomeResponse.CoffeeChatSection getHomeInbox(Long userId, int limit) {

        HomeRequestInbox inbox = getHomeRequestInbox(
                userId, ChatRequest.RequestType.COFFEE_CHAT, limit
        );
        if (inbox.pendingCount() == 0) return HomeResponse.CoffeeChatSection.empty();
        if (inbox.requests().isEmpty()) {
            return new HomeResponse.CoffeeChatSection(inbox.pendingCount(), List.of());
        }

        List<Long> senderIds = inbox.requests().stream()
                .map(cr -> cr.getRequester().getUserId())
                .distinct().toList();

        Map<Long, ProfileGlobalDto> globalMap =
                userProfileRepository.findGlobalsByUserIdIn(senderIds).stream()
                        .collect(Collectors.toMap(ProfileGlobalDto::userId, it -> it));

        List<HomeResponse.CoffeeChatSection.CoffeeChatPreview> previews = inbox.requests().stream()
                .map(cr -> {
                    Users sender = cr.getRequester();
                    ProfileGlobalDto profile = globalMap.get(sender.getUserId());

                    String majorName = profile != null ? profile.majorName() : null;
                    String studentNo = profile != null && StringUtils.hasText(profile.studentNo())
                            ? profile.studentNo()
                            : null;

                    return new HomeResponse.CoffeeChatSection.CoffeeChatPreview(
                            cr.getId(),
                            sender.getUserId(),
                            sender.getName(),
                            majorName,
                            studentNo
                    );
                })
                .toList();

        return new HomeResponse.CoffeeChatSection(inbox.pendingCount(), previews);
    }

    @Transactional(readOnly = true)
    public HomeResponse.RecruitmentSection getHomeRecruitmentInbox(Long userId, int limit) {

        HomeRequestInbox inbox = getHomeRequestInbox(
                userId, ChatRequest.RequestType.TEAM_RECRUIT, limit
        );
        if (inbox.pendingCount() == 0) return HomeResponse.RecruitmentSection.empty();
        if (inbox.requests().isEmpty()) {
            return new HomeResponse.RecruitmentSection(inbox.pendingCount(), List.of());
        }

        List<Long> senderIds = inbox.requests().stream()
                .map(cr -> cr.getRequester().getUserId())
                .distinct().toList();

        Map<Long, UserProfile> profileMap = userProfileRepository.findAllByUserIdInWithUser(senderIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, profile -> profile));

        List<Long> majorIds = profileMap.values().stream()
                .map(UserProfile::getMajorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> majorNameMap = majorIds.isEmpty()
                ? Map.of()
                : majorRepository.findAllById(majorIds).stream()
                        .collect(Collectors.toMap(Majors::getMajorId, Majors::getMajorNameKor));

        Map<Long, List<String>> tagMap = userTagMapRepository.findTagNamesWithUserIdByUserIdIn(senderIds).stream()
                .collect(Collectors.groupingBy(
                        row -> (Long) row[0],
                        Collectors.mapping(row -> (String) row[1], Collectors.toList())
                ));

        List<HomeResponse.RecruitmentSection.RecruitmentPreview> previews = inbox.requests().stream()
                .map(request -> {
                    Long senderId = request.getRequester().getUserId();
                    UserProfile profile = profileMap.get(senderId);
                    if (profile == null || profile.getUser() == null) return null;

                    String profileImageUrl = StringUtils.hasText(profile.getProfileImageKey())
                            ? publicUrlIssuer.issuePublicUrl(profile.getProfileImageKey())
                            : null;

                    return new HomeResponse.RecruitmentSection.RecruitmentPreview(
                            senderId,
                            profile.getUser().getName(),
                            majorNameMap.get(profile.getMajorId()),
                            ProfileCardDto.createCard(profile, profileImageUrl),
                            tagMap.getOrDefault(senderId, List.of()),
                            request.getId()
                    );
                })
                .filter(Objects::nonNull)
                .toList();

        return new HomeResponse.RecruitmentSection(inbox.pendingCount(), previews);
    }

    private HomeRequestInbox getHomeRequestInbox(Long userId, ChatRequest.RequestType type, int limit) {
        long pendingCount = chatRequestRepository.countByReceiver_UserIdAndTypeAndStatus(
                userId, type, ChatRequest.RequestStatus.WAITING
        );
        if (pendingCount == 0) return HomeRequestInbox.empty();

        List<ChatRequest> latest = chatRequestRepository.findLatestReceivedRequestsByType(
                userId, type, ChatRequest.RequestStatus.WAITING, PageRequest.of(0, limit)
        );
        if (latest.isEmpty()) return new HomeRequestInbox(pendingCount, List.of());

        return new HomeRequestInbox(pendingCount, latest);
    }

    private record HomeRequestInbox(long pendingCount, List<ChatRequest> requests) {
        private static HomeRequestInbox empty() {
            return new HomeRequestInbox(0, List.of());
        }
    }

    @Transactional
    public void rejectAllCoffeeChatRequests(Long userId, ChatRequest.RequestType requestType) {
        requireAuthenticatedUser(userId);
        List<ChatRequest> requests = chatRequestRepository.findAllByReceiver_UserIdAndTypeAndStatus(
                userId, requestType, ChatRequest.RequestStatus.WAITING);

        requests.forEach(ChatRequest::reject);
    }

    @Transactional
    public void rejectAllTeamRecruitRequestsByRecruitment(Long userId, Long recruitmentId) {
        requireAuthenticatedUser(userId);
        List<ChatRequest> requests = chatRequestRepository.findAllByReceiver_UserIdAndTypeAndRecruitmentIdAndStatus(
                userId,
                ChatRequest.RequestType.TEAM_RECRUIT,
                recruitmentId,
                ChatRequest.RequestStatus.WAITING
        );

        requests.forEach(ChatRequest::reject);
    }

    public void closeChatRoom(Long roomId, Long userId) {
        requireAuthenticatedUser(userId);
        ChatRoom room = chatRoomRepository.findByUserIdWithDetailsForUpdate(roomId, userId)
                .orElseThrow(() -> new CustomException(CoffeeChatErrorCode.CHATROOM_NOT_FOUND));
        room.closeRoom();
        
        ChatRequest request = room.getRequest();
        if (request == null) {
            throw new CustomException(CoffeeChatErrorCode.REQUESTER_NOT_FOUND);
        }
        request.closeRequest();
    }

    public void exitOfChatRoom(Long roomId, Long userId) {
        requireAuthenticatedUser(userId);
        ChatRoom room = chatRoomRepository.findByUserIdWithDetailsForUpdate(roomId, userId)
                .orElseThrow(() -> new CustomException(CoffeeChatErrorCode.CHATROOM_NOT_FOUND));
        room.leave(userId);

        ChatRequest request = room.getRequest();
        if (request == null) {
            throw new CustomException(CoffeeChatErrorCode.REQUESTER_NOT_FOUND);
        }
        request.closeRequest();

    }

    public void completeExitChatRoom(Long roomId, Long userId) {
        requireAuthenticatedUser(userId);
        ChatRoom room = chatRoomRepository.findByUserIdWithDetailsForUpdate(roomId, userId)
                .orElseThrow(() -> new CustomException(CoffeeChatErrorCode.CHATROOM_NOT_FOUND));

        // 사용자 퇴장 표시
        room.leave(userId);

        // 요청 종료
        ChatRequest request = room.getRequest();
        if (request == null) {
            throw new CustomException(CoffeeChatErrorCode.REQUESTER_NOT_FOUND);
        }
        request.closeRequest();

        // 채팅방 완전 종료
        room.closeRoom();

        log.info("채팅방 완전 종료 (roomId={}, userId={})", roomId, userId);
    }

    private void publishAcceptedNotification(ChatRequest request, Long roomId) {
        Long receiverUserId = request.getRequester().getUserId(); // 요청자
        Long actorUserId = request.getReceiver().getUserId();     // 승인자

        if (request.getType() == ChatRequest.RequestType.TEAM_RECRUIT) {
            eventPublisher.publishEvent(new TeamRecruitAcceptedEvent(
                    receiverUserId, actorUserId, roomId, request.getRecruitmentId()
            ));
        } else {
            eventPublisher.publishEvent(new CoffeeChatAcceptedEvent(
                    receiverUserId, actorUserId, roomId, request.getId()
            ));
        }
    }

    private void tryRewardCoffeeChatAcceptedPoint(ChatRequest request) {
        Long requestId = request.getId();
        Long targetUserId = (request.getRequester() == null) ? null : request.getRequester().getUserId();

        if (requestId == null || targetUserId == null) {
            log.warn("[coffeechat] skip point reward. requestId={}, targetUserId={}", requestId, targetUserId);
            return;
        }
        try {
            pointService.earnPoint(targetUserId, rewardCoffeeChatAccepted,
                    PointEvent.coffeeChatAccepted(targetUserId, request.getId()));
        } catch (Exception ex) {
            log.warn("[coffeechat] point reward failed. requestId={}, userId={}", requestId, targetUserId, ex);
        }
    }

    private List<Long> normalizeTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return List.of();

        // 순서 유지가 필요 없으면 Set으로 가도 되는데, 혹시 프론트가 순서 의미를 두면 LinkedHashSet 권장
        return tagIds.stream()
                .filter(Objects::nonNull)
                .map(id -> id == LEGACY_TAG_ID ? CANONICAL_TAG_ID : id)
                .distinct()
                .toList();
    }

    private Users requireAuthenticatedUser(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(AuthErrorCode.INVALID_TOKEN));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new CustomException(AuthErrorCode.USER_SUSPENDED);
        }
        return user;
    }

    private void lockUsersInOrder(Long firstUserId, Long secondUserId) {
        long first = Math.min(firstUserId, secondUserId);
        long second = Math.max(firstUserId, secondUserId);
        userRepository.lockUserRow(first);
        if (first != second) userRepository.lockUserRow(second);
    }

    private String normalizeClientMessageId(String rawClientMessageId) {
        if (!StringUtils.hasText(rawClientMessageId)) {
            return UUID.randomUUID().toString();
        }

        try {
            String trimmed = rawClientMessageId.trim();
            String normalized = UUID.fromString(trimmed).toString();
            if (!normalized.equalsIgnoreCase(trimmed)) {
                throw new IllegalArgumentException("clientMessageId is not canonical");
            }
            return normalized;
        } catch (IllegalArgumentException e) {
            throw new CustomException(CoffeeChatErrorCode.INVALID_CLIENT_MESSAGE_ID, e);
        }
    }
}
