package com.leverx.chat.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.leverx.chat.tools.ServerToolsService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ChatConfig {

    static final String SYSTEM_PROMPT = """
            You are the assistant tasked with helping the developers in maintaining the application.
            You help developers with questions about debugging errors and checking system resources.
            You have tools to check current server resource utilization and recent error reports — use them when the user asks.
            Be concise and friendly. Use Markdown for lists and code when helpful.

            The user you are talking to is {userName} ({userEmail}).
            Current date and time for the user: {now} ({timeZone}).
            """;

    /**
     * Replaces Spring AI's auto-configured Gemini client only to add a timeout: the SDK default is "no timeout",
     * so a stalled call to Gemini would leave the chat waiting forever.
     */
    @Bean
    Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey,
                             @Value("${chat.gemini.timeout}") Duration timeout) {
        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder().timeout((int) timeout.toMillis()).build())
                .build();
    }

    @Bean
    ChatMemory chatMemory(ChatMemoryRepository repository, @Value("${chat.memory.max-messages}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(maxMessages)
                .build();
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory, ServerToolsService serverTools) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(serverTools)
                .build();
    }
}
