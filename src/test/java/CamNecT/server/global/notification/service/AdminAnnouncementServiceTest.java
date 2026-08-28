package CamNecT.server.global.notification.service;

import CamNecT.server.domain.users.model.UserRole;
import CamNecT.server.domain.users.model.UserStatus;
import CamNecT.server.domain.users.model.Users;
import CamNecT.server.domain.users.repository.UserRepository;
import CamNecT.server.global.common.exception.CustomException;
import CamNecT.server.global.common.response.errorcode.ErrorCode;
import CamNecT.server.global.notification.dto.request.AdminAnnouncementRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static CamNecT.server.global.notification.dto.request.AdminAnnouncementRequest.TargetType.ALL;
import static CamNecT.server.global.notification.dto.request.AdminAnnouncementRequest.TargetType.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAnnouncementServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminAnnouncementBatchService batchService = mock(AdminAnnouncementBatchService.class);
    private final AdminAnnouncementService service = new AdminAnnouncementService(userRepository, batchService);

    @BeforeEach
    void setUpAdmin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(
                Users.builder()
                        .userId(1L)
                        .status(UserStatus.ACTIVE)
                        .role(UserRole.ADMIN)
                        .build()
        ));
    }

    @Test
    void sendRejectsExternalLink() {
        AdminAnnouncementRequest request = new AdminAnnouncementRequest(
                "message",
                "https://example.com",
                ALL,
                null
        );

        CustomException exception = assertThrows(CustomException.class, () -> service.send(1L, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        verify(batchService, never()).dispatch(1L, request, List.of());
    }

    @Test
    void sendRejectsProtocolRelativeAndEncodedExternalLinks() {
        List<String> unsafeLinks = List.of(
                "//example.com/path",
                "/%2F%2Fexample.com/path",
                "/\\example.com/path"
        );

        for (String link : unsafeLinks) {
            AdminAnnouncementRequest request = new AdminAnnouncementRequest(
                    "message",
                    link,
                    USERS,
                    List.of(2L)
            );

            CustomException exception = assertThrows(CustomException.class, () -> service.send(1L, request));
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        }

        verify(batchService, never()).dispatch(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList()
        );
    }

    @Test
    void sendAllowsInternalLinkWithQueryAndFragment() {
        Users receiver = Users.builder()
                .userId(2L)
                .status(UserStatus.ACTIVE)
                .build();
        AdminAnnouncementRequest request = new AdminAnnouncementRequest(
                "message",
                "  /events/1?source=admin#detail  ",
                USERS,
                List.of(2L)
        );
        when(userRepository.findAllById(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(receiver));
        when(batchService.dispatch(1L, request, List.of(2L))).thenReturn(1L);

        long queued = service.send(1L, request);

        assertThat(queued).isEqualTo(1L);
        verify(batchService).dispatch(1L, request, List.of(2L));
    }

    @Test
    void sendAllUsesAnAscendingKeysetAcrossMoreThanOneBatch() {
        AdminAnnouncementRequest request = new AdminAnnouncementRequest(
                "message",
                "/announcements/1",
                ALL,
                null
        );
        Pageable firstPage = PageRequest.of(0, 500);
        List<Long> firstBatch = LongStream.rangeClosed(2L, 501L).boxed().toList();
        List<Long> secondBatch = List.of(502L);

        when(userRepository.findUserIdsByStatusAndUserIdGreaterThan(
                UserStatus.ACTIVE, 0L, firstPage
        )).thenReturn(new SliceImpl<>(firstBatch, firstPage, true));
        when(userRepository.findUserIdsByStatusAndUserIdGreaterThan(
                UserStatus.ACTIVE, 501L, firstPage
        )).thenReturn(new SliceImpl<>(secondBatch, firstPage, false));
        when(batchService.dispatch(1L, request, firstBatch)).thenReturn(500L);
        when(batchService.dispatch(1L, request, secondBatch)).thenReturn(1L);

        long queued = service.send(1L, request);

        assertThat(queued).isEqualTo(501L);
        ArgumentCaptor<Long> cursorCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository, times(2)).findUserIdsByStatusAndUserIdGreaterThan(
                org.mockito.ArgumentMatchers.eq(UserStatus.ACTIVE),
                cursorCaptor.capture(),
                pageableCaptor.capture()
        );
        assertThat(cursorCaptor.getAllValues()).containsExactly(0L, 501L);
        assertThat(pageableCaptor.getAllValues())
                .allSatisfy(pageable -> {
                    assertThat(pageable.getPageNumber()).isZero();
                    assertThat(pageable.getPageSize()).isEqualTo(500);
                });
        verify(batchService).dispatch(1L, request, firstBatch);
        verify(batchService).dispatch(1L, request, secondBatch);
    }
}
