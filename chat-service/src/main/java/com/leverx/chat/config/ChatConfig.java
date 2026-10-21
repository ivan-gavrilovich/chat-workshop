package com.leverx.chat.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    static final String SYSTEM_PROMPT = """
            You are the assistant tasked with helping the developers in maintaining the application.
            You help developers with questions about debugging errors and checking system resources.
            You cannot access the resources use and error data yet: if the user asks to do so,
            explain how to do it from terminal (check utilization via terminal utils for linux and check error reports by directly connecting to postgresql database at port 5434) and say that access to this information through chat is coming soon.
            Be concise and friendly. Use Markdown for lists and code when helpful.

            The user you are talking to is {userName} ({userEmail}).
            Current date and time for the user: {now} ({timeZone}).
            """;

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }
}
