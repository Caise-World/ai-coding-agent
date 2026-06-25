package com.aicoding.agent.rag;

import com.aicoding.agent.rag.routing.CodeQuestionDetector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Routing rule regression eval. Pure rule-based, no LLM, no mocks.
 * Run via: mvn test -Dtest=RoutingEvalTest
 *
 * Goal: positive samples must return needsRag=true (RAG fires),
 *       negative samples must return needsRag=false (no RAG).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RoutingEvalTest {

    @Autowired
    private CodeQuestionDetector detector;

    // ---- positive: should trigger RAG ----

    @Test void classNameWithServiceSuffix_triggers() {
        assertTrue(detector.needsRag("MemoryService 怎么存长期记忆"));
    }

    @Test void classNameWithToolSuffix_triggers() {
        assertTrue(detector.needsRag("ToolSelector 是怎么选 tool 的"));
    }

    @Test void classNameWithControllerSuffix_triggers() {
        assertTrue(detector.needsRag("StreamingAgentController 在哪里定义的"));
    }

    @Test void classNameWithApplicationSuffix_triggers() {
        assertTrue(detector.needsRag("PluginBasedStreamingAgentService 怎么实现自愈"));
    }

    @Test void callChainKeyword_triggers() {
        assertTrue(detector.needsRag("调用链是什么"));
    }

    @Test void executionFlowKeyword_triggers() {
        assertTrue(detector.needsRag("执行流程是怎样的"));
    }

    @Test void sourceCodeKeyword_triggers() {
        assertTrue(detector.needsRag("看一下 Foo.java 的源码"));
    }

    @Test void fileExtension_triggers() {
        assertTrue(detector.needsRag("修改 Foo.java"));
    }

    @Test void methodCallForm_triggers() {
        assertTrue(detector.needsRag("ToolRegistry.getAll 怎么调用"));
    }

    @Test void mechanismKeyword_triggers() {
        assertTrue(detector.needsRag("RagService.retrieveContext 的工作机制"));
    }

    @Test void definitionMechanism_triggers() {
        assertTrue(detector.needsRag("MemoryService.saveShortTerm 的实现原理"));
    }

    // ---- negative: should NOT trigger RAG ----

    @Test void greeting_doesNotTrigger() {
        assertFalse(detector.needsRag("你好"));
        assertFalse(detector.needsRag("hello"));
    }

    @Test void generalConcept_doesNotTrigger() {
        assertFalse(detector.needsRag("Java 和 Python 有什么区别"));
        assertFalse(detector.needsRag("什么是 Spring Boot"));
    }

    @Test void thanks_doesNotTrigger() {
        assertFalse(detector.needsRag("谢谢"));
        assertFalse(detector.needsRag("thanks"));
    }

    @Test void cooking_doesNotTrigger() {
        assertFalse(detector.needsRag("蛋糕怎么做"));
        assertFalse(detector.needsRag("红烧肉怎么做"));
    }

    @Test void officeDocs_doesNotTrigger() {
        assertFalse(detector.needsRag("PPT 怎么做"));
        assertFalse(detector.needsRag("Word 文档怎么排版"));
    }

    @Test void weather_doesNotTrigger() {
        assertFalse(detector.needsRag("今天天气怎么样"));
    }

    @Test void emptyAndNull_doNotTrigger() {
        assertFalse(detector.needsRag(""));
        assertFalse(detector.needsRag(null));
        assertFalse(detector.needsRag("   "));
    }
}