# Spring AI workshop

This repository is used to demonstrate Spring AI step by step. Every step is a git branch; each branch builds and
runs on its own, so you can check out any step and see the intermediate result.

| Branch | What it adds | Main files |
|---|---|---|
| `raw` | Starting point: auth-service (JWT), chat UI and an **empty chat-service** that has all Spring AI dependencies but no AI yet (it only echoes your message) | `chat-service/pom.xml`, `chat-service/.../chat/ChatService.java` |
| `step1-configure-springai-props` | Gemini configuration (`spring.ai.google.genai.*`), the `ChatClient` and the first call to the model | `application.properties`, `ChatConfig.java`, `ChatService.java`, `.env.example` |
| `step2-system-prompt` | Default system prompt with template parameters (user name, date and time zone) | `ChatConfig.java`, `ChatService.java` |
| `step3-streaming` | Streaming answers token by token (`stream().content()` → server-sent events) | `ChatService.java` |
| `step4-chat-memory` | Conversation memory: `MessageChatMemoryAdvisor` + `MessageWindowChatMemory` (in memory), history and "New chat" | `ChatConfig.java`, `ChatService.java` |
| `step5-jdbc-chat-memory` | Chat history stored in PostgreSQL (`chat-db`) with `JdbcChatMemoryRepository`, survives restarts | `ChatConfig.java`, `application.properties` |
| `step6-timeouts-and-errors` | Timeout for Gemini calls and readable error messages (quota, overload, timeout) | `ChatConfig.java`, `ChatController.java` |
| `step7-mcp-servers-from-ui` | MCP client: users connect MCP servers from the chat UI (🔌 MCP), their tools are offered to the model | `chat-service/.../mcp/*`, `McpPanel.tsx` |
| `step8-chat-logging` | Logging to see how the chat works: a custom advisor logs every model round (prompt size, tool calls, tokens, timings), every MCP tool call and every chat turn | `ChatLoggingAdvisor.java`, `LoggingToolCallback.java`, `application.properties` |
| `master` | Latest state of the project (same as the last step) | |

See what a step changes:

```bash
git diff raw step1-configure-springai-props
git diff step3-streaming step4-chat-memory -- chat-service
```

## Running a step

```bash
git checkout step1-configure-springai-props
cp .env.example .env            # once; then put your Gemini API key into .env (GEMINI_API_KEY)
docker compose up -d --build    # rebuild after every checkout
```

- Chat UI: http://localhost:3001 (register an account first).
- Gemini API key: https://aistudio.google.com/apikey. The free tier allows only a few requests per minute and per
  day per model; if you hit the limit, switch `GEMINI_MODEL` in `.env` and run `docker compose up -d chat-service`.
- Switching between branches keeps the databases. Starting completely fresh: `docker compose down -v`.

## What to check in each step

1. **raw** - register an account; the chat answers "AI is not connected yet" with an echo.
2. **step1** - the chat answers with Gemini (the whole answer arrives at once). Without a key you get
   "Gemini API key is not configured".
3. **step2** - ask "Who am I and what day is it today?": the model knows your name and the date.
4. **step3** - long answers ("Write an agenda for a sprint planning") appear word by word.
5. **step4** - ask a follow-up question ("Make it shorter"): the model remembers the conversation; reload the page and
   the history is still there; "New chat" clears it. `docker compose restart chat-service` loses the history.
6. **step5** - the same, but the history survives `docker compose restart chat-service` (table
   `spring_ai_chat_memory` in `chat-db`).
7. **step6** - with an invalid key or exhausted quota the chat shows a readable message instead of hanging or a stack
   trace.
8. **step7** - click 🔌 MCP, add `https://mcp.deepwiki.com/mcp` (name `deepwiki`), test and save it, then ask
   "Using deepwiki, what is the GitHub repository spring-projects/spring-ai about?".
9. **step8** - run `docker compose logs -f chat-service | grep -E "Chat turn|Model round|MCP tool"` and ask the deepwiki
   question again: you see round 1 asking for a tool, the MCP call, round 2 answering, with tokens and timings. Set
   `CHAT_LOG_LEVEL=DEBUG` in `.env` and run `docker compose up -d chat-service` to also see the full prompt (system
   prompt, history, tool results).
