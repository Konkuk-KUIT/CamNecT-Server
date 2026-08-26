package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.model.Posts.PostAttachments;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.repository.Posts.PostAttachmentsRepository;
import CamNecT.server.domain.community.repository.Posts.PostsRepository;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.CommunityErrorCode;
import CamNecT.server.global.storage.dto.response.PresignDownloadResponse;
import CamNecT.server.global.storage.model.UploadTicket;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.service.PresignEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PostAttachmentDownloadService {

    private final PostsRepository postsRepository;
    private final PostAttachmentsRepository postAttachmentsRepository;
    private final CommunityPostAccessPolicy postAccessPolicy;
    private final UserRepository userRepository;

    private final PresignEngine presignEngine;
    private final UploadTicketRepository uploadTicketRepository;

    @Transactional(readOnly = true)
    public PresignDownloadResponse presignDownload(Long userId, Long postId, Long attachmentId) {

        Posts post = postsRepository.findById(postId)
                .orElseThrow(() -> new CustomException(CommunityErrorCode.POST_NOT_FOUND));

        if (!post.getStatus().isPublished()) {
            throw new CustomException(CommunityErrorCode.POST_NOT_PUBLISHED);
        }

        boolean adminRead = userRepository.existsByUserIdAndRole(userId, UserRole.ADMIN);
        postAccessPolicy.requireReadable(userId, post, adminRead);

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
}
