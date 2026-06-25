package com.aicoding.agent.rag;

import com.aicoding.agent.dto.AgentEvent;
import com.aicoding.agent.dto.ChatRequest;
import com.aicoding.agent.memory.MemoryService;
import com.aicoding.agent.rag.routing.CodeQuestionDetector;
import com.aicoding.agent.registry.ToolExecutor;
import com.aicoding.agent.registry.ToolRegistry;
import com.aicoding.agent.registry.ToolSelector;
import com.aicoding.agent.service.LLMService;
import com.aicoding.agent.service.PluginBasedStreamingAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RagAgentPipelineTest {

    @Autowired private ToolRegistry toolRegistry;
    @Autowired private ToolExecutor toolExecutor;
    @Autowired private MemoryService memoryService;
    @Autowired private CodeQuestionDetector codeQuestionDetector;

    @MockBean private ToolSelector toolSelector;
    @MockBean private RagService ragService;
    @MockBean private LLMService llmService;

    private PluginBasedStreamingAgentService agent;

    private static final String FIXED_RAG_CONTEXT = """
            [1] /Users/wanshun/Documents/Code/ai-coding-agent/src/main/java/com/aicoding/agent/service/PluginBasedStreamingAgentService.java:118-200 (score=0.842)
            private ToolExecutionResult executeWithSelfHealing(ToolSelection selection, ...) {
                for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                    String reflection = llmService.chat("Reflect on: " + error);
                    String repair = llmService.chat("Repair: " + reflection);
                    ...
                }
            }
            """;

    private static final String FIXED_FINAL_ANSWER =
            "The self-healing mechanism uses reflection → repair → retry up to 3 attempts, " +
            "and saves failures to MemoryService for future repair context.";

    @BeforeEach
    void setUp() {
        agent = new PluginBasedStreamingAgentService(
                toolRegistry, toolSelector, toolExecutor,
                memoryService, llmService, ragService,
                codeQuestionDetector,
                "/tmp/test-project");

        when(toolSelector.select(anyString(), anyString()))
                .thenReturn(new ToolSelector.ToolSelection("NONE", "", null));

        when(ragService.retrieveContext(anyString()))
                .thenReturn(FIXED_RAG_CONTEXT);

        when(llmService.chat(anyString()))
                .thenReturn(new LLMService.LLMResponse(FIXED_FINAL_ANSWER, null));
    }

    @Test
    void ragReadEventIsEmittedAndAnswerIsGroundedInContext() {
        ChatRequest req = new ChatRequest();
        req.setMessage("PluginBasedStreamingAgentService 是怎么实现自愈的");

        List<AgentEvent> events = agent.executeStream(req)
                .collectList()
                .block(Duration.ofSeconds(10));

        assertNotNull(events);
        assertFalse(events.isEmpty(), "Should emit at least one event");

        AgentEvent ragReadEvent = events.stream()
                .filter(e -> "RAG_READ".equals(e.getType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "RAG_READ event not emitted. Events: " +
                        events.stream().map(AgentEvent::getType).toList()));

        assertEquals(FIXED_RAG_CONTEXT, ragReadEvent.getContent(),
                "RAG_READ event should carry the retrieved context");

        AgentEvent finalEvent = events.stream()
                .filter(e -> "FINAL".equals(e.getType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("FINAL event not emitted"));

        assertTrue(finalEvent.getContent().contains("self-healing"),
                "FINAL answer should contain self-healing keyword");

        verify(ragService, times(1)).retrieveContext("PluginBasedStreamingAgentService 是怎么实现自愈的");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService, atLeast(1)).chat(promptCaptor.capture());
        String promptUsedForFinalAnswer = promptCaptor.getAllValues().get(promptCaptor.getAllValues().size() - 1);

        assertTrue(promptUsedForFinalAnswer.contains("executeWithSelfHealing"),
                "Final-answer prompt should contain the RAG-retrieved code snippet");
        assertTrue(promptUsedForFinalAnswer.contains("reflection"),
                "Final-answer prompt should contain the RAG-retrieved code snippet");
        assertTrue(promptUsedForFinalAnswer.contains("PluginBasedStreamingAgentService 是怎么实现自愈的"),
                "Final-answer prompt should contain the original user message");

        System.out.println("\n========================================");
        System.out.println("RagAgentPipelineTest — full event trace");
        System.out.println("========================================");
        events.forEach(e -> System.out.printf("  %-18s | %s%n",
                e.getType(),
                e.getContent() == null ? "(null)"
                        : e.getContent().substring(0, Math.min(80, e.getContent().length()))
                          + (e.getContent().length() > 80 ? "..." : "")));
        System.out.println("========================================");
    }
}