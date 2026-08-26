package CamNecT.server.domain.report.service;

import CamNecT.server.domain.activity.repository.external_activity.ExternalActivityRepository;
import CamNecT.server.domain.activity.repository.recruitment.TeamRecruitmentRepository;
import CamNecT.server.domain.chat.model.ChatRoom;
import CamNecT.server.domain.chat.repository.ChatRoomRepository;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.repository.Comments.CommentsRepository;
import CamNecT.server.domain.community.repository.Posts.PostsRepository;
import CamNecT.server.domain.report.dto.request.ReportCreateRequest;
import CamNecT.server.domain.report.model.Report;
import CamNecT.server.domain.report.model.ReportCase;
import CamNecT.server.domain.report.model.ReportCategory;
import CamNecT.server.domain.report.model.TargetType;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.ReportErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportTargetResolverTest {

    @Mock PostsRepository postsRepository;
    @Mock CommentsRepository commentsRepository;
    @Mock ExternalActivityRepository activityRepository;
    @Mock TeamRecruitmentRepository recruitmentRepository;
    @Mock ChatRoomRepository chatRoomRepository;
    @Mock UserRepository userRepository;

    @InjectMocks ReportTargetResolver resolver;

    @Test
    void contentReportUsesActualAuthorResolvedByServer() {
        Users author = Users.builder().userId(2L).name("author").build();
        Posts post = Posts.builder().id(100L).user(author).build();
        ReportCreateRequest request = request(2L, 100L);
        when(postsRepository.findById(100L)).thenReturn(Optional.of(post));

        ReportTargetResolver.ResolvedTarget resolved = resolver.resolve(1L, request);

        assertThat(resolved.author().getUserId()).isEqualTo(2L);
        assertThat(resolved.targetKey()).isEqualTo("COMMUNITY:100");
    }

    @Test
    void forgedReportedUserIsRejectedWhenItIsNotContentAuthor() {
        Users actualAuthor = Users.builder().userId(2L).name("author").build();
        Posts post = Posts.builder().id(100L).user(actualAuthor).build();
        ReportCreateRequest forged = request(3L, 100L);
        when(postsRepository.findById(100L)).thenReturn(Optional.of(post));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> resolver.resolve(1L, forged)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ReportErrorCode.REPORT_INVALID_TARGET);
    }

    @Test
    void chatReportsInOppositeDirectionsUseDifferentCaseKeys() {
        Users requester = Users.builder().userId(1L).name("requester").build();
        Users receiver = Users.builder().userId(2L).name("receiver").build();
        ChatRoom room = ChatRoom.builder().requester(requester).receiver(receiver).build();
        ReflectionTestUtils.setField(room, "id", 77L);
        when(chatRoomRepository.findById(77L)).thenReturn(Optional.of(room));

        ReportTargetResolver.ResolvedTarget requesterReportsReceiver = resolver.resolve(
                1L, request(2L, 77L, TargetType.CHAT));
        ReportTargetResolver.ResolvedTarget receiverReportsRequester = resolver.resolve(
                2L, request(1L, 77L, TargetType.CHAT));

        assertThat(requesterReportsReceiver.targetKey()).isEqualTo("CHAT:77:2");
        assertThat(receiverReportsRequester.targetKey()).isEqualTo("CHAT:77:1");
    }

    @Test
    void chatReportByOutsiderIsRejected() {
        Users requester = Users.builder().userId(1L).name("requester").build();
        Users receiver = Users.builder().userId(2L).name("receiver").build();
        ChatRoom room = ChatRoom.builder().requester(requester).receiver(receiver).build();
        ReflectionTestUtils.setField(room, "id", 77L);
        when(chatRoomRepository.findById(77L)).thenReturn(Optional.of(room));

        CustomException exception = assertThrows(CustomException.class,
                () -> resolver.resolve(3L, request(2L, 77L, TargetType.CHAT)));

        assertThat(exception.getErrorCode()).isEqualTo(ReportErrorCode.REPORT_INVALID_TARGET);
    }

    @Test
    void userReportUsesReportedUserAsTarget() {
        Users target = Users.builder().userId(2L).name("target").build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        ReportTargetResolver.ResolvedTarget resolved = resolver.resolve(
                1L, request(2L, null, TargetType.USER));

        assertThat(resolved.targetKey()).isEqualTo("USER:2");
        assertThat(resolved.targetId()).isEqualTo(2L);
    }

    @Test
    void nullTargetTypeIsRejected() {
        CustomException exception = assertThrows(CustomException.class,
                () -> resolver.resolve(1L, request(2L, 100L, null)));

        assertThat(exception.getErrorCode()).isEqualTo(ReportErrorCode.REPORT_INVALID_TARGET);
    }

    @Test
    void persistedCaseWithLegacyClientSuppliedOwnerIsRejected() {
        Users actualAuthor = Users.builder().userId(2L).name("actual").build();
        Users forgedAuthor = Users.builder().userId(3L).name("forged").build();
        Posts post = Posts.builder().id(100L).user(actualAuthor).build();
        ReportCase reportCase = ReportCase.open(
                "COMMUNITY:100",
                forgedAuthor,
                100L,
                TargetType.COMMUNITY
        );
        Report legacyReport = new Report(
                reportCase,
                1L,
                3L,
                100L,
                TargetType.COMMUNITY,
                ReportCategory.OTHER,
                "title",
                "context"
        );
        when(postsRepository.findById(100L)).thenReturn(Optional.of(post));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> resolver.validateStoredCase(reportCase, List.of(legacyReport))
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ReportErrorCode.REPORT_TARGET_INTEGRITY_UNVERIFIED);
    }

    @Test
    void persistedChatCaseIsResolvedFromReporterAndRoomParticipants() {
        Users requester = Users.builder().userId(1L).name("requester").build();
        Users receiver = Users.builder().userId(2L).name("receiver").build();
        ChatRoom room = ChatRoom.builder().requester(requester).receiver(receiver).build();
        ReflectionTestUtils.setField(room, "id", 77L);
        ReportCase reportCase = ReportCase.open("CHAT:77:2", receiver, 77L, TargetType.CHAT);
        Report report = new Report(
                reportCase,
                1L,
                2L,
                77L,
                TargetType.CHAT,
                ReportCategory.OTHER,
                "title",
                "context"
        );
        when(chatRoomRepository.findById(77L)).thenReturn(Optional.of(room));

        resolver.validateStoredCase(reportCase, List.of(report));
    }

    private static ReportCreateRequest request(Long reportedUserId, Long postId) {
        return request(reportedUserId, postId, TargetType.COMMUNITY);
    }

    private static ReportCreateRequest request(Long reportedUserId, Long postId, TargetType targetType) {
        return new ReportCreateRequest(
                reportedUserId,
                postId,
                targetType,
                ReportCategory.OTHER,
                "title",
                "context",
                null
        );
    }
}
