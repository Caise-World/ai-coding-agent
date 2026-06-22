# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Tech Stack
- Spring Boot 3.2.5, Java 17, Maven (Homebrew-installed `mvn`, no `mvnw`)
- Project Reactor (WebFlux) for SSE streaming
- MiniMax M2.7 LLM via OpenAI-compatible chat completions API
- Vue 3 + Vite frontend (port 5173, proxies `/api` to backend)

## Build & Run

```bash
# Backend (port 8082)
export MINIMAX_API_KEY=sk-...
mvn compile -DskipTests
mvn spring-boot:run

# Frontend (port 5173)
cd frontend && npm run dev
```

## Architecture

**Primary request flow** (`POST /api/agent/stream`, used by frontend):

```
User input → StreamingAgentController → PluginBasedStreamingAgentService
  → Sinks.Many<AgentEvent> + CompletableFuture.runAsync (non-blocking)
  → Phase 0: Memory retrieval (MemoryService)
  → Phase 1: Tool selection via LLM (ToolSelector) — returns NONE for chat
  → Phase 2: Tool execution with self-healing retry loop (max 3 attempts)
      → On failure: reflection → repair → extract-input → retry (all LLM-driven)
  → Phase 3: Final answer → Flux<AgentEvent> serialized as text/event-stream
```

There is also a legacy blocking endpoint `POST /api/agent/chat` served by `AgentController` → `V6EngineeringAgentService`.

**CRITICAL**: The `@PostMapping("/stream")` MUST have `produces = MediaType.TEXT_EVENT_STREAM_VALUE`. Without it, WebFlux serializes the Flux as `application/json` (a JSON array) instead of SSE `data:` lines, and the frontend silently drops all events.

## Key Components

### Tool Registry System
- **`Tool`** — Interface: `name()`, `description()`, `execute(ToolContext)`
- **`ToolRegistry`** — Key-value store for tools
- **`ToolRegistrar`** — Registers all tools via `@PostConstruct`
- **`ToolSelector`** — LLM-driven: sends tool list + user message to LLM, parses JSON response to pick tool (or `NONE` for chat)
- **`ToolExecutor`** — Looks up and executes tool by name

### Tools (all in `tool/`)
- `ProjectScanTool` — Recursively scans directory structure (max depth 4, 50 files)
- `FileReadTool` — Reads file content (max 10K chars)
- `FileWriteTool` — Writes files, supports JSON or pipe-delimited input
- `CommandExecuteTool` — Executes shell commands in Docker sandbox

### Memory System (`memory/MemoryService`)
- **Short-term**: Per-session `ConcurrentHashMap`, persisted to `memory/short-term/{sessionId}.json`
- **Long-term**: Single file `memory/long-term/memory.json`. Stores experiences (`toolName: context → solution`) and failures (`FAILURE: task → reason | fix`).
- `getMemoryContext()` returns last 5 experiences (truncated to 100 chars each)
- `getRepairContext()` returns last 3 failure entries for self-healing context

### Sandbox (`sandbox/DockerSandboxExecutor`)
- Runs commands in Docker containers with strict limits: 256MB memory, 0.5 CPU, `--network=none`, user 1000:1000
- Blacklists dangerous commands (rm -rf /, fork bombs, shutdown, mount binds, etc.)
- Mounts `/tmp/agent-workspace` as `/workspace`, 10s timeout, 50K output limit

### Frontend (`frontend/`)
- **`Chat.vue`** — Main view: session sidebar + SSE event timeline + input box
- **`StreamViewer.vue`** — Timeline rendering: `ToolCallPanel` for TOOL_CALL, `ChatMessage` for everything else
- **`agent.js`** — SSE client: fetch + ReadableStream, splits on `data:` lines, parses JSON
- The SSE parser handles both `data:` (no space, Spring default) and `data: ` (with space)
- Vite proxies `/api` to `localhost:8082`

### SSE Event Types (AgentEvent)
`THINKING`, `PLANNING`, `TOOL_CALL`, `TOOL_RESULT`, `MEMORY_READ`, `MEMORY_WRITE`, `VERIFICATION`, `REFLECTION`, `REPAIR`, `RETRY`, `MAX_RETRIES_EXCEEDED`, `FINAL`, `ERROR`, `FAILURE_ANALYSIS`, `RECOVERY`

### Self-Healing Flow (PluginBasedStreamingAgentService)
On tool execution failure (up to 3 retries):
1. **Reflection** — LLM call to analyze root cause
2. **Repair** — LLM call to generate fix strategy / alternative input
3. **Extract** — LLM call to parse just the new input from the repair plan
4. **Retry** — Re-execute tool with repaired input
5. Failures saved to MemoryService for future repair context

## Configuration (`application.yml`)
- Server port: 8082
- LLM: `MINIMAX_API_KEY` env var, model `minimax-m2.7`, base URL `https://api.minimax.chat/v1`
- Default project path: `USER_HOME` env var

## Notes
- The old services (V4, V5, V6, StreamingAgentService, ReActAgentService) are preserved but not used by the active endpoints. The active streaming flow uses only `PluginBasedStreamingAgentService`.
- Long-term memory has no expiry — it grows unbounded.
- MiniMax M2.7 sometimes outputs `\<think\>...\</think\>` tags in its responses; `generateDirectAnswer()` strips these.
- `GoalEnhancerService` exists but is unused.
