package com.leverx.chat.chat;

import com.leverx.chat.chat.ChatDtos.ChatChunk;
import com.leverx.chat.chat.ChatDtos.ChatMessage;
import com.leverx.chat.chat.ChatDtos.ChatRequest;
import com.google.genai.errors.ApiException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.InterruptedIOException;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Sends a message and streams the answer as server-sent events: "chunk" events carry pieces of the answer,
     * a final "done" event marks the end, an "error" event reports a failure that happened mid-stream.
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatChunk>> chat(@Valid @RequestBody ChatRequest request, @AuthenticationPrincipal Jwt jwt) {
        return chatService.stream(jwt, request.message(), request.timeZone())
                .map(text -> ServerSentEvent.builder(new ChatChunk(text)).event("chunk").build())
                .concatWith(Flux.just(ServerSentEvent.builder(new ChatChunk("")).event("done").build()))
                .onErrorResume(e -> {
                    log.warn("Chat request failed", e);
                    return Flux.just(ServerSentEvent.builder(new ChatChunk(describe(e))).event("error").build());
                });
    }

    /**
     * Turns provider failures into messages that make sense to the user of the chat.
     */
    static String describe(Throwable error) {
        var cause = NestedExceptionUtils.getMostSpecificCause(error);
        if (cause instanceof InterruptedIOException) {
            return "Gemini did not answer in time. It may be overloaded right now, please try again.";
        }
        if (cause instanceof ApiException api) {
            return switch (api.code()) {
                case 429 -> "Gemini rate limit or quota exceeded for this API key. Please wait a bit and try again.";
                case 500, 502, 503, 504 -> "Gemini is temporarily unavailable or overloaded. Please try again shortly.";
                default -> "The AI model failed to answer: " + firstLine(api.message());
            };
        }
        return "The AI model failed to answer: " + firstLine(cause.getMessage());
    }

    private static String firstLine(String text) {
        return text == null ? "unknown error" : text.lines().findFirst().orElse("unknown error");
    }

    @GetMapping("/history")
    public List<ChatMessage> history(@AuthenticationPrincipal Jwt jwt) {
        return chatService.history(jwt);
    }

    @DeleteMapping("/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal Jwt jwt) {
        chatService.clear(jwt);
    }
}
