package com.leverx.chat.chat;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import org.junit.jupiter.api.Test;

import java.io.InterruptedIOException;

import static org.assertj.core.api.Assertions.assertThat;

class ChatControllerTest {

    @Test
    void describesProviderFailuresForUsers() {
        assertThat(ChatController.describe(new RuntimeException("Failed", new InterruptedIOException("Canceled"))))
                .contains("did not answer in time");
        assertThat(ChatController.describe(new RuntimeException("Failed", new ClientException(429, "RESOURCE_EXHAUSTED", "quota"))))
                .contains("rate limit or quota");
        assertThat(ChatController.describe(new RuntimeException("Failed", new ServerException(503, "UNAVAILABLE", "overloaded"))))
                .contains("temporarily unavailable");
        assertThat(ChatController.describe(new RuntimeException("Failed",
                new ClientException(400, "INVALID_ARGUMENT", "API key not valid.\nDetails: {...}"))))
                .isEqualTo("The AI model failed to answer: API key not valid.");
    }
}
