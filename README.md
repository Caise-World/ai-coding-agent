# AI Coding Agent

A Java-based AI Coding Agent with ReAct pattern for multi-step reasoning and tool calling.

## Tech Stack

- Java 17
- Spring Boot 3.2.5
- Maven
- MiniMax LLM (configurable)

## Project Structure

```
src/main/java/com/aicoding/agent/
├── AgentApplication.java        # Main entry point
├── config/
│   └── LangChainConfig.java     # LLM configuration
├── controller/
│   ├── AgentController.java     # REST API endpoint
│   └── TestController.java      # Health check
├── dto/
│   ├── ChatRequest.java         # Request DTO
│   └── ChatResponse.java        # Response DTO
├── service/
│   ├── LLMService.java          # LLM API integration
│   └── ReActAgentService.java   # ReAct agent logic
└── tool/
    ├── Tool.java                # Tool interface
    ├── ProjectScanTool.java     # Scan project structure
    ├── FileReadTool.java        # Read file content
    ├── FileWriteTool.java       # Write file content
    └── CommandExecuteTool.java  # Execute shell commands
```

## API

### POST /api/agent/chat

```json
{
  "message": "analyze project structure",
  "path": "/path/to/project"
}
```

## Tools

| Tool | Description |
|------|-------------|
| ProjectScanTool | Scan project directory structure |
| FileReadTool | Read file content |
| FileWriteTool | Write content to file |
| CommandExecuteTool | Execute shell commands (mvn test, etc.) |

## Quick Start

```bash
# Set API key
export MINIMAX_API_KEY=your-key

# Run
mvn spring-boot:run
```

## ReAct Pattern

The agent uses a multi-step reasoning loop:

1. **Thought** - Analyze the question
2. **Action** - Call a tool if needed
3. **Observation** - Get tool result
4. Repeat until final answer

Max iterations: 8
