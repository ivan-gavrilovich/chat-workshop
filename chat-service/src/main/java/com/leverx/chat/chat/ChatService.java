package com.leverx.chat.chat;

import com.leverx.chat.chat.ChatDtos.ChatMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ChatService {

    static final String API_KEY_PLACEHOLDER = "PUT_YOUR_GEMINI_API_KEY_HERE";

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final boolean apiKeyConfigured;

    public ChatService(ChatClient chatClient, ChatMemory chatMemory,
                       @Value("${spring.ai.google.genai.api-key}") String apiKey) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.apiKeyConfigured = !apiKey.isBlank() && !API_KEY_PLACEHOLDER.equals(apiKey);
    }

    /**
     * Streams the assistant answer. Each user has a single conversation, keyed by the user id from the token.
     */
    public Flux<String> stream(Jwt user, String message, String timeZone) {
        if (!apiKeyConfigured) {
            throw new ChatUnavailableException(
                    "Gemini API key is not configured. Set GEMINI_API_KEY for chat-service and restart it.");
        }
        var zone = resolveZone(timeZone);
        return chatClient.prompt()
                .system(s -> s
                        .param("userName", String.valueOf(user.getClaimAsString("name")))
                        .param("userEmail", String.valueOf(user.getClaimAsString("email")))
                        .param("now", ZonedDateTime.now(zone).format(DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd HH:mm")))
                        .param("timeZone", zone.getId()))
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId(user)))
                .stream()
                .content();
    }

    public List<ChatMessage> history(Jwt user) {
        return chatMemory.get(conversationId(user)).stream()
                .filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
                .map(m -> new ChatMessage(m.getMessageType() == MessageType.USER ? "user" : "assistant", m.getText()))
                .toList();
    }

    public void clear(Jwt user) {
        chatMemory.clear(conversationId(user));
    }

    private static String conversationId(Jwt user) {
        return "user-" + user.getSubject();
    }

    private static ZoneId resolveZone(String timeZone) {
        try {
            return timeZone == null || timeZone.isBlank() ? ZoneOffset.UTC : ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }
}
