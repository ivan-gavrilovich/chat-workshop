# CLAUDE.md

Spring AI chat workshop. The AI chat is built step by step on git branches;
every branch must build and run on its own. Attendee-facing guide: `WORKSHOP.md`.

## Services

| Service | Stack | Port (host) | Database |
|---|---|---|---|
| `auth-service` | Register/login, issues RS256 JWTs, JWKS at `/.well-known/jwks.json` | 8081 | `auth-db` :5433 |
| `chat-service` | AI chat: Spring AI 2.0.1, Gemini (Google GenAI), chat memory, MCP client | 8082 | `chat-db` :5434 |
| `chat-ui` | Standalone chat UI (React 19 + Vite), nginx | 3001 | - |

- nginx in `chat-ui` proxies `/api/auth` → auth-service, `/api/chat` (and `/api/mcp` on step7+) → chat-service.
  It re-resolves upstreams through Docker DNS (`resolver 127.0.0.11`), so re-created containers don't cause 502s.
- chat-service only verifies JWTs (JWKS from auth-service); it never sees passwords.

## Workshop branches

History is linear: `raw` → `step1` → … → `step8` = `master`. Each step is **one commit**, so
`git diff <previous> <step>` shows exactly what the step teaches. Steps 1-6 and 8 only touch `chat-service`
(plus `.env.example`/`docker-compose.yml` in step1 and step8); step7 also changes `chat-ui`.

| Branch | Adds | Rebuild after checkout | Check |
|---|---|---|---|
| `raw` | Everything except AI: chat-service has all Spring AI deps but `spring.ai.model.chat=none`, `ChatService` echoes | all: `docker compose up -d --build` | chat answers "AI is not connected yet. You said: …"; `/api/mcp` is 404 |
| `step1-configure-springai-props` | `spring.ai.google.genai.*` props, `ChatClient` bean, blocking `call()` | `docker compose up -d --build chat-service` | answer arrives in one chunk; placeholder key → 503 "Gemini API key is not configured" |
| `step2-system-prompt` | `defaultSystem(...)` with `{userName}`, `{userEmail}`, `{now}`, `{timeZone}` params | chat-service | "Who am I and what day is it?" |
| `step3-streaming` | `stream().content()` → SSE chunks | chat-service | long answers arrive in several chunks |
| `step4-chat-memory` | `MessageChatMemoryAdvisor` + `MessageWindowChatMemory` with `InMemoryChatMemoryRepository`, history/clear endpoints | chat-service | follow-up questions work; history empty after `docker compose restart chat-service` |
| `step5-jdbc-chat-memory` | auto-configured `JdbcChatMemoryRepository` (`initialize-schema=always`) | chat-service | history survives restart (table `spring_ai_chat_memory`) |
| `step6-timeouts-and-errors` | own Google GenAI `Client` with timeout (`chat.gemini.timeout`), error → SSE `error` event with readable text | chat-service | invalid key/quota → readable message in chat |
| `step7-mcp-servers-from-ui` | runtime MCP clients managed from the UI (🔌 MCP), table `mcp_server` (Flyway V1), encrypted headers, SSRF guard | all: `docker compose up -d --build` | add `https://mcp.deepwiki.com/mcp`, ask about a GitHub repo |
| `step8-chat-logging` (= `master`) | `ChatLoggingAdvisor` (after Spring AI's `ToolCallingAdvisor`, so once per model round), turn stats in `ChatService`, `LoggingToolCallback` around MCP tools, MCP connect/settings logs, `CHAT_LOG_LEVEL` | all: `docker compose up -d --build` | `docker compose logs -f chat-service`: "Chat turn", "Model round", "MCP tool" lines; `CHAT_LOG_LEVEL=DEBUG` adds full prompts |

## Running

To run a step, use the **`/run-step` skill** (`.claude/skills/run-step/`):
`/run-step step3`, `/run-step master`. It checks out the branch, creates `.env` if missing, rebuilds with
`docker compose up -d --build`, waits for health checks and runs smoke checks that don't use Gemini quota
(`--ai-check` adds one real Gemini call, `--fresh` wipes chat-db). The script can also be run directly:
`.claude/skills/run-step/scripts/run-step.sh <branch> [--fresh] [--ai-check]`.

Manual equivalent: `cp .env.example .env` once (set `GEMINI_API_KEY` for step1+), then
`git checkout <branch> && docker compose up -d --build` (always rebuild after a checkout). Stop:
`docker compose down` (keeps data); `docker compose down -v` deletes all databases.

- Chat UI: http://localhost:3001. Register an account first.
- Hard-refresh the browser after switching branches (cached JS may still show e.g. the MCP button).
- Switching branches in **any direction** keeps the databases working: auth is identical on all branches;
  chat-service has `spring.flyway.enabled=false` until step7 (step7 enables Flyway with `baseline-on-migrate`), so
  earlier steps never fail validation on a chat-db that already has step7's migration. Leftover tables are unused.
  Reset only the chat data: `docker compose rm -sf chat-db chat-service && docker volume rm chat-workshop_chatdata`.
- `.env` holds real secrets and is git-ignored; never commit it. Only `.env.example` is versioned.

## Build and test

```bash
./mvnw verify                      # root aggregator pom: auth-service, chat-service (unit tests, no Docker)
(cd chat-service && ./mvnw test)   # one service
(cd chat-ui && npm install && npm run build)   # tsc + vite
```

Local dev without Docker for the Java services: `docker compose up -d auth-db chat-db`, then
`./mvnw spring-boot:run` in each service and `npm run dev` in `chat-ui` (:5174); Vite proxies `/api/*` to local services.

## Conventions and gotchas

- Maven wrappers are pinned to **Maven 3.9.x** on purpose: IntelliJ IDEA 2025.2 cannot import Maven 3.10 projects.
- Java style: constructor injection, records for DTOs (`*Dtos` holder classes), `@Transactional(readOnly = true)`
  services, errors as RFC 9457 `ProblemDetail`. Comments explain *why*, not *what*.
- Gemini free tier is tiny (≈20 requests/day per model). Avoid unnecessary real chat calls when testing; prefer
  health/history/MCP endpoints or an invalid key (does not consume quota). Model is set via `GEMINI_MODEL`.
- The Google GenAI SDK has no timeout by default; step6+ sets one through the custom `Client` bean in `ChatConfig`.
- MCP: only public `https://` servers unless the host is in `MCP_ALLOWED_HOSTS`; stdio servers are intentionally
  not supported.

## Changing workshop steps

A change that belongs to step N must also be present in every later step and in `master`, and each step must stay a
single commit:

1. Make the change on the step N branch as a fixup, then replay the later steps on top of it (or rebuild the commit
   chain from the trees), keeping `master` == the last step (`step8-chat-logging`).
2. Verify every branch: `git diff <prev> <step>` still contains only that step's topic, `./mvnw verify` passes, and
   `docker compose up -d --build` starts (chat-service health on :8082).
3. Changes that are not workshop content (docs, infra) go into `raw` so they exist on all branches.
