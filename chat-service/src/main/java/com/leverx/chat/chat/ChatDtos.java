package com.leverx.chat.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ChatDtos {

    private ChatDtos() {
    }

    /**
     * @param timeZone IANA zone of the user's browser (e.g. "Europe/Warsaw"), used so the model knows "today"
     */
    public record ChatRequest(@NotBlank @Size(max = 4000) String message, @Size(max = 64) String timeZone) {
    }

    /** One streamed piece of the assistant answer; JSON-wrapped so whitespace survives SSE framing. */
    public record ChatChunk(String text) {
    }

    public record ChatMessage(String role, String text) {
    }
}
