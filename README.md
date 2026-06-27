# AI Coding Agent

An AI coding assistant with a streaming ReAct agent, a self-healing tool-call loop, and a Vue 3 chat UI. Backed by an OpenAI-compatible LLM (default: MiniMax M2.7) and capable of reading, writing, scanning, and executing commands inside a Docker sandbox.

## Tech Stack

- Java 17, Spring Boot 3.2.5, Maven
- Spring WebFlux (Project Reactor) for SSE streaming
- MiniMax M2.7 LLM via OpenAI-compatible chat completions API
- Vue 3 + Vite (port 5173, dev proxy to backend)
- Docker sandbox for shell command execution
- JUnit 5 + Spring Boot Test for eval suite

## Quick Start (Docker — recommended)

```bash
# 1. Configure environment
cp .env.example .env
# edit .env, set MINIMAX_API_KEY

# 2. Build backend JAR (one-time, or after backend code changes)
mvn package -DskipTests

# 3. Start everything
docker compose up -d

# Frontend: http://localhost
# Backend:  http://localhost:8082
```

## Quick Start (Dev mode — no Docker)

Two terminals:

```bash
# Terminal 1: backend
export MINIMAX_API_KEY=sk-...
mvn spring-boot:run
# → http://localhost:8082

# Terminal 2: frontend
cd frontend
npm install
npm run dev
# → http://localhost:5173
```

## Architecture

**Primary request flow** (`POST /api/agent/stream`, used by the frontend):

```
User input → StreamingAgentController
  → Sinks.Many<AgentEvent> + CompletableFuture.runAsync (non-blocking)
  → Phase 0  Memory retrieval (MemoryService: short-term + long-term)
  → Phase 1  Tool selection via LLM (ToolSelector) — returns NONE for chat
  → Phase 2  Tool execution with self-healing retry loop (max 3 attempts)
              on failure: reflection → repair → extract-input → retry
  → Phase 3  Final answer (LLM summarizes tool result, or direct chat answer)
              — when ToolSelector flagged needsRag, retrieved chunks are injected here
  → Flux<AgentEvent> serialized as text/event-stream
```

The `POST /api/agent/chat` blocking endpoint and the V4/V5/V6 services are preserved for reference but not used by the active UI.

## API

### `POST /api/agent/stream` (SSE)

Request:

```json
{ "message": "执行 echo hello", "path": "/workspace" }
```

Response: `text/event-stream` with one `data: <json>` line per `AgentEvent`. The stream ends when the agent emits a `FINAL` or `ERROR` event.

Each `AgentEvent` has the shape:

```json
{
  "type":      "TOOL_CALL",
  "toolName":  "CommandExecuteTool",
  "input":     "echo hello",
  "content":   null,
  "timestamp": "2026-06-23T15:24:31.099Z"
}
```

Event types emitted by the active pipeline:

| Type | Meaning |
|------|---------|
| `THINKING` | Internal reasoning step (tool list, selection rationale) |
| `PLANNING` | Beginning of a phase |
| `TOOL_CALL` | Tool invocation: `toolName` + `input` |
| `TOOL_RESULT` | Tool output: `toolName` + truncated `content` |
| `MEMORY_READ` | Loaded short-term or long-term context |
| `MEMORY_WRITE` | Saved to memory |
| `RAG_READ` | Retrieved codebase chunks injected into the final-answer prompt |
| `REFLECTION` | Self-healing: root-cause analysis of failure |
| `REPAIR` | Self-healing: proposed fix |
| `RETRY` | Re-executing tool with repaired input |
| `MAX_RETRIES_EXCEEDED` | Gave up after 3 attempts |
| `FINAL` | Final answer (stream end) |
| `ERROR` | Pipeline error (stream end) |

SSE format note: Spring WebFlux emits `data:` (no space). The frontend parser handles both `data:` and `data: ` (with space) for robustness.

### `POST /api/agent/chat` (blocking, legacy)

Returns a single JSON response with the full session trace. Used by the V6 engineering agent and any external automation.

## Tools

All tools implement the `Tool` interface (`name`, `description`, `execute(ToolContext)`) and are registered automatically by `ToolRegistrar` via Spring's `ApplicationContext.getBeansOfType(Tool.class)` at startup. **Adding a new tool is a one-step process: write the class with `@Component` and implement `Tool` — no other code needs to change.**

| Tool | Purpose | Limits |
|------|---------|--------|
| `ProjectScanTool` | Recursively scan a directory tree | max depth 4, 50 files |
| `FileReadTool` | Read a single file | max 10K chars |
| `FileWriteTool` | Create/overwrite a file | JSON or pipe-delimited input |
| `CommandExecuteTool` | Run a shell command | Docker sandbox, 10s timeout, 50K output cap |
| `GrepTool` | Search files for a regex pattern | 30 results max, common file types only |
| `NONE` (selection) | Chat / general question | LLM direct answer |

### Tool selection (LLM-driven)

`ToolSelector` sends a few-shot prompt listing all tools to the LLM and parses the JSON response. The active prompt includes 12 worked examples covering each tool plus `NONE` for chat. Run `mvn test -Dtest=ToolSelectionEvalTest` to verify pass rate on the regression dataset.

## Memory

`MemoryService` maintains two layers:

- **Short-term** — per-session `ConcurrentHashMap` persisted to `memory/short-term/{sessionId}.json`. The current session is also exposed as `MEMORY_READ` / `MEMORY_WRITE` events on the SSE stream.
- **Long-term** — single file `memory/long-term/memory.json`. Stores experiences (`toolName: context → solution`) and failures (`FAILURE: task → reason | fix`).

Two retrieval methods back the agent:

- `getMemoryContext()` — last 5 long-term entries, each truncated to 100 chars. Returned in Phase 0 of every request.
- `getRepairContext()` — last 3 failure entries, used by the self-healing loop.

Long-term memory has no expiry and grows unbounded. Reset by deleting `memory/`.

## Codebase RAG

The agent can answer questions grounded in the contents of a specific project (e.g. "How does `PluginBasedStreamingAgentService` implement self-healing?"). It does this by indexing the project into a small in-memory vector store and retrieving the most relevant code chunks before the final-answer LLM call.

### Flow

```
1. User opens a workspace  → POST /api/workspace/open { path: "/path/to/project" }
                             → IndexingService walks files, chunks them, embeds via Ollama
                             → chunks + vectors cached at .rag-cache/<sha1(path)>/

2. User asks a question    → ToolSelector returns NONE with needsRag=true
                             → RagService embeds the question, runs cosine top-K over the index
                             → top chunks (path:startLine-endLine + content) injected into prompt
                             → FINAL answer is grounded in the retrieved code
```

RAG is **conditional** — `ToolSelector` only sets `needsRag=true` when the question is about code structure/behavior in a project (e.g. "X 是怎么实现的", "Y 有什么方法"). Greetings, general-knowledge questions, and tool-calling queries skip RAG entirely.

### Components

| Package | Purpose |
|---------|---------|
| `rag.chunk` | `ChunkParser` SPI + per-language parsers (Java via JavaParser, XML, YAML, Markdown, Properties). Each parser produces `Chunk(path, startLine, endLine, kind, symbol, content)` records. |
| `rag.embedding` | `OllamaEmbedder` — `POST /api/embeddings` to local Ollama, default model `nomic-embed-text` (768-dim). |
| `rag.store` | `VectorStore` interface + `InMemoryVectorStore` (cosine similarity, `CopyOnWriteArrayList`, binary save/load for embeddings). |
| `rag.indexing` | `IndexingService` — walks a workspace (max depth 6, 500 files, skips hidden dirs), routes to the right `ChunkParser` by extension, embeds and adds to the store. |
| `rag.workspace` | `WorkspaceService` — manages the "currently open" workspace + cache invalidation by SHA-1 of the path. Cache hit on second open for the same project. |
| `rag.RagService` | top-level entry `retrieveContext(question)` → embed question → top-K → format `[N] path:startLine-endLine (score=...) \n content`. |

### Chunking

Java files are parsed by **JavaParser** with `JAVA_17` language level. Each class becomes one chunk; each method becomes one chunk (carrying the method signature as the `symbol` field). This gives the retriever method-level granularity while preserving path + line numbers for citation.

Other formats get reasonable defaults: YAML and properties as whole files (or 4K blocks), XML by top-level element, Markdown by `#`/`##`/`###` headers.

### Open Workspace API

```http
POST /api/workspace/open
Content-Type: application/json

{ "path": "/Users/me/code/myproject" }
```

Response:

```json
{
  "path": "/Users/me/code/myproject",
  "chunksCreated": 312,
  "indexTimeMs": 1820,
  "cacheHit": false
}
```

`GET /api/workspace/current` returns the current workspace state (or an error if none open).

### Configuration

```yaml
ollama:
  host: ${OLLAMA_HOST:http://localhost:11434}
  embed-model: ${OLLAMA_EMBED_MODEL:nomic-embed-text}
  embed-dimension: 768

rag:
  enabled: true
  cache-dir: ${RAG_CACHE_DIR:./.rag-cache}
  top-k: 5
  similarity-threshold: 0.3
```

In Docker Compose, the `ollama` service runs alongside the backend. First-time setup:

```bash
docker compose up -d ollama
docker exec -it agent-ollama ollama pull nomic-embed-text   # 274 MB
docker compose up -d
```

The backend's `OLLAMA_HOST` points to `http://ollama:11434` automatically. RAG cache persists in the `agent-workspace` volume.

### Evaluation

RAG-specific regression cases are part of `eval-dataset.json` (e.g. `PluginBasedStreamingAgentService 是怎么实现自愈` → expected `NONE` with `needsRag=true`). Run `mvn test -Dtest=ToolSelectionEvalTest` to verify the routing decision; unit tests for `OllamaEmbedder`, `ChunkParser`, and end-to-end `RagEndToEndTest` cover the retrieval path.

### Limitations (MVP)

- **Full rebuild on first open** — no incremental indexing. Cache keyed by path hash, so a re-open of the same path is fast.
- **No code awareness during embedding** — code is embedded as raw text. A "code-aware" embedder (or splitting identifiers) would help Java retrieval.
- **No hybrid search** — pure cosine similarity. BM25 or reranking is a future improvement.

## Self-Healing

When a tool call fails, `PluginBasedStreamingAgentService.executeWithSelfHealing` runs up to 3 attempts, each driven by an LLM call:

1. **Reflection** — analyze the root cause (`REFLECTION` event)
2. **Repair** — propose a fix strategy (`REPAIR` event)
3. **Extract** — parse the new input from the repair plan (LLM call)
4. **Retry** — re-execute the tool with the repaired input (`RETRY` event)
5. After 3 failures, emit `MAX_RETRIES_EXCEEDED` and fall back to a direct LLM answer

Failures are appended to long-term memory, so future sessions see them in `getRepairContext()`.

## Sandbox

`DockerSandboxExecutor` runs shell commands in ephemeral Docker containers:

- Resources: 256MB memory, 0.5 CPU, `--network=none`, user 1000:1000
- Blacklisted: `rm -rf /`, fork bombs, `shutdown`, mount binds, and other dangerous patterns
- Workspace: `/tmp/agent-workspace` (host) → `/workspace` (container)
- Timeouts: 10s execution, 50K output cap

## Frontend

`frontend/` is a Vue 3 SPA built with Vite.

- `Chat.vue` — main view: session sidebar + workspace opener + SSE event timeline + input box
- `StreamViewer.vue` — timeline renderer, dispatches to `ToolCallPanel` / `RagContextPanel` / `ChatMessage` by event type
- `WorkspaceOpener.vue` — open a project path for RAG indexing; shows chunk count + cache hit
- `RagContextPanel.vue` — displays retrieved chunks with `path:line` citations
- `agent.js` — SSE client: `fetch` + `ReadableStream`, splits on `data:` lines, parses JSON
- `workspace.js` — REST client for `POST /api/workspace/open` and `GET /api/workspace/current`
- Vite dev server proxies `/api` → `http://localhost:8082`
- In production, the same proxy is handled by `frontend/nginx.conf` (see Docker setup)

## Eval System

Two JUnit 5 tests under `src/test/java/com/aicoding/agent/eval/`:

| Test | What it checks | Run command |
|------|----------------|-------------|
| `ToolSelectionEvalTest` | `ToolSelector` regression against `eval-dataset.json` (24 cases, includes RAG routing). Reports pass rate and lists failures. | `mvn test -Dtest=ToolSelectionEvalTest#runEval` |
| `AgentIntegrationEvalTest` | End-to-end pipeline: `Memory → ToolSelect → Execute → FinalAnswer` for each tool type. Verifies expected `TOOL_CALL` events and absence of `ERROR`. | `mvn test -Dtest=AgentIntegrationEvalTest` |

Plus RAG-specific tests under `src/test/java/com/aicoding/agent/rag/`:

| Test | What it checks | Run command |
|------|----------------|-------------|
| `OllamaEmbedderTest` | Embedding round-trip + dimension check | `mvn test -Dtest=OllamaEmbedderTest` |
| `ChunkParserTest` | Java/Xml/Yaml/Markdown/Properties parser output | `mvn test -Dtest=ChunkParserTest` |
| `RagEndToEndTest` | Open workspace → index → ask question → retrieve → assert hits | `mvn test -Dtest=RagEndToEndTest` |

Both tests use `@SpringBootTest(webEnvironment = NONE)` — no web server, real Spring context, real LLM calls. The `eval-dataset.json` lives at `src/test/resources/`.

After changing `ToolSelector.java` or the prompt, run `ToolSelectionEvalTest` to confirm no regression. After changing the agent service, tools, or LLM service, run `AgentIntegrationEvalTest`.

## Project Structure

```
.
├── pom.xml
├── Dockerfile                      # backend (eclipse-temurin:17-jre)
├── docker-compose.yml              # backend + frontend
├── .dockerignore
├── .env.example
├── src/main/java/com/aicoding/agent/
│   ├── AgentApplication.java
│   ├── config/                     # LangChainConfig, CorsConfig
│   ├── controller/                 # StreamingAgentController (active), AgentController (legacy)
│   ├── dto/                        # AgentEvent, ChatRequest, ChatResponse
│   ├── memory/MemoryService.java   # short-term + long-term
│   ├── model/                      # Task, CommandRequest, ExecutionResult
│   ├── rag/                        # Codebase RAG MVP
│   │   ├── RagService.java         #   top-level entry: question → embed → topK → format
│   │   ├── chunk/                  #   Chunk, ChunkParser SPI, Java/Xml/Yaml/Markdown/Properties parsers
│   │   ├── embedding/              #   OllamaEmbedder
│   │   ├── store/                  #   VectorStore, InMemoryVectorStore
│   │   ├── indexing/               #   IndexingService
│   │   └── workspace/              #   WorkspaceService
│   ├── registry/                   # Tool, ToolRegistry, ToolRegistrar, ToolSelector, ToolExecutor
│   ├── sandbox/                    # SandboxExecutor, DockerSandboxExecutor
│   ├── service/                    # PluginBasedStreamingAgentService (active), LLMService,
│   │                                #   FailureAnalysisService, GoalEnhancerService,
│   │                                #   TaskPlannerService, TaskVerifierService, TraceService,
│   │                                #   V4/V5/V6 agent services (legacy)
│   └── tool/                       # ProjectScanTool, FileReadTool, FileWriteTool, CommandExecuteTool
├── src/main/resources/application.yml
├── src/test/java/com/aicoding/agent/eval/
│   ├── EvalCase.java
│   ├── ToolSelectionEvalTest.java
│   └── AgentIntegrationEvalTest.java
├── src/test/resources/eval-dataset.json
└── frontend/
    ├── Dockerfile                  # node:20-alpine build + nginx:1.27-alpine serve
    ├── nginx.conf                  # SPA fallback + /api reverse proxy
    ├── package.json
    ├── vite.config.js
    └── src/
        ├── App.vue
        ├── api/                    # agent.js (SSE), workspace.js (REST)
        ├── views/Chat.vue
        └── components/             # StreamViewer, ToolCallPanel, ChatMessage,
                                    # WorkspaceOpener, RagContextPanel, InputBox
```

## Configuration

`application.yml` reads from environment variables (with defaults shown):

| Variable | Default | Purpose |
|----------|---------|---------|
| `MINIMAX_API_KEY` | `your-api-key-here` | LLM API key |
| `MINIMAX_BASE_URL` | `https://api.minimax.chat/v1` | OpenAI-compatible base URL |
| `MINIMAX_MODEL` | `minimax-m2.7` | Model name |
| `AGENT_PROJECT_PATH` | `$USER_HOME` or `/Users/wanshun` | Default project root for tools |
| `OLLAMA_HOST` | `http://localhost:11434` | Ollama server URL (RAG embeddings) |
| `OLLAMA_EMBED_MODEL` | `nomic-embed-text` | Ollama embedding model |
| `RAG_CACHE_DIR` | `./.rag-cache` | Where to store RAG index cache |
| Server port | `8082` | `server.port` |

In Docker, `AGENT_PROJECT_PATH` is set to `/workspace` and a named volume (`agent-workspace`) persists it across container restarts.

## Notes

- `MiniMax M2.7` sometimes outputs `<think>...</think>` tags in responses. The active service strips these in `generateDirectAnswer` and final-answer rendering.
- `GoalEnhancerService`, `TaskPlannerService`, `TaskVerifierService`, and `FailureAnalysisService` are wired into the V6 / streaming pipeline but mostly as scaffolding around the core `ToolSelector` + `ToolExecutor` path. The minimal hot path is the three steps in **Architecture** above.
- The Vue dev server proxies `/api` to the backend; the same proxy is handled by `nginx.conf` in production. This keeps the frontend free of hard-coded backend URLs.
