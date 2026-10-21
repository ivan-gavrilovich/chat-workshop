# Spring AI Chat Workshop

> Workshop: this project is built step by step on git branches (`raw`, `step1-…` … `step8-…`), see [WORKSHOP.md](WORKSHOP.md).

An AI chat service built with Spring AI and Google Gemini, demonstrating conversation memory, streaming, MCP tool use, and structured logging.

| Part           | Stack                                                                    |
|----------------|--------------------------------------------------------------------------|
| `auth-service` | Java 25, Spring Boot 4.1, Spring Security, issues RS256 JWTs, own DB      |
| `chat-service` | Java 25, Spring Boot 4.1, Spring AI 2.0 (Google GenAI / Gemini), JWT resource server |
| databases      | PostgreSQL 18 (`auth-db` users, `chat-db` chat history) |
| `chat-ui`      | React 19, TypeScript, Vite (standalone full-page AI chat)                 |

```
browser ──► chat-ui (nginx :3001) ──► /api/auth/** ──► auth-service :8081 ──► auth-db
                                  └─► /api/chat**  ──► chat-service :8082 ──► Gemini API, chat-db

chat-service verifies JWTs with auth-service /.well-known/jwks.json
```

## AI assistant (chat-service)

> On the workshop branches the assistant is built up step by step: on `raw` chat-service only echoes the message,
> the features described below are complete from `step6-timeouts-and-errors` on (see [WORKSHOP.md](WORKSHOP.md)).

1. Get a Gemini API key at https://aistudio.google.com/apikey.
2. Put it into `.env` in the project root (template: `.env.example`):
   ```
   GEMINI_API_KEY=your-key
   GEMINI_MODEL=gemini-3.5-flash-lite
   ```
3. `docker compose up -d chat-service` (re-creates the container with the new key).

Until the key is set, the chat answers with "Gemini API key is not configured". Configuration lives in
`chat-service/src/main/resources/application.properties` (`spring.ai.google.genai.*`).

- Answers are streamed as server-sent events (`event: chunk` / `done` / `error`, data is `{"text": "..."}`).
- Each user has one conversation (keyed by the user id from the JWT). History is stored in its own PostgreSQL
  (`chat-db`, Spring AI JDBC chat memory, table `SPRING_AI_CHAT_MEMORY`), so it survives restarts; the last 30 messages
  are kept and sent to the model as context.
- Each Gemini call is limited to `chat.gemini.timeout` (90s); the Google SDK default is no timeout at all. Rate limit
  (429), overload (503) and timeout errors are shown to the user as readable messages.

| Method | Path                | Description                                                  |
|--------|---------------------|--------------------------------------------------------------|
| POST   | `/api/chat`         | `{message, timeZone}` → `text/event-stream` with the answer   |
| GET    | `/api/chat/history` | Messages of the current conversation                         |
| DELETE | `/api/chat/history` | Start a new conversation                                     |

## MCP servers

From step7, users can connect external MCP servers from the chat UI (🔌 MCP button). The tools they expose are
offered to the model alongside the conversation. Servers are stored per-user in `chat-db` with encrypted auth headers.
Only public `https://` servers are allowed unless the host is in `MCP_ALLOWED_HOSTS`.

| Method | Path                    | Description                        |
|--------|-------------------------|------------------------------------|
| GET    | `/api/mcp/servers`      | List user's configured MCP servers |
| POST   | `/api/mcp/servers`      | Add a server                       |
| PUT    | `/api/mcp/servers/{id}` | Update a server                    |
| DELETE | `/api/mcp/servers/{id}` | Remove a server                    |

## Authentication

- `auth-service` stores users (BCrypt password hashes) and signs JWTs with an RSA key pair. The key pair is generated on
  first start and stored in `auth-db`, so tokens survive restarts.
- The public key is published at `GET /.well-known/jwks.json`. `chat-service` validates tokens against it.
- Token claims: `sub` = user id, `email`, `name`, `iss` = `room-booking-auth`; lifetime is `JWT_TTL` (default 8h).
- All `/api/**` endpoints except register/login require `Authorization: Bearer <token>`.

| Method | Path                     | Auth | Description                                              |
|--------|--------------------------|------|----------------------------------------------------------|
| POST   | `/api/auth/register`     | no   | `{email, fullName, password}` → token + user              |
| POST   | `/api/auth/login`        | no   | `{email, password}` → token + user                       |
| GET    | `/api/auth/me`           | yes  | Current user                                             |
| GET    | `/.well-known/jwks.json` | no   | Public signing keys                                      |

## Run with Docker

Create `.env` from the template first: `cp .env.example .env` (and put your Gemini API key into it).

```bash
docker compose up -d --build
```

| URL                            | What                    |
|--------------------------------|-------------------------|
| http://localhost:3001          | Chat UI                 |
| http://localhost:8081/api/auth | Auth API                |
| http://localhost:8082/api/chat | Chat API                |

Register an account on the first screen of the chat UI. Health endpoints: `/actuator/health` on ports 8081 and 8082.

## Local development

```bash
docker compose up -d auth-db chat-db
cd auth-service && ./mvnw spring-boot:run            # http://localhost:8081
cd chat-service && GEMINI_API_KEY=... ./mvnw spring-boot:run   # http://localhost:8082
cd chat-ui && npm install && npm run dev             # http://localhost:5174, proxies /api/auth to :8081, /api/chat to :8082
```

Run tests for all Java services with `./mvnw verify` from the repository root (root `pom.xml` aggregates `auth-service`
and `chat-service`; open the root folder in IntelliJ IDEA to import both). The Maven wrappers are pinned to Maven 3.9.x:
IntelliJ IDEA 2025.2 cannot import projects with Maven 3.10.
