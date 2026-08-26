package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.enums.BoardCode;
import CamNecT.server.domain.community.model.enums.ContentAccessStatus;
import CamNecT.server.domain.community.model.enums.PostAccessType;
import CamNecT.server.domain.community.repository.Comments.AcceptedCommentsRepository;
import CamNecT.server.domain.community.repository.Posts.PostAccessRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.CommunityErrorCode;
import CamNecT.server.global.point.service.PointService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPostAccessPolicy {

    private final AcceptedCommentsRepository acceptedCommentsRepository;
    private final PostAccessRepository postAccessRepository;
    private final PointService pointService;

    @Value("${app.point.cost.question-view:100}")
    private int questionViewCost;

    /**
     * 질문은 답변 채택 전까지 공개하고, 채택된 시점부터 유료 열람 정책을 적용한다.
     * INFO의 유료 정책은 확정 전이므로 현재는 활성화하지 않는다.
     */
    public boolean isPaywallActive(Posts post, boolean hasAcceptedComment) {
        return post.getAccessType() == PostAccessType.POINT_REQUIRED
                && post.getBoard().getCode() == BoardCode.QUESTION
                && hasAcceptedComment;
    }

    public boolean isPaywallActive(Posts post) {
        if (!isPaywallActive(post, true)) return false;
        return isPaywallActive(post, acceptedCommentsRepository.existsByPost_Id(post.getId()));
    }

    public AccessDecision evaluate(Long userId, boolean adminRead, Posts post, boolean hasAcceptedComment) {
        if (!isPaywallActive(post, hasAcceptedComment)) {
            return AccessDecision.free();
        }

        if (userId == null) {
            return new AccessDecision(
                    ContentAccessStatus.LOGIN_REQUIRED,
                    questionViewCost,
                    null,
                    true
            );
        }

        if (adminRead
                || isOwner(userId, post)
                || isAcceptedAnswerAuthor(userId, post.getId())
                || postAccessRepository.existsByPost_IdAndUser_UserId(post.getId(), userId)) {
            return new AccessDecision(
                    ContentAccessStatus.GRANTED,
                    questionViewCost,
                    null,
                    true
            );
        }

        int myPoints = pointService.getBalance(userId);
        ContentAccessStatus status = myPoints >= questionViewCost
                ? ContentAccessStatus.NEED_PURCHASE
                : ContentAccessStatus.INSUFFICIENT_POINTS;

        return new AccessDecision(status, questionViewCost, myPoints, true);
    }

    public void requireReadable(Long userId, Posts post, boolean adminRead) {
        if (!isPaywallActive(post)) return;

        if (userId != null && (adminRead
                || isOwner(userId, post)
                || isAcceptedAnswerAuthor(userId, post.getId())
                || postAccessRepository.existsByPost_IdAndUser_UserId(post.getId(), userId))) {
            return;
        }

        throw new CustomException(CommunityErrorCode.POST_FORBIDDEN);
    }

    public boolean isAcceptedAnswerAuthor(Long userId, Long postId) {
        return userId != null
                && acceptedCommentsRepository.existsByPost_IdAndComment_UserId(postId, userId);
    }

    private boolean isOwner(Long userId, Posts post) {
        return Objects.equals(userId, post.getUser().getUserId());
    }

    public record AccessDecision(
            ContentAccessStatus status,
            Integer requiredPoints,
            Integer myPoints,
            boolean paywallActive
    ) {
        private static AccessDecision free() {
            return new AccessDecision(ContentAccessStatus.GRANTED, null, null, false);
        }

        public boolean canRead() {
            return status.canReadProtectedContent();
        }
    }
}
