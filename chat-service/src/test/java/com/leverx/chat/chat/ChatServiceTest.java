package com.leverx.chat.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

class ChatServiceTest {

    private final ChatMemory chatMemory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .build();

    @Test
    void rejectsChatWhenApiKeyIsNotConfigured() {
        var service = new ChatService(mock(ChatClient.class), chatMemory, ChatService.API_KEY_PLACEHOLDER);

        assertThatThrownBy(() -> service.stream(user("1"), "hi", "Europe/Warsaw"))
                .isInstanceOf(ChatUnavailableException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    @Test
    void historyIsPerUserAndHidesSystemMessages() {
        var service = new ChatService(mock(ChatClient.class), chatMemory, "real-key");
        chatMemory.add("user-1", List.of(new SystemMessage("system"), new UserMessage("hi"), new AssistantMessage("hello")));
        chatMemory.add("user-2", List.of(new UserMessage("other user")));

        assertThat(service.history(user("1")))
                .extracting(ChatDtos.ChatMessage::role, ChatDtos.ChatMessage::text)
                .containsExactly(
                        tuple("user", "hi"),
                        tuple("assistant", "hello"));

        service.clear(user("1"));
        assertThat(service.history(user("1"))).isEmpty();
        assertThat(service.history(user("2"))).hasSize(1);
    }

    private static Jwt user(String id) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject(id)
                .claim("name", "Ivan").claim("email", "ivan@example.com").build();
    }
}
