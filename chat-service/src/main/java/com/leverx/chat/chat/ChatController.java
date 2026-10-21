package com.leverx.chat.chat;

import com.leverx.chat.chat.ChatDtos.ChatChunk;
import com.leverx.chat.chat.ChatDtos.ChatMessage;
import com.leverx.chat.chat.ChatDtos.ChatRequest;
import jakarta.validation.Valid;
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

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Sends a message and streams the answer as server-sent events: "chunk" events carry pieces of the answer,
     * a final "done" event marks the end.
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatChunk>> chat(@Valid @RequestBody ChatRequest request, @AuthenticationPrincipal Jwt jwt) {
        return chatService.stream(jwt, request.message(), request.timeZone())
                .map(text -> ServerSentEvent.builder(new ChatChunk(text)).event("chunk").build())
                .concatWith(Flux.just(ServerSentEvent.builder(new ChatChunk("")).event("done").build()));
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
