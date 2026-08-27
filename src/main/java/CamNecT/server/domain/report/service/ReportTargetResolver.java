package CamNecT.server.domain.report.service;

import CamNecT.server.domain.activity.model.external_activity.ExternalActivity;
import CamNecT.server.domain.activity.model.recruitment.TeamRecruitment;
import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityRepository;
import CamNecT.server.domain.activity.repository.recruitment.TeamRecruitmentRepository;
import CamNecT.server.domain.chat.model.ChatRoom;
import CamNecT.server.domain.chat.repository.ChatRoomRepository;
import CamNecT.server.domain.community.model.Comments.Comments;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.repository.Comments.CommentsRepository;
import CamNecT.server.domain.community.repository.Posts.PostsRepository;
import CamNecT.server.domain.report.dto.request.ReportCreateRequest;
import CamNecT.server.domain.report.model.Report;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.ReportErrorCode;
import CamNecT.server.global.common.response.errorcode.bydomains.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
        ChatRoom room = chatRoomRepository.findById(requiredTargetId(request))
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

    public record ResolvedTarget(String targetKey, Long targetId, Users author) {
    }
}
