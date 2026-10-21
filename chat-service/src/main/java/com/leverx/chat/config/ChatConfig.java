package com.leverx.chat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    /**
     * ChatClient.Builder is auto-configured by Spring AI for the model on the classpath (Gemini via Google GenAI),
     * using the spring.ai.google.genai.* properties.
     */
    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
