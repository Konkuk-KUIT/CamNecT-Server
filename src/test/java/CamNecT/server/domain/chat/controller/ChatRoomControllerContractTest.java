package CamNecT.server.domain.chat.controller;

import CamNecT.server.domain.chat.repository.ChatRequestRepository;
import CamNecT.server.domain.chat.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PatchMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChatRoomControllerContractTest {

    private final ChatService chatService = mock(ChatService.class);
    private final ChatRoomController controller = new ChatRoomController(
            chatService,
            mock(ChatRequestRepository.class)
    );

    @Test
    void exitRouteKeepsCompleteExitCompatibilityAlias() throws Exception {
        controller.exitRoom(99L, 1L);

        verify(chatService).exitOfChatRoom(99L, 1L);
        Method method = ChatRoomController.class.getMethod("exitRoom", Long.class, Long.class);
        assertThat(method.getAnnotation(PatchMapping.class).value())
                .containsExactly(
                        "/room/{roomId}/exit",
                        "/room/{roomId}/complete-exit"
                );
    }
}
