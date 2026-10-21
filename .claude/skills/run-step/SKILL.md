---
name: run-step
description: Run the chat workshop at a given git branch (raw, step1 … step8, master) with Docker Compose and verify it works. Use when asked to run, start, switch to or check a workshop step or the app.
argument-hint: "<raw|step1…step8|master> [--fresh] [--ai-check]"
---

# Run a workshop step

The workshop is a chain of branches (`raw` → `step1-…` → … → `step8-…` = `master`, see `WORKSHOP.md`).
Every branch runs with the same Docker Compose setup; this skill switches the branch, rebuilds and verifies it.

## Steps

1. Resolve what the user asked for:
   - the branch: `raw`, `step1` … `step8` (short names work), `master`, or a full branch name;
     if none is given, use the currently checked-out branch with `--no-checkout`;
   - `--fresh` only if they explicitly want to clear chat history / MCP servers (it deletes chat-db data);
   - `--ai-check` only if they ask to test the AI answer. It sends one real request to Gemini, whose free tier
     allows only about 20 requests per day per model, so don't add it by default.
2. Run the script from the repository root:
   ```bash
   .claude/skills/run-step/scripts/run-step.sh <branch> [--fresh] [--ai-check]
   ```
   It checks out the branch, creates `.env` from `.env.example` if missing, runs `docker compose up -d --build`,
   waits for the health checks and runs smoke checks with a temporary account that it deletes afterwards.
   The first build of all images takes several minutes; use a long timeout (10+ minutes).
3. If the script stops because of **uncommitted changes**, do not stash or discard them yourself: show them to the
   user and ask what to do.
4. Report to the user: branch, whether all checks passed, the URL, and what to try on this step in the UI
   (table below). If a check failed, show the relevant output and the likely cause from the table at the end.

## What to try on each step

| Branch | Try in the UI |
|---|---|
| `raw` | Register an account; the chat answers "AI is not connected yet. You said: …" |
| `step1-configure-springai-props` | Chat answers with Gemini, the whole answer at once |
| `step2-system-prompt` | "Who am I and what day is it today?" |
| `step3-streaming` | A long answer ("Write an agenda for a sprint planning") appears word by word |
| `step4-chat-memory` | Follow-up questions work; history is lost after `docker compose restart chat-service` |
| `step5-jdbc-chat-memory` | History survives `docker compose restart chat-service` |
| `step6-timeouts-and-errors` | Errors (invalid key, quota) show as readable messages |
| `step7-mcp-servers-from-ui` | 🔌 MCP → add `https://mcp.deepwiki.com/mcp` (name `deepwiki`) → ask about a GitHub repository |
| `step8-chat-logging`, `master` | As step7, while watching `docker compose logs -f chat-service`: "Chat turn", "Model round" and "MCP tool" lines |

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| "GEMINI_API_KEY is not set" | Put a key from https://aistudio.google.com/apikey into `.env`, rerun with `--no-checkout` |
| Chat says "rate limit or quota exceeded" (step6+) or hangs / errors (step1-5) | Gemini free-tier quota used up: change `GEMINI_MODEL` in `.env` and rerun |
| A service does not become ready | The script prints its last log lines; port already in use → stop the other process or `docker compose down` |
| UI still shows the previous step (e.g. MCP button) | Browser cache: hard-refresh (Cmd+Shift+R) |
| Want everything from scratch | `docker compose down -v` (deletes all databases, accounts included), then run the step again |

Never commit `.env`: it contains real API keys.
