#!/usr/bin/env bash
# Checks out a workshop branch, (re)starts the Docker stack and verifies it.
#
#   run-step.sh <branch> [--fresh] [--ai-check] [--no-checkout]
#
#   <branch>       raw | step1 … step8 | master | full branch name (e.g. step3-streaming)
#   --fresh        delete chat-db data first (chat history, MCP servers); accounts stay
#   --ai-check     also send one real message to Gemini (uses quota of the free tier)
#   --no-checkout  run whatever is checked out now
#
# Smoke checks use a temporary account that is deleted at the end. Without --ai-check no Gemini quota is used.
set -uo pipefail

die() { echo "ERROR: $*" >&2; exit 1; }
info() { echo "==> $*"; }

BRANCH="" FRESH=0 AI_CHECK=0 CHECKOUT=1
for arg in "$@"; do
  case "$arg" in
    --fresh) FRESH=1 ;;
    --ai-check) AI_CHECK=1 ;;
    --no-checkout) CHECKOUT=0 ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    -*) die "unknown option $arg" ;;
    *) BRANCH="$arg" ;;
  esac
done

ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || die "not inside the workshop git repository"
cd "$ROOT" || exit 1

# --- resolve and check out the branch ------------------------------------------------------------
if [ "$CHECKOUT" = 1 ]; then
  [ -n "$BRANCH" ] || die "branch is required (raw, step1 … step8, master)"
  if ! git show-ref --verify --quiet "refs/heads/$BRANCH"; then
    matches=$(git for-each-ref --format='%(refname:short)' "refs/heads/${BRANCH}-*")
    [ "$(printf '%s\n' "$matches" | grep -c .)" = 1 ] || die "unknown branch '$BRANCH'. Available: $(git for-each-ref --format='%(refname:short)' refs/heads | tr '\n' ' ')"
    BRANCH=$matches
  fi
  if [ "$(git rev-parse --abbrev-ref HEAD)" != "$BRANCH" ]; then
    [ -z "$(git status --porcelain)" ] || die "uncommitted changes in the working tree; commit or stash them before switching to $BRANCH:
$(git status --short)"
    info "Checking out $BRANCH"
    git checkout -q "$BRANCH" || die "git checkout $BRANCH failed"
  fi
fi
BRANCH=$(git rev-parse --abbrev-ref HEAD)

# --- .env ------------------------------------------------------------------------------------------
if [ ! -f .env ]; then
  cp .env.example .env
  info "Created .env from .env.example (set GEMINI_API_KEY in it for step1 and later)"
fi
env_value() { grep -E "^$1=" .env | tail -1 | cut -d= -f2-; }

# What this branch contains (detected from the code, so it also works for master)
HAS_AI=1; grep -q '^spring.ai.model.chat=none' chat-service/src/main/resources/application.properties && HAS_AI=0
HAS_MCP=0; [ -d chat-service/src/main/java/com/leverx/chat/mcp ] && HAS_MCP=1
KEY=$(env_value GEMINI_API_KEY)
KEY_SET=1; { [ -z "$KEY" ] || [ "$KEY" = "PUT_YOUR_GEMINI_API_KEY_HERE" ]; } && KEY_SET=0

# --- start the stack -------------------------------------------------------------------------------
PROJECT=${COMPOSE_PROJECT_NAME:-$(basename "$ROOT" | tr '[:upper:]' '[:lower:]')}
COMPOSE=(docker compose)

if [ "$FRESH" = 1 ]; then
  info "Deleting chat-db data (chat history, MCP servers)"
  "${COMPOSE[@]}" rm -sf chat-service chat-db >/dev/null 2>&1
  docker volume ls -q --filter "label=com.docker.compose.project=$PROJECT" --filter "label=com.docker.compose.volume=chatdata" \
    | xargs -r docker volume rm >/dev/null
fi

info "Building and starting on $BRANCH (first build can take a few minutes)"
LOG=$(mktemp)
if ! "${COMPOSE[@]}" up -d --build >"$LOG" 2>&1; then
  tail -30 "$LOG" >&2
  die "docker compose up failed"
fi
rm -f "$LOG"

# --- wait until everything is healthy --------------------------------------------------------------
ENDPOINTS=("auth-service|http://localhost:8081/actuator/health" "chat-service|http://localhost:8082/actuator/health" "chat-ui|http://localhost:3001/")
info "Waiting for services"
deadline=$(( $(date +%s) + 300 ))
for entry in "${ENDPOINTS[@]}"; do
  name=${entry%%|*} url=${entry#*|}
  until curl -sf -o /dev/null "$url"; do
    if [ "$(date +%s)" -ge "$deadline" ]; then
      "${COMPOSE[@]}" ps
      echo "--- last log lines of $name:" >&2
      "${COMPOSE[@]}" logs --tail 40 "$name" >&2
      die "$name did not become ready ($url)"
    fi
    sleep 3
  done
done

# --- smoke checks ----------------------------------------------------------------------------------
API=http://localhost:3001/api
JSON='Content-Type: application/json'
FAILED=0
pass() { echo "  ✓ $*"; }
fail() { echo "  ✗ $*"; FAILED=1; }
status() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

EMAIL="run-step-$(date +%s)@check.local"
TOKEN=$(curl -s -XPOST "$API/auth/register" -H "$JSON" \
  -d "{\"email\":\"$EMAIL\",\"fullName\":\"Run Step Check\",\"password\":\"run-step-check\"}" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("accessToken",""))' 2>/dev/null)
AUTH="Authorization: Bearer $TOKEN"
cleanup() {
  [ -n "${TOKEN:-}" ] && curl -s -o /dev/null -XDELETE "$API/chat/history" -H "$AUTH"
  "${COMPOSE[@]}" exec -T auth-db psql -U auth -d auth -qc "DELETE FROM app_user WHERE email='$EMAIL';" >/dev/null 2>&1
}
trap cleanup EXIT

info "Checks"
[ -n "$TOKEN" ] && pass "register/login via auth-service" || fail "registration failed"
[ "$(status "$API/chat/history" -H "$AUTH")" = 200 ] && pass "chat history endpoint" || fail "GET /api/chat/history"

chat() { curl -sN --max-time 150 -XPOST "$API/chat" -H "$JSON" -H "$AUTH" -d "{\"message\":\"$1\",\"timeZone\":\"UTC\"}"; }
answer_text() { python3 -c '
import json,sys
ev=None; out=[]
for line in sys.stdin.read().split("\n"):
    if line.startswith("event:"): ev=line[6:]
    elif line.startswith("data:"):
        t=json.loads(line[5:])["text"]; out.append(("[error] " if ev=="error" else "")+t)
    elif line.startswith("{"): out.append("[http] "+json.loads(line).get("detail",""))
print(" ".join("".join(out).split())[:200])'; }

if [ "$HAS_AI" = 0 ]; then
  reply=$(chat "hello" | answer_text)
  [[ "$reply" == "AI is not connected yet"* ]] && pass "chat echo (no AI on this step): $reply" || fail "unexpected chat reply: $reply"
elif [ "$KEY_SET" = 0 ]; then
  fail "GEMINI_API_KEY is not set in .env - the chat will answer 'Gemini API key is not configured'"
elif [ "$AI_CHECK" = 1 ]; then
  start=$(date +%s); reply=$(chat "Reply with exactly: OK" | answer_text)
  if [ -n "$reply" ] && [[ "$reply" != "[error]"* ]] && [[ "$reply" != "[http]"* ]]; then
    pass "Gemini answered in $(( $(date +%s) - start ))s: $reply"
  else
    fail "Gemini call failed: $reply"
  fi
else
  pass "Gemini key is set (real chat call skipped to save quota; use --ai-check)"
fi

if [ "$HAS_MCP" = 1 ]; then
  [ "$(status "$API/mcp/servers" -H "$AUTH")" = 200 ] && pass "MCP servers API" || fail "GET /api/mcp/servers"
fi

# --- report ----------------------------------------------------------------------------------------
echo
info "Branch $BRANCH ($(git rev-parse --short HEAD))"
"${COMPOSE[@]}" ps --format '{{.Service}}: {{.Status}}' | sed 's/^/  /'
echo
echo "  Chat UI: http://localhost:3001"
echo "  Hard-refresh the browser (Cmd+Shift+R) after switching branches."
exit $FAILED
