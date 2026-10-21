package com.leverx.chat.chat;

import com.leverx.chat.chat.ChatDtos.ChatMessage;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Starting point of the workshop: the chat API used by both UIs is in place, but there is no AI behind it yet.
 */
@Service
public class ChatService {

    /**
     * Answers with an echo until a model is connected with Spring AI.
     */
    public Flux<String> stream(Jwt user, String message, String timeZone) {
        return Flux.just("AI is not connected yet. You said: " + message);
    }

    public List<ChatMessage> history(Jwt user) {
        return List.of();
    }

    public void clear(Jwt user) {
        // nothing is stored yet
    }
}
