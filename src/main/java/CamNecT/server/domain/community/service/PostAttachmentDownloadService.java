package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.model.Posts.PostAttachments;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.enums.ContentAccessStatus;
import CamNecT.server.domain.community.model.enums.PostAccessType;
import CamNecT.server.domain.community.model.enums.PostStatus;
import CamNecT.server.domain.community.repository.Posts.PostAccessRepository;
import CamNecT.server.domain.community.repository.Posts.PostAttachmentsRepository;
import CamNecT.server.domain.community.repository.Posts.PostsRepository;
import CamNecT.server.global.point.service.PointService;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.CommunityErrorCode;
import CamNecT.server.global.storage.dto.response.PresignDownloadResponse;
import CamNecT.server.global.storage.model.UploadTicket;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.service.PresignEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PostAttachmentDownloadService {

    @Value("${app.point.cost.question-view:100}")
    private int questionViewCost;

    private final PostsRepository postsRepository;
    private final PostAttachmentsRepository postAttachmentsRepository;
    private final PostAccessRepository postAccessRepository;
    private final PointService pointService;

    private final PresignEngine presignEngine;
    private final UploadTicketRepository uploadTicketRepository;

    @Transactional(readOnly = true)
    public PresignDownloadResponse presignDownload(Long userId, Long postId, Long attachmentId) {

        Posts post = postsRepository.findById(postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_NOT_FOUND));

        if (!post.getStatus().isPublished()) {
            throw new CustomException(CommunityErrorCode.POST_NOT_PUBLISHED);
        }

        ContentAccessStatus access = computeAccessStatus(userId, postId, post);

        if (!access.canReadProtectedContent()) {
            throw new CustomException(CommunityErrorCode.POST_FORBIDDEN);
        }

        PostAttachments att = postAttachmentsRepository.findByIdAndPost_IdAndStatusTrue(attachmentId, postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.ATTACHMENT_NOT_FOUND));

        String key = att.getFileKey();
        if (!StringUtils.hasText(key)) {
            throw new CustomException(CommunityErrorCode.ATTACHMENT_NOT_FOUND);
        }

        var ticketOpt = uploadTicketRepository.findByStorageKey(key);
        String filename = ticketOpt.map(UploadTicket::getOriginalFilename).orElse(null);
        String contentType = ticketOpt.map(UploadTicket::getContentType).orElse(null);

        return presignEngine.presignDownload(key, filename, contentType);
    }

    private ContentAccessStatus computeAccessStatus(Long userId, Long postId, Posts post) {
        if (post.getAccessType() != PostAccessType.POINT_REQUIRED) {
            return ContentAccessStatus.GRANTED;
        }

        if (Objects.equals(userId, post.getUser().getUserId())) return ContentAccessStatus.GRANTED;
        if (postAccessRepository.existsByPost_IdAndUser_UserId(postId, userId)) return ContentAccessStatus.GRANTED;

        int myPoints = pointService.getBalance(userId);
        return (myPoints >= questionViewCost)
                ? ContentAccessStatus.NEED_PURCHASE
                : ContentAccessStatus.INSUFFICIENT_POINTS;
    }
}
