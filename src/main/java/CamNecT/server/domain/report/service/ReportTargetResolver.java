package CamNecT.server.domain.report.service;

import CamNecT.server.domain.activity.model.external_activity.ExternalActivity;
import CamNecT.server.domain.activity.model.recruitment.TeamRecruitment;
import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityRepository;
import CamNecT.server.domain.activity.repository.recruitment.TeamRecruitmentRepository;
import CamNecT.server.domain.chat.model.ChatRoom;
import CamNecT.server.domain.chat.repository.ChatRoomRepository;
import CamNecT.server.domain.community.model.Comments.Comments;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.enums.CommentStatus;
import CamNecT.server.domain.community.model.enums.PostStatus;
import CamNecT.server.domain.community.repository.Comments.CommentsRepository;
import CamNecT.server.domain.community.repository.Posts.PostsRepository;
import CamNecT.server.domain.report.dto.request.ReportCreateRequest;
import CamNecT.server.domain.report.model.Report;
import CamNecT.server.domain.report.model.ReportCase;
import CamNecT.server.domain.report.model.TargetType;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.ReportErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ReportTargetResolver {

    private final PostsRepository postsRepository;
    private final CommentsRepository commentsRepository;
    private final ExternalActivityRepository activityRepository;
    private final TeamRecruitmentRepository recruitmentRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;

    public ResolvedTarget resolve(Long reporterId, ReportCreateRequest request) {
        if (request.postType() == null) {
            throw invalidTarget();
        }

        return switch (request.postType()) {
            case COMMUNITY -> fromPost(request);
            case COMMUNITY_COMMENT -> fromComment(request);
            case ACTIVITY -> fromActivity(request);
            case ACTIVITY_RECRUITMENT -> fromRecruitment(request);
            case USER -> fromUser(request);
            case CHAT -> fromChat(reporterId, request);
        };
    }

    /**
     * Re-resolves and holds the target row after the reporter and author rows are locked.
     * This closes the window in which an administrator can delete the target between
     * initial coordinate resolution and report persistence.
     */
    public ResolvedTarget resolveForCreateLocked(Long reporterId, ReportCreateRequest request) {
        if (request.postType() == null) {
            throw invalidTarget();
        }

        return switch (request.postType()) {
            case COMMUNITY -> fromPostForCreateLocked(request);
            case COMMUNITY_COMMENT -> fromCommentForCreateLocked(request);
            case ACTIVITY -> fromActivityForCreateLocked(request);
            case ACTIVITY_RECRUITMENT -> fromRecruitmentForCreateLocked(request);
            case USER -> fromUser(request);
            case CHAT -> fromChatForCreateLocked(reporterId, request);
        };
    }

    /**
     * Re-resolves a persisted case from the current domain data. This must run
     * immediately before approving a case because early report rows trusted the
     * client-supplied reportedUserId.
     */
    public void validateStoredCase(ReportCase reportCase, List<Report> submissions) {
        if (reportCase == null || submissions == null || submissions.isEmpty()) {
            throw unverifiedTarget();
        }

        Users authoritativeAuthor = switch (reportCase.getTargetType()) {
            case COMMUNITY -> postsRepository.findById(reportCase.getTargetId())
                    .map(Posts::getUser)
                    .orElseThrow(this::unverifiedTarget);
            case COMMUNITY_COMMENT -> commentsRepository.findById(reportCase.getTargetId())
                    .map(Comments::getUserId)
                    .flatMap(userRepository::findById)
                    .orElseThrow(this::unverifiedTarget);
            case ACTIVITY -> activityRepository.findById(reportCase.getTargetId())
                    .map(ExternalActivity::getUser)
                    .orElseThrow(this::unverifiedTarget);
            case ACTIVITY_RECRUITMENT -> recruitmentRepository.findById(reportCase.getTargetId())
                    .map(TeamRecruitment::getUserId)
                    .flatMap(userRepository::findById)
                    .orElseThrow(this::unverifiedTarget);
            case USER -> userRepository.findById(reportCase.getTargetId())
                    .orElseThrow(this::unverifiedTarget);
            case CHAT -> resolveStoredChatAuthor(reportCase, submissions);
        };

        Long authoritativeUserId = authoritativeAuthor.getUserId();
        String authoritativeKey = Report.targetKeyFor(
                reportCase.getTargetType(),
                authoritativeUserId,
                reportCase.getTargetId()
        );
        if (!Objects.equals(reportCase.getReportedUser().getUserId(), authoritativeUserId)
                || !Objects.equals(reportCase.getTargetKey(), authoritativeKey)
                || submissions.stream().anyMatch(report -> !matchesCase(
                        report,
                        reportCase,
                        authoritativeUserId,
                        authoritativeKey
                ))) {
            throw unverifiedTarget();
        }
    }

    private ResolvedTarget fromPost(ReportCreateRequest request) {
        Posts post = postsRepository.findById(requiredTargetId(request))
                .orElseThrow(this::invalidTarget);
        return contentTarget(request, post.getId(), post.getUser());
    }

    private ResolvedTarget fromComment(ReportCreateRequest request) {
        Comments comment = commentsRepository.findById(requiredTargetId(request))
                .orElseThrow(this::invalidTarget);
        return contentTarget(request, comment.getId(), comment.getUserId());
    }

    private ResolvedTarget fromActivity(ReportCreateRequest request) {
        ExternalActivity activity = activityRepository.findById(requiredTargetId(request))
                .orElseThrow(this::invalidTarget);
        if (activity.getUser() == null) {
            throw invalidTarget();
        }
        return contentTarget(request, activity.getActivityId(), activity.getUser());
    }

    private ResolvedTarget fromRecruitment(ReportCreateRequest request) {
        TeamRecruitment recruitment = recruitmentRepository.findById(requiredTargetId(request))
                .orElseThrow(this::invalidTarget);
        return contentTarget(request, recruitment.getRecruitId(), recruitment.getUserId());
    }

    private ResolvedTarget fromUser(ReportCreateRequest request) {
        if (request.reportedPostId() != null) {
            throw invalidTarget();
        }
        Users user = userRepository.findById(request.reportedUserId())
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        return resolved(request, user.getUserId(), user);
    }

    private ResolvedTarget fromChat(Long reporterId, ReportCreateRequest request) {
        Long roomId = requiredTargetId(request);
        Long targetUserId = chatRoomRepository.findReportTargetUserId(roomId, reporterId)
                .orElseThrow(this::invalidTarget);
        Users targetUser = userRepository.findById(targetUserId)
                .orElseThrow(this::invalidTarget);

        return contentTarget(request, roomId, targetUser);
    }

    private ResolvedTarget fromPostForCreateLocked(ReportCreateRequest request) {
        Posts post = postsRepository.findByIdAndStatusForRead(
                        requiredTargetId(request),
                        PostStatus.PUBLISHED
                )
                .orElseThrow(this::invalidTarget);
        return contentTarget(request, post.getId(), post.getUser());
    }

    private ResolvedTarget fromCommentForCreateLocked(ReportCreateRequest request) {
        Long commentId = requiredTargetId(request);
        Long postId = commentsRepository.findPostIdByCommentId(commentId)
                .orElseThrow(this::invalidTarget);
        postsRepository.findByIdAndStatusForRead(postId, PostStatus.PUBLISHED)
                .orElseThrow(this::invalidTarget);
        Comments comment = commentsRepository.findByIdAndStatusForRead(commentId, CommentStatus.PUBLISHED)
                .orElseThrow(this::invalidTarget);
        return contentTarget(request, comment.getId(), comment.getUserId());
    }

    private ResolvedTarget fromActivityForCreateLocked(ReportCreateRequest request) {
        ExternalActivity activity = activityRepository.findByIdForRead(requiredTargetId(request))
                .orElseThrow(this::invalidTarget);
        if (activity.getUser() == null) {
            throw invalidTarget();
        }
        return contentTarget(request, activity.getActivityId(), activity.getUser());
    }

    private ResolvedTarget fromRecruitmentForCreateLocked(ReportCreateRequest request) {
        TeamRecruitment recruitment = recruitmentRepository.findByIdForRead(requiredTargetId(request))
                .orElseThrow(this::invalidTarget);
        return contentTarget(request, recruitment.getRecruitId(), recruitment.getUserId());
    }

    private ResolvedTarget fromChatForCreateLocked(Long reporterId, ReportCreateRequest request) {
        ChatRoom room = chatRoomRepository.findByIdForRead(requiredTargetId(request))
                .orElseThrow(this::invalidTarget);
        Users targetUser;
        if (Objects.equals(room.getRequester().getUserId(), reporterId)) {
            targetUser = room.getReceiver();
        } else if (Objects.equals(room.getReceiver().getUserId(), reporterId)) {
            targetUser = room.getRequester();
        } else {
            throw invalidTarget();
        }
        return contentTarget(request, room.getId(), targetUser);
    }

    private Users resolveStoredChatAuthor(ReportCase reportCase, List<Report> submissions) {
        ChatRoom room = chatRoomRepository.findById(reportCase.getTargetId())
                .orElseThrow(this::unverifiedTarget);
        Users authoritativeAuthor = null;

        for (Report report : submissions) {
            Users submissionTarget;
            if (Objects.equals(room.getRequester().getUserId(), report.getReporterId())) {
                submissionTarget = room.getReceiver();
            } else if (Objects.equals(room.getReceiver().getUserId(), report.getReporterId())) {
                submissionTarget = room.getRequester();
            } else {
                throw unverifiedTarget();
            }

            if (authoritativeAuthor != null
                    && !Objects.equals(authoritativeAuthor.getUserId(), submissionTarget.getUserId())) {
                throw unverifiedTarget();
            }
            authoritativeAuthor = submissionTarget;
        }

        if (authoritativeAuthor == null) {
            throw unverifiedTarget();
        }
        return authoritativeAuthor;
    }

    private boolean matchesCase(
            Report report,
            ReportCase reportCase,
            Long authoritativeUserId,
            String authoritativeKey
    ) {
        TargetType targetType = reportCase.getTargetType();
        Long expectedPostId = targetType == TargetType.USER ? null : reportCase.getTargetId();
        return report.getPostType() == targetType
                && Objects.equals(report.getReportedUserId(), authoritativeUserId)
                && Objects.equals(report.getReportedPostId(), expectedPostId)
                && Objects.equals(report.getTargetKey(), authoritativeKey);
    }

    private ResolvedTarget contentTarget(ReportCreateRequest request, Long targetId, Long actualAuthorId) {
        if (!Objects.equals(request.reportedUserId(), actualAuthorId)) {
            throw invalidTarget();
        }
        Users author = userRepository.findById(actualAuthorId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        return resolved(request, targetId, author);
    }

    private ResolvedTarget contentTarget(ReportCreateRequest request, Long targetId, Users author) {
        if (author == null || !Objects.equals(request.reportedUserId(), author.getUserId())) {
            throw invalidTarget();
        }
        return resolved(request, targetId, author);
    }

    private ResolvedTarget resolved(ReportCreateRequest request, Long targetId, Users author) {
        return new ResolvedTarget(
                Report.targetKeyFor(request.postType(), author.getUserId(), targetId),
                targetId,
                author
        );
    }

    private Long requiredTargetId(ReportCreateRequest request) {
        if (request.reportedPostId() == null) {
            throw invalidTarget();
        }
        return request.reportedPostId();
    }

    private CustomException invalidTarget() {
        return new CustomException(ReportErrorCode.REPORT_INVALID_TARGET);
    }

    private CustomException unverifiedTarget() {
        return new CustomException(ReportErrorCode.REPORT_TARGET_INTEGRITY_UNVERIFIED);
    }

    public record ResolvedTarget(String targetKey, Long targetId, Users author) {
    }
}
