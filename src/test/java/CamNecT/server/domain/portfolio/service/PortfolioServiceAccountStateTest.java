package CamNecT.server.domain.portfolio.service;

import CamNecT.server.domain.community.service.AuthorAssembler;
import CamNecT.server.domain.portfolio.dto.request.PortfolioRequest;
import CamNecT.server.domain.portfolio.model.PortfolioProject;
import CamNecT.server.domain.portfolio.repository.PortfolioAssetRepository;
import CamNecT.server.domain.portfolio.repository.PortfolioRepository;
import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.auth.AccountAccessGuard;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.bydomains.AuthErrorCode;
import CamNecT.server.global.storage.repository.UploadTicketRepository;
import CamNecT.server.global.storage.service.PresignEngine;
import CamNecT.server.global.storage.service.PublicUrlIssuer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceAccountStateTest {

    @Mock UserRepository userRepository;
    @Mock AccountAccessGuard accountAccessGuard;
    @Mock PortfolioRepository portfolioRepository;
    @Mock PortfolioAssetRepository portfolioAssetRepository;
    @Mock UploadTicketRepository uploadTicketRepository;
    @Mock AuthorAssembler authorAssembler;
    @Mock PresignEngine presignEngine;
    @Mock PublicUrlIssuer publicUrlIssuer;
    @Mock PortfolioAttachmentService portfolioAttachmentService;

    @InjectMocks PortfolioService service;

    @ParameterizedTest(name = "{0} actor cannot execute {1}")
    @MethodSource("blockedMutations")
    void inaccessibleActorCannotMutatePortfolioOrFiles(UserStatus status, Mutation mutation) {
        AuthErrorCode expected = status == UserStatus.WITHDRAWN
                ? AuthErrorCode.USER_WITHDRAWN
                : AuthErrorCode.USER_SUSPENDED;
        doThrow(new CustomException(expected))
                .when(accountAccessGuard).requireAccessibleForUpdate(1L);

        CustomException exception = assertThrows(CustomException.class,
                () -> invoke(mutation, 1L, 1L));

        assertThat(exception.getErrorCode()).isEqualTo(expected);
        verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        verifyNoInteractions(portfolioRepository, portfolioAttachmentService);
    }

    @ParameterizedTest
    @EnumSource(value = Mutation.class, names = {"UPDATE", "DELETE", "TOGGLE_PUBLIC", "TOGGLE_FAVORITE"})
    void actorUserLockAlwaysPrecedesPortfolioRowLock(Mutation mutation) {
        Users actor = Users.builder().userId(1L).status(UserStatus.ACTIVE).build();
        PortfolioProject project = project(10L, 1L);
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(actor);
        when(portfolioRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(project));

        invoke(mutation, 1L, 1L);

        InOrder order = inOrder(accountAccessGuard, portfolioRepository, portfolioAttachmentService);
        order.verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        order.verify(portfolioRepository).findByIdForUpdate(10L);
        if (mutation == Mutation.UPDATE) {
            order.verify(portfolioAttachmentService).applyOnUpdate(
                    project,
                    1L,
                    "temp/new-thumbnail.png",
                    List.of()
            );
        }
        if (mutation == Mutation.DELETE) {
            order.verify(portfolioAttachmentService).deleteAllFilesAfterCommit(project);
        }
    }

    @Test
    void createLocksActorBeforeCreatingPortfolioAndConsumingFiles() {
        Users actor = Users.builder().userId(1L).status(UserStatus.ACTIVE).build();
        when(accountAccessGuard.requireAccessibleForUpdate(1L)).thenReturn(actor);
        when(portfolioRepository.save(any(PortfolioProject.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create(1L, 1L, createRequest());

        InOrder order = inOrder(accountAccessGuard, portfolioRepository, portfolioAttachmentService);
        order.verify(accountAccessGuard).requireAccessibleForUpdate(1L);
        order.verify(portfolioRepository).save(any(PortfolioProject.class));
        order.verify(portfolioAttachmentService).applyOnCreate(
                any(PortfolioProject.class),
                eq(1L),
                eq("temp/thumbnail.png"),
                eq(List.of())
        );
    }

    @Test
    void activeAdminCanStillDeleteAnotherUsersPortfolio() {
        Users admin = Users.builder()
                .userId(9L)
                .status(UserStatus.ACTIVE)
                .role(UserRole.ADMIN)
                .build();
        PortfolioProject project = project(10L, 1L);
        when(accountAccessGuard.requireAccessibleForUpdate(9L)).thenReturn(admin);
        when(portfolioRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(project));

        service.delete(9L, 1L, 10L);

        verify(accountAccessGuard).requireAccessibleForUpdate(9L);
        verify(userRepository, never()).existsByUserIdAndRole(anyLong(), any());
        verify(portfolioAttachmentService).deleteAllFilesAfterCommit(project);
        verify(portfolioRepository).delete(project);
    }

    private void invoke(Mutation mutation, Long actorId, Long ownerId) {
        switch (mutation) {
            case CREATE -> service.create(actorId, ownerId, createRequest());
            case UPDATE -> service.update(actorId, ownerId, 10L, updateRequest());
            case DELETE -> service.delete(actorId, ownerId, 10L);
            case TOGGLE_PUBLIC -> service.togglePublic(actorId, ownerId, 10L);
            case TOGGLE_FAVORITE -> service.toggleFavorite(actorId, ownerId, 10L);
        }
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> blockedMutations() {
        return Stream.of(UserStatus.SUSPENDED, UserStatus.WITHDRAWN)
                .flatMap(status -> Stream.of(Mutation.values())
                        .map(mutation -> org.junit.jupiter.params.provider.Arguments.of(status, mutation)));
    }

    private static PortfolioRequest createRequest() {
        return new PortfolioRequest(
                "portfolio",
                "description",
                LocalDate.of(2026, 8, 1),
                null,
                "backend",
                List.of("Java"),
                "review",
                "temp/thumbnail.png",
                List.of()
        );
    }

    private static PortfolioRequest updateRequest() {
        return new PortfolioRequest(
                "updated portfolio",
                "description",
                LocalDate.of(2026, 8, 1),
                null,
                "backend",
                List.of("Java"),
                "review",
                "temp/new-thumbnail.png",
                List.of()
        );
    }

    private static PortfolioProject project(Long portfolioId, Long userId) {
        return PortfolioProject.builder()
                .portfolioId(portfolioId)
                .userId(userId)
                .title("portfolio")
                .startDate(LocalDate.of(2026, 8, 1))
                .isPublic(true)
                .createdAt(LocalDate.of(2026, 8, 1))
                .updatedAt(LocalDate.of(2026, 8, 1))
                .build();
    }

    private enum Mutation {
        CREATE,
        UPDATE,
        DELETE,
        TOGGLE_PUBLIC,
        TOGGLE_FAVORITE
    }
}
