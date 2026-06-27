package com.aicoding.agent.rag.routing;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Deterministic priority-chain router for tool selection.
 *
 * Routes clear-cut user inputs via regex rules (zero LLM calls).
 * Ambiguous inputs fall back to {@link com.aicoding.agent.registry.ToolSelector}.
 *
 * P13-A: bypasses ToolSelector for deterministic inputs.
 * P13-B (future): ToolSelector prompt shrinks to only handle AMBIGUOUS cases.
 */
@Component
public class DeterministicRouter {

    private final CodeQuestionDetector codeQuestionDetector;

    // R1: Greeting tokens
    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "^\\s*(你好|谢谢|再见|hello|hi|thanks|bye|你是谁|thank you|goodbye)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // R2: File read verbs — Chinese (no \b needed, characters are distinct)
    private static final Pattern FILE_READ_VERB_CN = Pattern.compile("(读取|查看|看下|打开|显示)");
    // R2: File read verbs — English (\b prevents "read" matching inside "README")
    private static final Pattern FILE_READ_VERB_EN = Pattern.compile(
            "\\b(read|view|show|cat)\\b", Pattern.CASE_INSENSITIVE);

    // R2: Filename with code extension
    private static final Pattern CODE_FILE_EXT = Pattern.compile(
            "\\b\\w+\\.(java|yml|yaml|xml|md|properties|json|txt|py|kt|js|ts|go|rs|cpp|c|h)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // R3: System command names
    private static final Pattern COMMAND_NAME = Pattern.compile(
            "\\b(git|mvn|maven|npm|yarn|docker|kubectl|curl|wget|ssh|scp|rsync|pip|gradle|brew|apt|yum)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // R3: Negative guard — question-phrasing keywords
    private static final Pattern QUESTION_PHRASING = Pattern.compile(
            "(什么是|是什么|为什么|怎么|如何|原理|机制|区别|解释)"
    );

    // R5: Code generation verbs (no file target)
    private static final Pattern CODE_GEN_VERB = Pattern.compile(
            "(给一下|给我|写一段|写一个|输出一段|给我一个|给一段)"
    );

    // R6: File write verbs — Chinese
    private static final Pattern FILE_WRITE_VERB_CN = Pattern.compile("(写|创建|生成|保存)");
    // R6: File write verbs — English
    private static final Pattern FILE_WRITE_VERB_EN = Pattern.compile(
            "\\b(write|create|generate|save)\\b", Pattern.CASE_INSENSITIVE);

    // R6: Filename with code extension (write-targeted extensions)
    private static final Pattern FILE_TARGET = Pattern.compile(
            "\\b\\w+\\.(java|yml|yaml|xml|md|properties|json|txt|py|kt|js|ts|go|rs)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // R6: File-related words (Chinese no \b; English uses \b)
    private static final Pattern FILE_WORD = Pattern.compile(
            "(文件|保存到|写入到|\\bfile\\b)", Pattern.CASE_INSENSITIVE);

    // R7: Search verb
    private static final Pattern SEARCH_VERB = Pattern.compile(
            "(搜索|查找|找出|搜|search|find|locate|grep)",
            Pattern.CASE_INSENSITIVE
    );

    // R8: Project scan verb
    private static final Pattern PROJECT_SCAN_VERB = Pattern.compile(
            "(列出.*文件|列出.*项目|项目结构|目录结构|整体布局|scan|list.*(files|dir|project|structure))",
            Pattern.CASE_INSENSITIVE
    );

    public DeterministicRouter(CodeQuestionDetector codeQuestionDetector) {
        this.codeQuestionDetector = codeQuestionDetector;
    }

    /**
     * Classify the user message through the priority chain.
     * Returns AMBIGUOUS when no rule matches — caller must fall back to ToolSelector.
     */
    public RouterResult classify(String userMessage, String projectPath) {
        if (userMessage == null || userMessage.isBlank()) {
            return RouterResult.ambiguous(userMessage);
        }

        // R1: Greeting → NONE (pure greeting only, compound requests fall through)
        RouterResult r1 = classifyGreeting(userMessage);
        if (r1 != null) return r1;

        // R2: File Read → FileReadTool
        RouterResult r2 = classifyFileRead(userMessage, projectPath);
        if (r2 != null) return r2;

        // R3: System Command → CommandExecuteTool
        RouterResult r3 = classifySystemCommand(userMessage);
        if (r3 != null) return r3;

        // R4: Code Understanding → NONE
        // Guard: don't fire when the message is a clear file read/write operation.
        // CodeQuestionDetector pattern 7 matches any \w+\.java (etc.), which
        // would incorrectly intercept "read pom.xml" or "write README.md".
        if (codeQuestionDetector != null && codeQuestionDetector.needsRag(userMessage)
                && !hasFileOperation(userMessage)) {
            return new RouterResult("NONE", userMessage, false);
        }

        // R5: Code Generation → NONE
        RouterResult r5 = classifyCodeGeneration(userMessage);
        if (r5 != null) return r5;

        // R6: File Write → FileWriteTool
        RouterResult r6 = classifyFileWrite(userMessage, projectPath);
        if (r6 != null) return r6;

        // R7: Text Search → GrepTool
        if (SEARCH_VERB.matcher(userMessage).find()) {
            return new RouterResult("GrepTool", userMessage, false);
        }

        // R8: Project Scan → ProjectScanTool
        if (PROJECT_SCAN_VERB.matcher(userMessage).find()) {
            return new RouterResult("ProjectScanTool", projectPath, false);
        }

        // R9: Likely Chat — no code signals at all → NONE
        if (!hasAnyCodeSignal(userMessage)) {
            return new RouterResult("NONE", userMessage, false);
        }

        // Final: AMBIGUOUS — fall back to ToolSelector
        return RouterResult.ambiguous(userMessage);
    }

    // ─── R1: Greeting ─────────────────────────────────────────

    private RouterResult classifyGreeting(String message) {
        var matcher = GREETING_PATTERN.matcher(message);
        if (!matcher.find()) return null;

        // Strip the greeting, check if remainder has content
        String remainder = message.substring(matcher.end()).trim();
        // Remove leading punctuation that connects greeting to real request
        remainder = remainder.replaceFirst("^[,，。！!\\s]+", "").trim();

        if (!remainder.isEmpty()) {
            return null; // compound request — fall through to next rules
        }

        return new RouterResult("NONE", message, false);
    }

    // ─── R2: File Read ────────────────────────────────────────

    private RouterResult classifyFileRead(String message, String projectPath) {
        if (!hasFileReadVerb(message)) return null;

        var fileMatcher = CODE_FILE_EXT.matcher(message);
        if (!fileMatcher.find()) return null;

        String filename = fileMatcher.group();
        return new RouterResult("FileReadTool", filename, false);
    }

    // ─── R3: System Command ───────────────────────────────────

    private RouterResult classifySystemCommand(String message) {
        if (!COMMAND_NAME.matcher(message).find()) return null;

        // Negative guard: question-phrasing keywords
        if (QUESTION_PHRASING.matcher(message).find()) return null;

        // Belt-and-suspenders: not a code-understanding question
        if (codeQuestionDetector != null && codeQuestionDetector.needsRag(message)) return null;

        return new RouterResult("CommandExecuteTool", message, false);
    }

    // ─── R5: Code Generation ──────────────────────────────────

    private RouterResult classifyCodeGeneration(String message) {
        if (!CODE_GEN_VERB.matcher(message).find()) return null;

        // Negative: if a code file extension is present, this is likely FileWrite (R6)
        if (FILE_TARGET.matcher(message).find()) return null;

        return new RouterResult("NONE", message, false);
    }

    // ─── R6: File Write ───────────────────────────────────────

    private RouterResult classifyFileWrite(String message, String projectPath) {
        if (!hasFileWriteVerb(message)) return null;

        if (FILE_TARGET.matcher(message).find() || FILE_WORD.matcher(message).find()) {
            // Extract filename if present
            var fileMatcher = FILE_TARGET.matcher(message);
            String input = fileMatcher.find() ? fileMatcher.group() : message;
            return new RouterResult("FileWriteTool", input, false);
        }

        return null;
    }

    // ─── R4 Guard ──────────────────────────────────────────────

    /**
     * Returns true when the message is a clear file read or write operation.
     * Used by R4 to avoid intercepting file ops that happen to contain a
     * code-file extension (CodeQuestionDetector pattern 7).
     */
    private boolean hasFileOperation(String message) {
        // File read: read/view verb + code file extension
        if (hasFileReadVerb(message) && CODE_FILE_EXT.matcher(message).find()) {
            return true;
        }
        // File write: write verb + (file extension or file keyword)
        if (hasFileWriteVerb(message)
                && (FILE_TARGET.matcher(message).find() || FILE_WORD.matcher(message).find())) {
            return true;
        }
        return false;
    }

    private boolean hasFileReadVerb(String message) {
        return FILE_READ_VERB_CN.matcher(message).find() || FILE_READ_VERB_EN.matcher(message).find();
    }

    private boolean hasFileWriteVerb(String message) {
        return FILE_WRITE_VERB_CN.matcher(message).find() || FILE_WRITE_VERB_EN.matcher(message).find();
    }

    // ─── R9: Likely Chat Signal ────────────────────────────────

    /**
     * Returns true if the message contains ANY code-related signal.
     * Used by R9 to short-circuit chat questions to NONE without any LLM call.
     */
    private static final Pattern LATIN_LETTERS = Pattern.compile("[a-zA-Z]");
    private static final Pattern CODE_KEYWORDS_CN = Pattern.compile(
            "(代码|项目|文件|源码|程序|接口|类|方法|函数|依赖|配置|编译|部署|调试|架构|性能|日志|系统|库)");

    private boolean hasAnyCodeSignal(String message) {
        if (CODE_FILE_EXT.matcher(message).find()) return true;
        if (COMMAND_NAME.matcher(message).find()) return true;
        // Any Latin letters suggest code-related content (class names, file names, tech terms)
        if (LATIN_LETTERS.matcher(message).find()) return true;
        // Code-related Chinese keywords
        if (CODE_KEYWORDS_CN.matcher(message).find()) return true;
        return false;
    }

    // ─── Router Result ────────────────────────────────────────

    public record RouterResult(String toolName, String input, boolean ambiguous) {
        public RouterResult {
            if (toolName == null || toolName.isBlank()) {
                throw new IllegalArgumentException("toolName must not be blank");
            }
        }

        public static RouterResult ambiguous(String userMessage) {
            return new RouterResult("AMBIGUOUS", userMessage, true);
        }
    }
}
