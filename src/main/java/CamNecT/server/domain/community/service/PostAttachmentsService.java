package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.dto.request.AttachmentRequest;
import CamNecT.server.domain.community.model.props.CommunityAttachmentProps;
import CamNecT.server.domain.community.model.Posts.PostAttachments;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.repository.Posts.PostAttachmentsRepository;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.StorageErrorCode;
import CamNecT.server.global.storage.service.GlobalPresignMethods;
import CamNecT.server.global.storage.dto.request.PresignUploadBatchRequest;
import CamNecT.server.global.storage.dto.response.PresignUploadBatchResponse;
import CamNecT.server.global.storage.model.UploadPurpose;
import CamNecT.server.global.storage.model.UploadRefType;
import CamNecT.server.global.storage.service.PresignEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PostAttachmentsService {
    private final PostAttachmentsRepository postAttachmentsRepository;
    private final UserRepository userRepository;

    private final PresignEngine presignEngine;
    private final CommunityAttachmentProps attachmentProps;
    private final GlobalPresignMethods globalPresignMethods;



    @Transactional
    public PresignUploadBatchResponse presignAttachmentsBatch(Long userId, PresignUploadBatchRequest req) {
        var items = (req == null || req.items() == null)
                ? List.<PresignUploadBatchRequest.Item>of()
                : req.items();

        if (items.isEmpty()) throw new CustomException(StorageErrorCode.EMPTY_FILE_NOT_ALLOWED);
        if (items.size() > attachmentProps.maxFiles()) throw new CustomException(StorageErrorCode.UPLOAD_TICKET_LIMIT_EXCEEDED);
        if (items.stream().anyMatch(Objects::isNull)) {
            throw new CustomException(StorageErrorCode.INVALID_ATTACHMENT_METADATA);
        }

        userRepository.lockUserRow(userId);

        String tempPrefix = "community/user-" + userId + "/attachments";


        for (PresignUploadBatchRequest.Item value : items) {
            validateAttachmentItem(value);
        }

        List<PresignEngine.IssueItem> issueItems = new ArrayList<>(items.size());
        for (var item : items) {
            String ct = globalPresignMethods.normalize(item.contentType());
            issueItems.add(new PresignEngine.IssueItem(ct, item.size(), item.originalFilename()));
        }

        return new PresignUploadBatchResponse(
                presignEngine.issueUploadBatch(
                        userId,
                        UploadPurpose.COMMUNITY_POST_ATTACHMENT,
                        tempPrefix,
                        issueItems,
                        attachmentProps.maxFiles()
                )
        );
    }

    /**
     * 게시글 저장/수정 시 첨부 교체(consume + 정렬)
     * - sortOrder=0의 파일이 "이미지"이면 finalPrefix=/thumbnail 로 이동
     * - sortOrder=0의 파일이 "이미지 아님(pdf 등)"이면 finalPrefix=/attachments 로 이동 (썸네일 없음)
     * - sortOrder>=1은 항상 /attachments
     */
    @Transactional
    public void replace(Posts post, Long userId, List<AttachmentRequest> attachments) {
        validateAttachmentRequests(attachments);

        // 기존 활성 첨부파일
        List<PostAttachments> oldActive =
                postAttachmentsRepository.findByPost_IdAndStatusTrueOrderBySortOrderAscIdAsc(post.getId());

        Set<String> oldKeys = new HashSet<>();
        for (PostAttachments a : oldActive) {
            if (StringUtils.hasText(a.getFileKey())) oldKeys.add(a.getFileKey());
            // thumbnailKey는 더 이상 여기서 관리 안 함
        }

        // 기존 활성건 soft delete
        postAttachmentsRepository.softDeleteByPostId(post.getId());

        // 새 첨부 없으면: 기존 파일 전부 삭제 예약
        if (attachments == null || attachments.isEmpty()) {
            registerAfterCommitDelete(oldActive, Set.of());
            return;
        }

        String finalThumbPrefix  = "community/posts/post-" + post.getId() + "/thumbnail";
        String finalAttachPrefix = "community/posts/post-" + post.getId() + "/attachments";

        List<PostAttachments> toSave = new ArrayList<>();
        int order = 0;

        Set<String> newFinalKeys = new HashSet<>();
        Set<String> consumedThisRequest = new HashSet<>();

        for (AttachmentRequest req : attachments) {
            if (req == null) continue;

            String inFileKey = req.fileKey();
            if (!StringUtils.hasText(inFileKey)) continue;

            // order==0이면 "이미지인지"를 보고 thumbnail 경로로 보낼지 결정
            String chosenPrefix;
            if (order == 0 && isThumbCandidateKey(inFileKey)) {
                chosenPrefix = finalThumbPrefix;
            } else {
                chosenPrefix = finalAttachPrefix;
            }

            String finalFileKey = resolveFinalKey(
                    userId,
                    post.getId(),
                    oldKeys,
                    consumedThisRequest,
                    inFileKey,
                    chosenPrefix
            );

            newFinalKeys.add(finalFileKey);

            toSave.add(PostAttachments.create(
                    post,
                    finalFileKey,
                    req.width(),
                    req.height(),
                    req.fileSize(),
                    order
            ));

            order++;
        }

        if (toSave.isEmpty()) {
            registerAfterCommitDelete(oldActive, Set.of());
            return;
        }

        postAttachmentsRepository.saveAll(toSave);
        registerAfterCommitDelete(oldActive, newFinalKeys);
    }

    private void validateAttachmentItem(PresignUploadBatchRequest.Item item) {
        String ct = globalPresignMethods.normalize(item.contentType());

        if (!StringUtils.hasText(item.originalFilename())
                || item.originalFilename().length() > 255
                || item.originalFilename().chars().anyMatch(Character::isISOControl)
                || item.originalFilename().contains("/")
                || item.originalFilename().contains("\\")) {
            throw new CustomException(StorageErrorCode.INVALID_ATTACHMENT_METADATA);
        }
        if (item.size() <= 0) throw new CustomException(StorageErrorCode.EMPTY_FILE_NOT_ALLOWED);
        if (item.size() > attachmentProps.maxFileSizeBytes()) throw new CustomException(StorageErrorCode.FILE_TOO_LARGE);

        if (!StringUtils.hasText(ct) || !attachmentProps.allowedContentTypes().contains(ct)) {
            throw new CustomException(StorageErrorCode.UNSUPPORTED_CONTENT_TYPE);
        }
    }

    private void validateAttachmentRequests(List<AttachmentRequest> attachments) {
        if (attachments == null) return;
        if (attachments.size() > attachmentProps.maxFiles()) {
            throw new CustomException(StorageErrorCode.UPLOAD_TICKET_LIMIT_EXCEEDED);
        }

        Set<String> keys = new HashSet<>();
        for (AttachmentRequest attachment : attachments) {
            if (attachment == null
                    || !StringUtils.hasText(attachment.fileKey())
                    || attachment.fileKey().length() > 500
                    || attachment.fileKey().chars().anyMatch(Character::isISOControl)
                    || (attachment.width() == null) != (attachment.height() == null)
                    || (attachment.width() != null && attachment.width() <= 0)
                    || (attachment.height() != null && attachment.height() <= 0)
                    || (attachment.fileSize() != null && (attachment.fileSize() <= 0
                        || attachment.fileSize() > attachmentProps.maxFileSizeBytes()))) {
                throw new CustomException(StorageErrorCode.INVALID_ATTACHMENT_METADATA);
            }
            if (!keys.add(attachment.fileKey())) {
                throw new CustomException(StorageErrorCode.DUPLICATE_ATTACHMENT_KEY);
            }
        }
    }

    private boolean isThumbCandidateKey(String key) {
        if (!StringUtils.hasText(key)) return false;
        String k = key.toLowerCase(Locale.ROOT);
        return k.endsWith(".jpg") || k.endsWith(".jpeg") || k.endsWith(".png") || k.endsWith(".webp");
    }

    private String resolveFinalKey(
            Long userId,
            Long postId,
            Set<String> oldKeys,
            Set<String> consumedThisRequest,
            String keyFromClient,
            String finalPrefix
    ) {
        if (oldKeys.contains(keyFromClient)) return keyFromClient;
        if (!consumedThisRequest.add(keyFromClient)) return keyFromClient;

        return presignEngine.consume(
                userId,
                UploadPurpose.COMMUNITY_POST_ATTACHMENT,
                UploadRefType.POST,
                postId,
                keyFromClient,
                finalPrefix
        );
    }

    @Transactional
    public void purgeAllByPostId(Long postId) {
        List<PostAttachments> oldActive =
                postAttachmentsRepository.findByPost_IdAndStatusTrueOrderBySortOrderAscIdAsc(postId);

        postAttachmentsRepository.softDeleteByPostId(postId);

        registerAfterCommitDelete(oldActive, Set.of());
    }

    private void registerAfterCommitDelete(List<PostAttachments> oldActive, Set<String> newKeys) {
        Set<String> deleteKeys = new HashSet<>();
        for (PostAttachments a : oldActive) {
            String fk = a.getFileKey();
            if (StringUtils.hasText(fk) && !newKeys.contains(fk)) {
                deleteKeys.add(fk);
            }
        }
        globalPresignMethods.deleteAfterCommit(deleteKeys);
    }
}
