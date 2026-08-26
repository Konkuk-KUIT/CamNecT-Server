package CamNecT.server.domain.community.service;

import CamNecT.server.domain.community.model.Boards;
import CamNecT.server.domain.community.model.Posts.Posts;
import CamNecT.server.domain.community.model.enums.BoardCode;
import CamNecT.server.domain.community.model.enums.ContentAccessStatus;
import CamNecT.server.domain.community.model.enums.PostAccessType;
import CamNecT.server.domain.community.model.enums.PostStatus;
import CamNecT.server.domain.community.repository.Comments.AcceptedCommentsRepository;
import CamNecT.server.domain.community.repository.Posts.PostAccessRepository;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.CommunityErrorCode;
import CamNecT.server.global.point.service.PointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityPostAccessPolicyTest {

    @Mock AcceptedCommentsRepository acceptedCommentsRepository;
    @Mock PostAccessRepository postAccessRepository;
    @Mock PointService pointService;

    @InjectMocks CommunityPostAccessPolicy policy;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(policy, "questionViewCost", 100);
    }

    @Test
    void paywallActivatesOnlyForAcceptedPointQuestion() {
        assertThat(policy.isPaywallActive(post(BoardCode.QUESTION, PostAccessType.POINT_REQUIRED), false)).isFalse();
        assertThat(policy.isPaywallActive(post(BoardCode.QUESTION, PostAccessType.POINT_REQUIRED), true)).isTrue();
        assertThat(policy.isPaywallActive(post(BoardCode.QUESTION, PostAccessType.FREE), true)).isFalse();
        assertThat(policy.isPaywallActive(post(BoardCode.INFO, PostAccessType.POINT_REQUIRED), true)).isFalse();
    }

    @Test
    void acceptedQuestionOwnerAnswerAuthorAndPurchaserAreGranted() {
        Posts post = post(BoardCode.QUESTION, PostAccessType.POINT_REQUIRED);

        CommunityPostAccessPolicy.AccessDecision owner = policy.evaluate(1L, false, post, true);
        when(acceptedCommentsRepository.existsByPost_IdAndComment_UserId(10L, 3L)).thenReturn(true);
        CommunityPostAccessPolicy.AccessDecision answerAuthor = policy.evaluate(3L, false, post, true);
        when(postAccessRepository.existsByPost_IdAndUser_UserId(10L, 2L)).thenReturn(true);
        CommunityPostAccessPolicy.AccessDecision purchaser = policy.evaluate(2L, false, post, true);

        assertThat(owner.status()).isEqualTo(ContentAccessStatus.GRANTED);
        assertThat(answerAuthor.status()).isEqualTo(ContentAccessStatus.GRANTED);
        assertThat(purchaser.status()).isEqualTo(ContentAccessStatus.GRANTED);
        assertThat(owner.requiredPoints()).isEqualTo(100);
        assertThat(owner.myPoints()).isNull();
        verifyNoInteractions(pointService);
    }

    @Test
    void acceptedQuestionNonPurchaserGetsBalanceBasedStatus() {
        Posts post = post(BoardCode.QUESTION, PostAccessType.POINT_REQUIRED);
        when(pointService.getBalance(2L)).thenReturn(100);
        when(pointService.getBalance(3L)).thenReturn(99);

        CommunityPostAccessPolicy.AccessDecision enough = policy.evaluate(2L, false, post, true);
        CommunityPostAccessPolicy.AccessDecision insufficient = policy.evaluate(3L, false, post, true);

        assertThat(enough.status()).isEqualTo(ContentAccessStatus.NEED_PURCHASE);
        assertThat(enough.myPoints()).isEqualTo(100);
        assertThat(insufficient.status()).isEqualTo(ContentAccessStatus.INSUFFICIENT_POINTS);
        assertThat(insufficient.myPoints()).isEqualTo(99);
    }

    @Test
    void unreadableAcceptedQuestionIsForbiddenButUnacceptedQuestionIsReadable() {
        Posts post = post(BoardCode.QUESTION, PostAccessType.POINT_REQUIRED);
        when(acceptedCommentsRepository.existsByPost_Id(10L)).thenReturn(false, true);

        assertDoesNotThrow(() -> policy.requireReadable(2L, post, false));
        CustomException exception = assertThrows(CustomException.class,
                () -> policy.requireReadable(2L, post, false));

        assertThat(exception.getErrorCode()).isEqualTo(CommunityErrorCode.POST_FORBIDDEN);
    }

    @Test
    void acceptedAnswerAuthorPassesReadableCheckWithoutPurchase() {
        Posts post = post(BoardCode.QUESTION, PostAccessType.POINT_REQUIRED);
        when(acceptedCommentsRepository.existsByPost_Id(10L)).thenReturn(true);
        when(acceptedCommentsRepository.existsByPost_IdAndComment_UserId(10L, 2L)).thenReturn(true);

        assertDoesNotThrow(() -> policy.requireReadable(2L, post, false));

        verify(postAccessRepository, never()).existsByPost_IdAndUser_UserId(anyLong(), anyLong());
        verifyNoInteractions(pointService);
    }

    @Test
    void administratorReadIsGrantedWithoutPurchaseOrBalanceLookup() {
        Posts post = post(BoardCode.QUESTION, PostAccessType.POINT_REQUIRED);
        when(acceptedCommentsRepository.existsByPost_Id(10L)).thenReturn(true);

        CommunityPostAccessPolicy.AccessDecision decision = policy.evaluate(99L, true, post, true);

        assertThat(decision.status()).isEqualTo(ContentAccessStatus.GRANTED);
        assertDoesNotThrow(() -> policy.requireReadable(99L, post, true));
        verify(acceptedCommentsRepository, never())
                .existsByPost_IdAndComment_UserId(anyLong(), anyLong());
        verifyNoInteractions(postAccessRepository, pointService);
    }

    private static Posts post(BoardCode boardCode, PostAccessType accessType) {
        return Posts.builder()
                .id(10L)
                .board(Boards.of(boardCode, boardCode.name()))
                .user(Users.builder().userId(1L).build())
                .title("제목")
                .content("본문")
                .status(PostStatus.PUBLISHED)
                .accessType(accessType)
                .build();
    }
}
