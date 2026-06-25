package com.aicoding.agent.rag.routing;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministic detector for "is this user message a code-understanding question?"
 *
 * Pure rule-based, no LLM, no Spring deps beyond the singleton bean.
 * Called by PluginBasedStreamingAgentService in the NONE branch to decide
 * whether to inject RAG-retrieved code chunks into the final-answer prompt.
 *
 * Design note: needsRag from ToolSelector is unreliable (LLM temperature != 0).
 * Splitting the decision into this rule-based detector gives stable RAG_READ
 * appearance in real SSE streams.
 */
@Component
public class CodeQuestionDetector {

    private static final List<Pattern> CODE_PATTERNS = List.of(
            // 1. Class-name suffix (Java/Spring naming convention)
            Pattern.compile(
                    "\\b[A-Z][A-Za-z0-9]*(Service|Tool|Controller|Repository|" +
                    "Component|Manager|Handler|Bean|Application|Factory|Strategy|Provider)\\b"
            ),

            // 2. Chinese code-understanding question stem
            //    "怎么/如何/怎样" + "实现/定义/工作/调用/写/用/调"
            //    Note: "做" intentionally excluded to avoid "蛋糕怎么做" false positives.
            Pattern.compile("(怎么|如何|怎样)\\s*(实现|定义|工作|调用|写|用|调)"),

            // 3. Location-style question: "在哪里/哪儿/哪个文件 + 实现/定义/调用/写"
            Pattern.compile("(在哪里|哪儿|哪个文件|哪里).{0,8}(实现|定义|调用|写)"),

            // 4. Implementation-mechanism phrasing
            Pattern.compile("(实现|定义|背后)\\s*(原理|机制|流程|逻辑|过程|细节)"),

            // 5. Call chain / stack keywords
            Pattern.compile("(调用链|调用栈|执行流程|工作流程|调用关系|调用顺序)"),

            // 6. Source-code reference
            Pattern.compile("(源码|源代码|source\\s*code|代码\\s*(里|中|内)?)"),

            // 7. File extension (code-file hint)
            Pattern.compile("\\b\\w+\\.(java|kt|py|js|ts|go|rs|cpp|c|h)\\b"),

            // 8. Method-call form: Foo.bar(...) — even without parens
            Pattern.compile("\\b[A-Z][A-Za-z0-9]+\\.[a-z][A-Za-z0-9]*\\b"),

            // 9. CamelCase identifier (catches class names that don't end with
            //    standard suffixes, e.g. ToolSelector, AgentEvent, RagService)
            Pattern.compile("\\b[A-Z][a-z]+[A-Z][A-Za-z0-9]*\\b")
    );

    public boolean needsRag(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return false;
        }
        for (Pattern p : CODE_PATTERNS) {
            if (p.matcher(userMessage).find()) {
                return true;
            }
        }
        return false;
    }
}