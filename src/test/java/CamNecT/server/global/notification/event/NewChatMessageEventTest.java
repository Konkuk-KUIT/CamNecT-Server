package CamNecT.server.global.notification.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewChatMessageEventTest {

    @Test
    void messagePreviewDoesNotSplitUnicodeCodePoint() {
        String emoji = "😀";
        NewChatMessageEvent event = new NewChatMessageEvent(
                2L,
                1L,
                99L,
                emoji.repeat(41)
        );

        assertThat(event.message()).isEqualTo(emoji.repeat(40) + "...");
    }
}
