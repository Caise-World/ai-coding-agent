package com.aicoding.agent.eval;

import com.aicoding.agent.dto.AgentEvent;
import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.memory.MemoryService;
import com.aicoding.agent.registry.ToolExecutor;
import com.aicoding.agent.registry.ToolRegistry;
import com.aicoding.agent.registry.ToolSelector;
import com.aicoding.agent.service.LLMService;
import com.aicoding.agent.service.PluginBasedStreamingAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end Agent integration tests.
 * Runs the full PluginBasedStreamingAgentService pipeline:
 * Memory → ToolSelection → ToolExecution → FinalAnswer
 *
 * Run via: mvn test -Dtest=AgentIntegrationEvalTest
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AgentIntegrationEvalTest {

    @Autowired private ToolRegistry toolRegistry;
    @Autowired private ToolSelector toolSelector;
    @Autowired private ToolExecutor toolExecutor;
    @Autowired private MemoryService memoryService;
    @Autowired private LLMService llmService;

    private PluginBasedStreamingAgentService agent;

    @BeforeEach
    void setUp() {
        agent = new PluginBasedStreamingAgentService(
                toolRegistry, toolSelector, toolExecutor,
                memoryService, llmService, "/tmp/test-project");
    }

    @Test
    void chatMessageShouldReturnDirectAnswer() {
        ChatRequest req = new ChatRequest();
        req.setMessage("你好，请介绍一下你自己");

        List<AgentEvent> events = collectEvents(agent.executeStream(req));

        assertHasEvent(events, "FINAL", "Should have a final answer");
        assertNoEvent(events, "ERROR", "Should have no errors");
        assertHasEvent(events, "THINKING", "Should show thinking process");

        AgentEvent last = events.get(events.size() - 2); // before MEMORY_WRITE
        assertNotNull(last.getContent(), "Final answer should have content");
        assertFalse(last.getContent().isBlank(), "Final answer should not be empty");

        System.out.printf("  [PASS] chat  | events=%d | final=\"%s...\"%n",
                events.size(), last.getContent().substring(0, Math.min(80, last.getContent().length())));
    }

    @Test
    void fileReadRequestShouldCallFileReadTool() {
        ChatRequest req = new ChatRequest();
        req.setMessage("读取 pom.xml 文件的内容");

        List<AgentEvent> events = collectEvents(agent.executeStream(req));

        assertHasEvent(events, "FINAL", "Should have a final answer");
        assertNoEvent(events, "ERROR", "Should have no errors");
        assertHasEvent(events, "TOOL_CALL", "Should have a tool call");
        assertEventWithTool(events, "TOOL_CALL", "FileReadTool", "Should call FileReadTool");

        System.out.printf("  [PASS] read  | events=%d | tool=FileReadTool%n", events.size());
    }

    @Test
    void projectScanRequestShouldCallProjectScanTool() {
        ChatRequest req = new ChatRequest();
        req.setMessage("帮我分析这个项目的目录结构");

        List<AgentEvent> events = collectEvents(agent.executeStream(req));

        assertHasEvent(events, "FINAL", "Should have a final answer");
        assertNoEvent(events, "ERROR", "Should have no errors");
        assertHasEvent(events, "TOOL_CALL", "Should have a tool call");
        assertEventWithTool(events, "TOOL_CALL", "ProjectScanTool", "Should call ProjectScanTool");

        System.out.printf("  [PASS] scan  | events=%d | tool=ProjectScanTool%n", events.size());
    }

    @Test
    void commandExecuteRequestShouldCallCommandExecuteTool() {
        ChatRequest req = new ChatRequest();
        req.setMessage("执行 echo hello 命令");

        List<AgentEvent> events = collectEvents(agent.executeStream(req));

        assertHasEvent(events, "FINAL", "Should have a final answer");
        assertNoEvent(events, "ERROR", "Should have no errors");
        assertHasEvent(events, "TOOL_CALL", "Should have a tool call");
        assertEventWithTool(events, "TOOL_CALL", "CommandExecuteTool", "Should call CommandExecuteTool");

        System.out.printf("  [PASS] exec  | events=%d | tool=CommandExecuteTool%n", events.size());
    }

    @Test
    void allScenariosShouldCompleteWithoutErrors() {
        record TestScenario(String name, String message, String expectedTool) {}
        List<TestScenario> scenarios = List.of(
                new TestScenario("chat",   "你好，请介绍一下你自己", "NONE"),
                new TestScenario("read",   "读取 pom.xml 文件", "FileReadTool"),
                new TestScenario("scan",   "列出项目所有文件", "ProjectScanTool"),
                new TestScenario("exec",   "执行 echo test", "CommandExecuteTool"),
                new TestScenario("write",  "帮我创建一个 HelloWorld.java 文件", "FileWriteTool")
        );

        int passed = 0;
        for (TestScenario s : scenarios) {
            ChatRequest req = new ChatRequest();
            req.setMessage(s.message);

            List<AgentEvent> events = collectEvents(agent.executeStream(req));

            boolean hasFinal = events.stream().anyMatch(e -> "FINAL".equals(e.getType()));
            boolean hasError = events.stream().anyMatch(e -> "ERROR".equals(e.getType()));
            boolean correctTool = events.stream()
                    .anyMatch(e -> "TOOL_CALL".equals(e.getType())
                            && s.expectedTool.equals(e.getToolName()));

            if (hasFinal && !hasError && ("NONE".equals(s.expectedTool) || correctTool)) {
                passed++;
                System.out.printf("  [PASS] %-6s | final=%s error=%s tool=%s%n",
                        s.name, hasFinal, hasError,
                        events.stream().filter(e -> "TOOL_CALL".equals(e.getType()))
                                .map(AgentEvent::getToolName).findFirst().orElse("NONE"));
            } else {
                String actualTool = events.stream()
                        .filter(e -> "TOOL_CALL".equals(e.getType()))
                        .map(AgentEvent::getToolName).findFirst().orElse("NONE");
                System.out.printf("  [FAIL] %-6s | final=%s error=%s expectedTool=%s actual=%s%n",
                        s.name, hasFinal, hasError, s.expectedTool, actualTool);
            }
        }

        System.out.printf("%n  Summary: %d/%d passed%n", passed, scenarios.size());
        assertEquals(scenarios.size(), passed, "All scenarios should pass");
    }

    // ---- helpers ----

    private List<AgentEvent> collectEvents(Flux<AgentEvent> flux) {
        return flux.collectList()
                .block(Duration.ofSeconds(120));
    }

    private void assertHasEvent(List<AgentEvent> events, String type, String msg) {
        assertNotNull(events, "Events list should not be null");
        assertFalse(events.isEmpty(), "Should have at least one event");
        assertTrue(
                events.stream().anyMatch(e -> type.equals(e.getType())),
                msg + " (events: " + eventTypes(events) + ")");
    }

    private void assertNoEvent(List<AgentEvent> events, String type, String msg) {
        assertTrue(
                events.stream().noneMatch(e -> type.equals(e.getType())),
                msg);
    }

    private void assertEventWithTool(List<AgentEvent> events, String type, String toolName, String msg) {
        assertTrue(
                events.stream().anyMatch(e -> type.equals(e.getType())
                        && toolName.equals(e.getToolName())),
                msg);
    }

    private Set<String> eventTypes(List<AgentEvent> events) {
        return events.stream().map(AgentEvent::getType).collect(Collectors.toSet());
    }
}
