package com.leverx.chat.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ChatServiceTest {

    @Test
    void rejectsChatWhenApiKeyIsNotConfigured() {
        var service = new ChatService(mock(ChatClient.class), ChatService.API_KEY_PLACEHOLDER);

        assertThatThrownBy(() -> service.stream(user("1"), "hi", "Europe/Warsaw"))
                .isInstanceOf(ChatUnavailableException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    private static Jwt user(String id) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject(id)
                .claim("name", "Ivan").claim("email", "ivan@example.com").build();
    }
}
