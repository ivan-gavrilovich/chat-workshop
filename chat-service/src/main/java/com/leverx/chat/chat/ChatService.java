package com.leverx.chat.chat;

import com.leverx.chat.chat.ChatDtos.ChatMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class ChatService {

    static final String API_KEY_PLACEHOLDER = "PUT_YOUR_GEMINI_API_KEY_HERE";

    private final ChatClient chatClient;
    private final boolean apiKeyConfigured;

    public ChatService(ChatClient chatClient, @Value("${spring.ai.google.genai.api-key}") String apiKey) {
        this.chatClient = chatClient;
        this.apiKeyConfigured = !apiKey.isBlank() && !API_KEY_PLACEHOLDER.equals(apiKey);
    }

    /**
     * Asks the model and waits for the whole answer, which is returned as a single chunk of the stream the UI reads.
     * Every message is answered on its own: the model does not see earlier messages.
     */
    public Flux<String> stream(Jwt user, String message, String timeZone) {
        if (!apiKeyConfigured) {
            throw new ChatUnavailableException(
                    "Gemini API key is not configured. Set GEMINI_API_KEY for chat-service and restart it.");
        }
        var answer = chatClient.prompt()
                .user(message)
                .call()
                .content();
        return Flux.just(answer);
    }

    /**
     * No conversation memory yet, so there is no history to show.
     */
    public List<ChatMessage> history(Jwt user) {
        return List.of();
    }

    public void clear(Jwt user) {
        // nothing is stored yet
    }
}
