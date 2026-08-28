package CamNecT.server.domain.activity.service;

import CamNecT.server.domain.activity.dto.ExternalActivityAttachmentDto;
import CamNecT.server.domain.activity.dto.ExternalActivityDto;
import CamNecT.server.domain.activity.dto.TeamRecruitmentDto;
import CamNecT.server.domain.activity.dto.request.ActivityRequest;
import CamNecT.server.domain.activity.dto.request.AdminActivityRequest;
import CamNecT.server.domain.activity.dto.response.ActivityDetailResponse;
import CamNecT.server.domain.activity.dto.response.ActivityPreviewResponse;
import CamNecT.server.domain.activity.model.enums.ActivityCategory;
import CamNecT.server.domain.activity.model.enums.ActivityStatus;
import CamNecT.server.domain.activity.model.external_activity.ExternalActivity;
import CamNecT.server.domain.activity.model.external_activity.ExternalActivityAttachment;
import CamNecT.server.domain.activity.model.external_activity.ExternalActivityBookmark;
import CamNecT.server.domain.activity.model.external_activity.ExternalActivityTag;
import CamNecT.server.domain.activity.model.recruitment.TeamRecruitment;
import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityAttachmentRepository;
import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityBookmarkRepository;
import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityRepository;
import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityTagRepository;
import CamNecT.server.domain.activity.repository.recruitment.TeamRecruitmentRepository;
import CamNecT.server.domain.community.dto.AuthorDto;
import CamNecT.server.domain.community.service.AuthorAssembler;
import CamNecT.server.domain.home.dto.HomeResponse;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.ActivityErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import CamNecT.server.global.storage.service.GlobalPresignMethods;
import CamNecT.server.global.storage.model.UploadPurpose;
import CamNecT.server.global.storage.model.UploadRefType;
import CamNecT.server.global.storage.model.UploadTicket;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.service.PresignEngine;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import CamNecT.server.global.tag.model.Tag;
import CamNecT.server.global.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static CamNecT.server.domain.activity.service.ActivityAttachmentService.THUMB_ALLOWED;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ActivityService {

    private static final String DEFAULT_THUMB = "기본이미지";

    private final ExternalActivityRepository activityRepository;
    private final ExternalActivityTagRepository activityTagRepository;
    private final ExternalActivityAttachmentRepository activityAttachmentRepository;
    private final ExternalActivityBookmarkRepository activityBookmarkRepository;
    private final TagRepository tagRepository;
    private final TeamRecruitmentRepository teamRecruitmentRepository;
    private final UserRepository userRepository;
    private final AccountAccessGuard accountAccessGuard;

    private final AuthorAssembler authorAssembler;

    //S3 관련 의존성 주입
    private final UploadTicketRepository uploadTicketRepository;
    private final PresignEngine presignEngine;
    private final PublicUrlIssuer publicUrlIssuer;
    private final GlobalPresignMethods globalPresignMethods;

    public Slice<ActivityPreviewResponse> getActivities(
            Long userId,
            ActivityCategory category,
            List<Long> tagIds,
            String title,
            String sortType,
            Pageable pageable
    ) {
        // Repository에서 이미 모든 필드를 포함한 Response를 반환하므로 그대로 반환
        // 단, thumbnailUrl만 CDN URL로 변환
        var activities = activityRepository.findActivitiesByCondition(
                userId, category, tagIds, title, sortType, pageable
        );

        return activities.map(a -> new ActivityPreviewResponse(
                a.activityId(),
                a.title(),
                a.contextPreview(),
                thumbnailUrlOrNull(a.thumbnailUrl()),
                a.tags(),
                a.bookmarkCount(),
                a.organizer(),
                a.applyEndDate(),
                a.status(),
                a.createdAt()
        ));
    }

    @Transactional
    public ActivityPreviewResponse create(Long userId, ActivityRequest request) {
        validateGeneralActivityCategory(request.category());
        Users user = accountAccessGuard.requireAccessibleForUpdate(userId);
        validateActiveTagIds(request.tagIds());

        // 1. 엔티티 기본 저장
        ExternalActivity saved = activityRepository.save(ExternalActivity.builder()
                .user(user)
                .title(request.title())
                .category(request.category())
                .context(request.content())
                .thumbnailKey(DEFAULT_THUMB)
                .build());

        String finalAttachPrefix = "activity/activities/activity-" + saved.getActivityId() + "/attachments";
        String finalThumbPrefix = "activity/activities/activity-" + saved.getActivityId() + "/attachments/thumbnail";

        // 2. 썸네일 Consume
        if (StringUtils.hasText(request.thumbnailKey())) {
            String finalKey = presignEngine.consume(
                    userId,
                    UploadPurpose.ACTIVITY_THUMBNAIL,
                    UploadRefType.ACTIVITY,
                    saved.getActivityId(),
                    request.thumbnailKey(),
                    finalThumbPrefix
            );
            saved.updateThumbnailKey(finalKey);
        }

        // 3. 첨부파일 Consume 및 저장
        List<String> attachmentKeys = (request.attachmentKey() == null) ? List.of() : request.attachmentKey();
        List<String> finalAttachmentKeysInOrder = new ArrayList<>(attachmentKeys.size());

        // 요청 key 정리: 공백 제거 + 중복 제거 + (썸네일 key가 attachment에 섞여오면 스킵)
        String reqThumbKey = request.thumbnailKey();
        LinkedHashSet<String> reqAttachKeys = new LinkedHashSet<>();

        for (String k : attachmentKeys) {
            if (!StringUtils.hasText(k)) continue;
            if (StringUtils.hasText(reqThumbKey) && k.equals(reqThumbKey)) continue; // 중복 consume 방지
            reqAttachKeys.add(k);
        }
        for (String tempKey : reqAttachKeys) {
            String finalKey = presignEngine.consume(
                    userId,
                    UploadPurpose.ACTIVITY_ATTACHMENT,
                    UploadRefType.ACTIVITY,
                    saved.getActivityId(),
                    tempKey,
                    finalAttachPrefix
            );

            activityAttachmentRepository.save(ExternalActivityAttachment.builder()
                    .activity(saved)
                    .fileKey(finalKey)
                    .build());

            finalAttachmentKeysInOrder.add(finalKey); // 순서 유지
        }

        // 3.5) 썸네일이 비어있으면, 첨부 중 첫 이미지를 썸네일 copy해서 주입
        if (!StringUtils.hasText(request.thumbnailKey()) && !finalAttachmentKeysInOrder.isEmpty()) {
            String candidate = pickFirstImageKey(finalAttachmentKeysInOrder); // update에서 쓰던 그대로
            if (candidate != null) {
                String copiedThumbKey = globalPresignMethods.copyToPrefix(candidate, finalThumbPrefix);
                saved.updateThumbnailKey(copiedThumbKey);
            }
        }

        // 4. 태그 저장
        saveTags(saved, request.tagIds());

        return new ActivityPreviewResponse(
                saved.getActivityId(),
                saved.getTitle(),
                saved.getContext(),
                thumbnailUrlOrNull(saved.getThumbnailKey()),
                null,
                0L, // 새로 생성된 활동이므로 북마크 수는 0
                saved.getOrganizer(),
                saved.getApplyEndDate(),
                saved.getStatus(),
                saved.getCreatedAt()
        );
    }

    @Transactional
    public ActivityPreviewResponse createAdmin(Long userId, AdminActivityRequest request) {
        if (request.category() != ActivityCategory.EXTERNAL && request.category() != ActivityCategory.RECRUITMENT) {
            throw new CustomException(ActivityErrorCode.INVALID_ACTIVITY_CATEGORY);
        }

        Users adminUser = accountAccessGuard.requireAccessibleForUpdate(userId);
        if (adminUser.getRole() != UserRole.ADMIN) throw new CustomException(UserErrorCode.USER_NOT_ADMIN);
        validateActiveTagIds(request.tagIds());

        ExternalActivity saved = activityRepository.save(ExternalActivity.builder()
                .user(adminUser)
                .title(request.title())
                .category(request.category())
                .organizer(request.organizer())
                .targetDescription(request.targetDescription())
                .applyStartDate(request.applyStartDate())
                .applyEndDate(request.applyEndDate())
                .resultAnnounceDate(request.resultAnnounceDate())
                .officialUrl(request.officialUrl())
                .contextTitle(request.contextTitle())
                .context(request.content())
                .thumbnailKey(DEFAULT_THUMB)
                .build());

        String finalThumbPrefix = "activity/activities/activity-" + saved.getActivityId() + "/attachments/thumbnail";

        if (StringUtils.hasText(request.thumbnailKey())) {
            String finalKey = presignEngine.consume(
                    userId,
                    UploadPurpose.ACTIVITY_THUMBNAIL,
                    UploadRefType.ACTIVITY,
                    saved.getActivityId(),
                    request.thumbnailKey(),
                    finalThumbPrefix
            );
            saved.updateThumbnailKey(finalKey);

            activityRepository.flush(); // 또는 saveAndFlush(saved)
        }

        saveTags(saved, request.tagIds());

        return new ActivityPreviewResponse(
                saved.getActivityId(),
                saved.getTitle(),
                saved.getContext(),
                thumbnailUrlOrNull(saved.getThumbnailKey()),
                null,
                0L,
                saved.getOrganizer(),
                saved.getApplyEndDate(),
                saved.getStatus(),
                saved.getCreatedAt()
        );
    }

    @Transactional
    public void update(Long userId, Long activityId, ActivityRequest request) {
        validateGeneralActivityCategory(request.category());
        accountAccessGuard.requireAccessibleForUpdate(userId);

        ExternalActivity activity = activityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> new CustomException(ActivityErrorCode.ACTIVITY_NOT_FOUND));

        if (activity.getUser() == null || !Objects.equals(activity.getUser().getUserId(), userId)) {
            throw new CustomException(ActivityErrorCode.NOT_AUTHOR);
        }
        validateGeneralActivityCategory(activity.getCategory());
        validateActiveTagIds(request.tagIds());

        Set<String> deleteAfterCommit = new HashSet<>();

        String finalAttachPrefix = "activity/activities/activity-" + activity.getActivityId() + "/attachments";
        String finalThumbPrefix = "activity/activities/activity-" + activity.getActivityId() + "/attachments/thumbnail";

        // ----------------------------------------------------------------------
        // 규칙
        // - thumbnailKey: null=유지, ""=삭제(기본썸네일로), 값=교체(consume)
        // - attachmentKey: null=유지, []=전부삭제, 값=리스트 기준 전체교체(consume + keep)
        // ----------------------------------------------------------------------
        String reqThumb = request.thumbnailKey();
        List<String> reqAttachList = request.attachmentKey();
        // - attachmentKey가 null(유지)면 비워둠
        List<String> finalAttachmentKeysInOrder = List.of();

        // 1) 첨부파일 처리
        if (reqAttachList != null) { // null이면 유지
            List<ExternalActivityAttachment> current =
                    activityAttachmentRepository.findAllByActivity_ActivityId(activityId);

            Map<String, ExternalActivityAttachment> currentByKey = current.stream()
                    .filter(a -> StringUtils.hasText(a.getFileKey()))
                    .collect(Collectors.toMap(
                            ExternalActivityAttachment::getFileKey,
                            a -> a,
                            (a, b) -> a
                    ));

            // 요청 키 정리(중복 제거 + 공백 제거)
            LinkedHashSet<String> reqKeys = new LinkedHashSet<>();
            for (String k : reqAttachList) {
                if (StringUtils.hasText(k)) reqKeys.add(k);
            }

            // [] (또는 공백만) => 전부 삭제
            if (reqKeys.isEmpty()) {
                for (ExternalActivityAttachment a : current) {
                    if (StringUtils.hasText(a.getFileKey())) deleteAfterCommit.add(a.getFileKey());
                }
                if (!current.isEmpty()) activityAttachmentRepository.deleteAll(current);
                finalAttachmentKeysInOrder = List.of();
            } else {
                Set<String> keepFinalKeys = new HashSet<>();
                List<String> orderedFinalKeys = new ArrayList<>(reqKeys.size());

                for (String k : reqKeys) {
                    ExternalActivityAttachment existing = currentByKey.get(k);
                    if (existing != null) {
                        keepFinalKeys.add(k); // 이미 finalKey로 존재하면 유지
                        orderedFinalKeys.add(k);
                        continue;
                    }

                    String finalKey = presignEngine.consume(
                            userId,
                            UploadPurpose.ACTIVITY_ATTACHMENT,
                            UploadRefType.ACTIVITY,
                            activityId,
                            k,
                            finalAttachPrefix
                    );

                    activityAttachmentRepository.save(ExternalActivityAttachment.builder()
                            .activity(activity)
                            .fileKey(finalKey)
                            .build());

                    keepFinalKeys.add(finalKey);
                    orderedFinalKeys.add(finalKey);
                }

                // 삭제 예약 + DB 삭제
                List<ExternalActivityAttachment> toDelete = currentByKey.values().stream()
                        .filter(a -> !keepFinalKeys.contains(a.getFileKey()))
                        .toList();

                for (ExternalActivityAttachment a : toDelete) {
                    if (StringUtils.hasText(a.getFileKey())) deleteAfterCommit.add(a.getFileKey());
                }
                if (!toDelete.isEmpty()) activityAttachmentRepository.deleteAll(toDelete);
                finalAttachmentKeysInOrder = orderedFinalKeys;
            }
        }

        // 2) 썸네일 처리
        if (reqThumb != null) { // null이면 유지
            if (!StringUtils.hasText(reqThumb)) {
                // 첨부를 같이 넘긴 경우, 남는 첨부 중 "첫 번째 이미지"를 썸네일로 복사 저장
                String candidateImageKey = null;

                if (reqAttachList != null) {
                    if (!finalAttachmentKeysInOrder.isEmpty()) candidateImageKey = pickFirstImageKey(finalAttachmentKeysInOrder);
                } else {
                    // 첨부 유지(null) + 썸네일 삭제 => "현재 첨부"에서 첫 이미지로 fallback
                    List<String> currentKeys = activityAttachmentRepository.findAllByActivity_ActivityId(activityId).stream()
                            .map(ExternalActivityAttachment::getFileKey)
                            .filter(StringUtils::hasText)
                            .toList();
                    candidateImageKey = pickFirstImageKey(currentKeys);
                }
                // 기존 썸네일 삭제 예약
                if (StringUtils.hasText(activity.getThumbnailKey())
                        && !DEFAULT_THUMB.equals(activity.getThumbnailKey())) {
                    deleteAfterCommit.add(activity.getThumbnailKey());
                }
                if (candidateImageKey != null) {
                    // 첨부 -> 썸네일 경로로 복사
                    String copiedThumbKey = globalPresignMethods.copyToPrefix(candidateImageKey, finalThumbPrefix);
                    activity.updateThumbnailKey(copiedThumbKey);
                } else {
                    // 이미지 첨부가 없으면 기존 정책대로 기본썸네일
                    activity.updateThumbnailKey(DEFAULT_THUMB);
                }
            }
            else if (!reqThumb.equals(activity.getThumbnailKey())) {
                if (StringUtils.hasText(activity.getThumbnailKey())
                        && !DEFAULT_THUMB.equals(activity.getThumbnailKey())) {
                    deleteAfterCommit.add(activity.getThumbnailKey());
                }

                String finalKey = presignEngine.consume(
                        userId,
                        UploadPurpose.ACTIVITY_THUMBNAIL,
                        UploadRefType.ACTIVITY,
                        activityId,
                        reqThumb,
                        finalThumbPrefix
                );
                activity.updateThumbnailKey(finalKey);
            }
        }

        // 3) 기본 정보 업데이트
        activity.update(request);

        // 4) 태그 저장
        saveTags(activity, request.tagIds());

        // 5) S3 파일 삭제 예약
        globalPresignMethods.deleteAfterCommit(deleteAfterCommit);
    }

    @Transactional
    public void updateAdmin(Long adminId, Long activityId, AdminActivityRequest request) {
        // 1. 관리자는 대외활동과 취업정보만 수정 가능
        if (request.category() != ActivityCategory.EXTERNAL && request.category() != ActivityCategory.RECRUITMENT) {
            throw new CustomException(ActivityErrorCode.INVALID_ACTIVITY_CATEGORY);
        }

        Users admin = accountAccessGuard.requireAccessibleForUpdate(adminId);
        if (admin.getRole() != UserRole.ADMIN) {
            throw new CustomException(UserErrorCode.USER_NOT_ADMIN);
        }

        // 2. 활동 조회
        ExternalActivity activity = activityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> new CustomException(ActivityErrorCode.ACTIVITY_NOT_FOUND));

        // 3. 관리자가 작성한 글인지 카테고리로 검증 (유저 검증 없이 카테고리로만 검증)
        if (activity.getCategory() != ActivityCategory.EXTERNAL && activity.getCategory() != ActivityCategory.RECRUITMENT) {
            throw new CustomException(ActivityErrorCode.INVALID_ACTIVITY_CATEGORY);
        }
        validateActiveTagIds(request.tagIds());

        Set<String> deleteAfterCommit = new HashSet<>();
        String finalThumbPrefix = "activity/activities/activity-" + activity.getActivityId() + "/attachments/thumbnail";

        String reqThumb = request.thumbnailKey();

        // 4. 썸네일 교체 로직
        if (reqThumb != null) {
            if (!StringUtils.hasText(reqThumb)) {
                if (StringUtils.hasText(activity.getThumbnailKey()) && !DEFAULT_THUMB.equals(activity.getThumbnailKey())) {
                    deleteAfterCommit.add(activity.getThumbnailKey());
                }
                activity.updateThumbnailKey(DEFAULT_THUMB);

            } else if (!reqThumb.equals(activity.getThumbnailKey())) {
                if (StringUtils.hasText(activity.getThumbnailKey()) && !DEFAULT_THUMB.equals(activity.getThumbnailKey())) {
                    deleteAfterCommit.add(activity.getThumbnailKey());
                }

                String finalKey = presignEngine.consume(
                        adminId,
                        UploadPurpose.ACTIVITY_THUMBNAIL,
                        UploadRefType.ACTIVITY,
                        activityId,
                        reqThumb,
                        finalThumbPrefix
                );
                activity.updateThumbnailKey(finalKey);
            }
        }

        // 5. 기본 정보 업데이트 (ExternalActivity에 updateAdmin 메서드 추가 필요)
        activity.updateAdmin(request);

        saveTags(activity, request.tagIds());

        // 6. S3 파일 삭제 예약
        globalPresignMethods.deleteAfterCommit(deleteAfterCommit);
    }

    @Transactional
    public void delete(Long activityId, Long userId) {
        Users actor = accountAccessGuard.requireAccessibleForUpdate(userId);

        ExternalActivity activity = activityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> new CustomException(ActivityErrorCode.ACTIVITY_NOT_FOUND));

        boolean isAdmin = actor.getRole() == UserRole.ADMIN;
        boolean isAuthor =  activity.getUser() != null && Objects.equals(activity.getUser().getUserId(), userId);


        if (!isAdmin && !isAuthor) {
            throw new CustomException(ActivityErrorCode.NOT_AUTHOR);
        }

        deleteLockedActivity(activity);
    }

    @Transactional
    public void deleteForModeration(Long adminId, Long activityId) {
        ExternalActivity activity = activityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> new CustomException(ActivityErrorCode.ACTIVITY_NOT_FOUND));

        if (!userRepository.existsByUserIdAndRole(adminId, UserRole.ADMIN)) {
            throw new CustomException(ActivityErrorCode.NOT_AUTHOR);
        }

        deleteLockedActivity(activity);
    }

    private void deleteLockedActivity(ExternalActivity activity) {
        Long activityId = activity.getActivityId();
        if (teamRecruitmentRepository.existsByActivityId(activityId)) {
            throw new CustomException(ActivityErrorCode.ACTIVITY_HAS_RECRUITMENTS);
        }

        Set<String> deleteAfterCommit = new HashSet<>();

        if (StringUtils.hasText(activity.getThumbnailKey()) && !DEFAULT_THUMB.equals(activity.getThumbnailKey())) {
            deleteAfterCommit.add(activity.getThumbnailKey());
        }

        activityAttachmentRepository.findAllByActivity_ActivityId(activityId)
                .forEach(a -> {
                    if (StringUtils.hasText(a.getFileKey())) deleteAfterCommit.add(a.getFileKey());
                });
        activityTagRepository.deleteAllByActivityId(activityId);       // 태그 삭제
        activityBookmarkRepository.deleteAllByActivityId(activityId);  // 북마크 삭제
        activityAttachmentRepository.deleteAllByActivityId(activityId); // 첨부파일 삭제

        activityRepository.delete(activity);
        globalPresignMethods.deleteAfterCommit(deleteAfterCommit);
    }

    @Transactional(readOnly = true)
    public ActivityDetailResponse getActivityDetail(Long userId, Long activityId) {
        if (userId == null) throw new CustomException(ActivityErrorCode.USER_NOT_FOUND);
        // 1. 메인 활동 조회
        ExternalActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new CustomException(ActivityErrorCode.ACTIVITY_NOT_FOUND));

        // 2. Activity → DTO 변환 + 썸네일 presign
        ExternalActivityDto activityDto = ExternalActivityDto.from(activity)
                .withThumbnailUrl(thumbnailUrlOrNull(activity.getThumbnailKey()));

        // 3. 첨부파일 조회 (카테고리 조건)
        List<ExternalActivityAttachmentDto> attachmentDtos = List.of();

        /// 글쓴이 프로필
        Long authorId = Optional.ofNullable(activity.getUser())
                .map(Users::getUserId)
                .orElseThrow(() -> new CustomException(ActivityErrorCode.USER_NOT_FOUND));

        AuthorDto author = authorAssembler.buildAuthorMap(List.of(authorId))
                .get(authorId);

        if (activity.getCategory() == ActivityCategory.CLUB || activity.getCategory() == ActivityCategory.STUDY) {

            List<ExternalActivityAttachment> atts =
                    activityAttachmentRepository.findAllByActivity_ActivityId(activityId);

            // fileKey 목록
            List<String> keys = atts.stream()
                    .map(ExternalActivityAttachment::getFileKey)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();

            // ticket bulk
            Map<String, UploadTicket> ticketMap = keys.isEmpty()
                    ? Map.of()
                    : uploadTicketRepository.findAllByStorageKeyIn(keys).stream()
                    .collect(Collectors.toMap(UploadTicket::getStorageKey, t -> t, (a, b) -> a));

            // presign (실패는 스킵)
            Map<String, String> urlMap = new HashMap<>();
            for (String key : keys) {
                UploadTicket t = ticketMap.get(key);
                String filename = (t == null) ? null : t.getOriginalFilename();
                String contentType = (t == null) ? null : t.getContentType();

                try {
                    String url = presignEngine.presignDownload(key, filename, contentType).downloadUrl();
                    if (StringUtils.hasText(url)) urlMap.put(key, url);
                } catch (Exception e) {
                    log.warn("activity presignDownload failed. activityId={}, key={}", activityId, key, e);
                }
            }

            attachmentDtos = atts.stream()
                    .filter(a -> StringUtils.hasText(a.getFileKey()))
                    .map(a -> {
                        String url = urlMap.get(a.getFileKey());
                        if (!StringUtils.hasText(url)) return null;
                        return ExternalActivityAttachmentDto.from(a).withFileUrl(url);
                    })
                    .filter(Objects::nonNull)
                    .toList();
        }
        // 4. 태그 리스트 조회
        List<Long> tagIds = activityTagRepository.findAllByActivity_ActivityId(activityId).stream()
                .map(t -> t.getTag().getId())
                .toList();

        // 5. 팀원 공고 리스트 조회
        List<TeamRecruitment> recruitmentList =
                teamRecruitmentRepository.findAllByActivityId(activityId);

        // 3. 스트림을 이용한 변환
        List<TeamRecruitmentDto> recruitmentDtoList = recruitmentList.stream()
                .map(recruitment -> {

                    String userName = userRepository.findNameByUserId(recruitment.getUserId()).orElse("알 수 없는 사용자");

                    return recruitment.toDto(activity.getTitle(), userName);
                })
                .toList();

        // 6. 본인 글 여부
        boolean isMine = activity.getUser() != null && Objects.equals(activity.getUser().getUserId(), userId);


        // 7. 북마크 수 조회
        Long bookmarkCount = activityBookmarkRepository.countByActivity_ActivityId(activityId);

        // 8. 북마크 여부 조회
        boolean isBookmarked = activityBookmarkRepository.existsByUser_UserIdAndActivity_ActivityId(userId, activityId);

        // 9. Response 생성
        return new ActivityDetailResponse(
                isMine,
                author,
                activityDto,
                attachmentDtos,
                tagIds,
                recruitmentDtoList,
                bookmarkCount,
                isBookmarked
        );
    }

    @Transactional
    public void closeActivity(Long userId, Long activityId) {
        accountAccessGuard.requireAccessibleForUpdate(userId);

        // 1. 대외활동 조회
        ExternalActivity activity = activityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> new CustomException(ActivityErrorCode.ACTIVITY_NOT_FOUND));

        // 2. 스터디와 동아리만 모집 마감 가능 (대외활동, 취업정보는 불가)
        if (activity.getCategory() != ActivityCategory.STUDY && activity.getCategory() != ActivityCategory.CLUB) {
            throw new CustomException(ActivityErrorCode.INVALID_ACTIVITY_CATEGORY);
        }

        // 3. 작성자 본인 확인
        if (activity.getUser() == null || !Objects.equals(activity.getUser().getUserId(), userId)) {
            throw new CustomException(ActivityErrorCode.NOT_AUTHOR);
        }

        // 4. 이미 마감된 경우 예외 처리
        if (activity.getStatus() == ActivityStatus.CLOSED) {
            throw new CustomException(ActivityErrorCode.ALREADY_CLOSED);
        }

        // 5. 상태를 CLOSED로 변경 (더티 체킹으로 자동 업데이트)
        activity.close();
    }

    @Transactional
    public void closeActivityAdmin(Long adminId, Long activityId) {
        Users admin = accountAccessGuard.requireAccessibleForUpdate(adminId);
        if (admin.getRole() != UserRole.ADMIN) {
            throw new CustomException(UserErrorCode.USER_NOT_ADMIN);
        }

        // 1. 대외활동 조회
        ExternalActivity activity = activityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> new CustomException(ActivityErrorCode.ACTIVITY_NOT_FOUND));

        // 2. 대외활동과 취업정보만 마감 가능 (관리자가 작성한 것만)
        if (activity.getCategory() != ActivityCategory.EXTERNAL && activity.getCategory() != ActivityCategory.RECRUITMENT) {
            throw new CustomException(ActivityErrorCode.INVALID_ACTIVITY_CATEGORY);
        }

        // 4. 이미 마감된 경우 예외 처리
        if (activity.getStatus() == ActivityStatus.CLOSED) {
            throw new CustomException(ActivityErrorCode.ALREADY_CLOSED);
        }

        // 5. 상태를 CLOSED로 변경
        activity.close();
    }

    @Transactional
    public boolean toggleActivityBookmark(Long userId, Long activityId) {
        Users user = accountAccessGuard.requireAccessibleForUpdate(userId);

        // 삭제·수정과 북마크 변경을 동일 활동 행 기준으로 직렬화한다.
        ExternalActivity activity = activityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> new CustomException(ActivityErrorCode.ACTIVITY_NOT_FOUND));

        // 북마크 존재 여부 확인
        Optional<ExternalActivityBookmark> bookmarkOpt =
                activityBookmarkRepository.findByUser_UserIdAndActivity_ActivityId(userId, activityId);

        if (bookmarkOpt.isPresent()) {
            // 이미 존재하면 삭제
            activityBookmarkRepository.delete(bookmarkOpt.get());
            return false; // 해제됨을 반환
        } else {
            ExternalActivityBookmark newBookmark = ExternalActivityBookmark.of(user, activity);
            activityBookmarkRepository.save(newBookmark);
            return true;
        }
    }

    @Transactional(readOnly = true)
    public HomeResponse.ContestSection getHomeContests(int limit) {

        List<Long> ids = activityRepository.findTopIdsByBookmark(ActivityCategory.EXTERNAL, limit + 1);
        if (ids.isEmpty()) return HomeResponse.ContestSection.empty();

        boolean hasMore = ids.size() > limit;
        List<Long> topIds = hasMore ? ids.subList(0, limit) : ids;

        Map<Long, ExternalActivity> map = activityRepository.findAllById(topIds).stream()
                .collect(Collectors.toMap(ExternalActivity::getActivityId, a -> a));

        List<HomeResponse.ContestSection.ContestCard> items = topIds.stream()
                .map(id -> {
                    ExternalActivity a = map.get(id);
                    if (a == null) return null;

                    String thumbUrl = thumbnailUrlOrNull(a.getThumbnailKey());
                    return new HomeResponse.ContestSection.ContestCard(
                            a.getActivityId(),
                            a.getTitle(),
                            a.getOrganizer(),
                            thumbUrl
                    );
                })
                .filter(Objects::nonNull)
                .toList();

        return new HomeResponse.ContestSection(items, hasMore);
    }

    // --- Helper Methods ---

    private void validateGeneralActivityCategory(ActivityCategory category) {
        if (category != ActivityCategory.STUDY && category != ActivityCategory.CLUB) {
            throw new CustomException(ActivityErrorCode.INVALID_ACTIVITY_CATEGORY);
        }
    }

    private void validateActiveTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return;

        if (tagIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new CustomException(ActivityErrorCode.INVALID_TAG_IDS);
        }

        List<Long> uniqueIds = tagIds.stream().distinct().toList();
        if (tagRepository.findExistingActiveIds(uniqueIds).size() != uniqueIds.size()) {
            throw new CustomException(ActivityErrorCode.INVALID_TAG_IDS);
        }
    }

    /**
     * thumbnail 전용 URL 제공 메서드
     * CDN 방식
     */
    private String thumbnailUrlOrNull(String key) {
        if (!StringUtils.hasText(key) || DEFAULT_THUMB.equals(key)) return null;
        return publicUrlIssuer.issueImagePublicUrl(key);
    }

    /**
     * 활동의 태그 저장
     */
    private void saveTags(ExternalActivity activity, List<Long> tagIds) {
        if (tagIds == null) return;

        activityTagRepository.deleteAllByActivityId(activity.getActivityId());

        for (Long id : new LinkedHashSet<>(tagIds)) {
            Tag tagRef = tagRepository.getReferenceById(id);
            activityTagRepository.save(ExternalActivityTag.builder()
                    .activity(activity)
                    .tag(tagRef)
                    .build());
        }
    }

    /**
     * 요청 순서를 유지한 final attachment key 목록에서 "첫 번째 이미지" key를 반환
     */
    private String pickFirstImageKey(List<String> finalKeysInOrder) {
        for (String key : finalKeysInOrder) {
            if (!StringUtils.hasText(key)) continue;
            if (isImageKey(key)) return key;
        }
        return null;
    }

    /**
     * UploadTicket contentType 우선으로 이미지 여부 판단, 없으면 확장자 fallback
     */
    private boolean isImageKey(String key) {
        String ct = uploadTicketRepository.findByStorageKey(key)
                .map(UploadTicket::getContentType)
                .map(globalPresignMethods::normalize)
                .orElse(null);

        if (StringUtils.hasText(ct)) {
            return THUMB_ALLOWED.contains(ct);
        }

        String k = key.toLowerCase(Locale.ROOT);
        return k.endsWith(".jpg") || k.endsWith(".jpeg") || k.endsWith(".png") || k.endsWith(".webp");
    }



}
