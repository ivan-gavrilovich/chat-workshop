package com.leverx.chat.chat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class ChatServiceTest {

    private final ChatService chatService = new ChatService();

    @Test
    void echoesMessageUntilAiIsConnected() {
        assertThat(chatService.stream(user("1"), "hi", "Europe/Warsaw").collectList().block())
                .containsExactly("AI is not connected yet. You said: hi");
    }

    private static Jwt user(String id) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject(id)
                .claim("name", "Ivan").claim("email", "ivan@example.com").build();
    }
}
