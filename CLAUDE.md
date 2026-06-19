# AI Coding Agent

A minimal AI Coding Agent with Tool Calling capability.

## Tech Stack
- Spring Boot 3.2.5
- LangChain4j 1.0.0
- Java 17

## API

### POST /api/agent/chat

```json
{
  "message": "帮我分析这个Spring Boot项目结构",
  "path": "/Users/wanshun/Documents/Code/ai-coding-agent"
}
```

Response:
```json
{
  "answer": "分析结果...",
  "toolUsed": true,
  "toolName": "ProjectScanTool"
}
```

## Build & Run

```bash
cd ai-coding-agent
# Set your OpenAI API key
export OPENAI_API_KEY=sk-...

# Build
./mvnw clean package -DskipTests

# Run
./mvnw spring-boot:run
```

## Tools

1. **ProjectScanTool** - Scans project directory structure
2. **FileReadTool** - Reads file content

## Architecture

Single-round execution: User input → LLM decides tool → Execute tool → Generate final answer
