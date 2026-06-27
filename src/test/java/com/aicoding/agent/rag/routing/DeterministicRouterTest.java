package com.aicoding.agent.rag.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deterministic tests for the priority-chain router. No Spring context, no LLM.
 *
 * Each rule gets must-match and must-NOT-match cases.
 */
class DeterministicRouterTest {

    private DeterministicRouter router;
    private static final String PROJECT = "/tmp/test-project";

    @BeforeEach
    void setUp() {
        router = new DeterministicRouter(new CodeQuestionDetector());
    }

    // ─── R1: Greeting → NONE (pure greetings only) ───────────

    @Test void r1_pureGreetingChinese_niHao() {
        var r = router.classify("你好", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r1_pureGreetingEnglish_hello() {
        var r = router.classify("hello", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r1_pureGreetingEnglish_hi() {
        var r = router.classify("hi", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r1_pureGreetingChinese_xieXie() {
        var r = router.classify("谢谢", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r1_pureGreetingChinese_zaiJian() {
        var r = router.classify("再见", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r1_pureGreetingChinese_niShiShui() {
        var r = router.classify("你是谁", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r1_pureGreetingEnglish_thanks() {
        var r = router.classify("thanks", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r1_pureGreetingEnglish_goodbye() {
        var r = router.classify("goodbye", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r1_compoundGreetingWithFileRead_fallsThrough() {
        // "你好，帮我查看 pom.xml" → strip greeting → R2 matches
        var r = router.classify("你好，帮我查看 pom.xml", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("FileReadTool", r.toolName());
        assertEquals("pom.xml", r.input());
    }

    @Test void r1_compoundGreetingWithCommand_fallsThrough() {
        // "hi, run git status" → strip greeting → R3 matches
        var r = router.classify("hi, run git status", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("CommandExecuteTool", r.toolName());
    }

    @Test void r1_compoundGreetingWithFileWrite_fallsThrough() {
        // "谢谢，帮我创建一个 README.md" → strip greeting → R6 matches
        var r = router.classify("谢谢，帮我创建一个 README.md", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("FileWriteTool", r.toolName());
        assertEquals("README.md", r.input());
    }

    // ─── R2: File Read → FileReadTool ─────────────────────────

    @Test void r2_readPomXml() {
        var r = router.classify("读取 pom.xml 文件的内容", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("FileReadTool", r.toolName());
        assertEquals("pom.xml", r.input());
    }

    @Test void r2_readClaudeMd() {
        var r = router.classify("read the file CLAUDE.md", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("FileReadTool", r.toolName());
        assertEquals("CLAUDE.md", r.input());
    }

    @Test void r2_viewApplicationYml() {
        var r = router.classify("帮我看下 application.yml 里配置了什么", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("FileReadTool", r.toolName());
        assertEquals("application.yml", r.input());
    }

    @Test void r2_viewJavaSource() {
        var r = router.classify("查看 AgentApplication.java 的源代码", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("FileReadTool", r.toolName());
        assertEquals("AgentApplication.java", r.input());
    }

    @Test void r2_catJsonFile() {
        var r = router.classify("cat package.json for me", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("FileReadTool", r.toolName());
        assertEquals("package.json", r.input());
    }

    @Test void r2_showPropertiesFile() {
        var r = router.classify("显示 config.properties 的内容", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("FileReadTool", r.toolName());
        assertEquals("config.properties", r.input());
    }

    @Test void r2_noFileReadVerb_onlyExtension_shouldNotMatch() {
        // Has file extension but no read/view verb
        var r = router.classify("HelloWorld.java 在哪里", PROJECT);
        // R4: CodeQuestionDetector — "HelloWorld.java" has file extension pattern 7 match
        // but actually pattern 3: "在哪里" + ".{0,8}" + "实现|定义|调用|写" — no, ".java" is between them
        // Let me trace: needsRag("HelloWorld.java 在哪里"):
        // Pattern 7: \b\w+\.(java|...)\b matches "HelloWorld.java" → true
        // So R4 fires → NONE
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r2_readVerbWithoutExtension_shouldNotMatch() {
        // Has "查看" but no filename with extension
        var r = router.classify("查看一下项目", PROJECT);
        // R4: needsRag("查看一下项目") — no class name, no CamelCase, no understanding words → false
        // R5-R7: nothing
        // R8: "项目" — PROJECT_SCAN_VERB has "项目结构|目录结构|整体布局" but not standalone "项目"
        // Actually "list.*(files|dir|project|structure)" - "查看" is not "list"
        // So nothing matches → AMBIGUOUS
        assertTrue(r.ambiguous());
        assertEquals("AMBIGUOUS", r.toolName());
    }

    // ─── R3: System Command → CommandExecuteTool ──────────────

    @Test void r3_explicitMvnCompile() {
        var r = router.classify("执行 mvn clean compile 命令", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("CommandExecuteTool", r.toolName());
    }

    @Test void r3_runGitStatus() {
        var r = router.classify("run git status to see what files changed", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("CommandExecuteTool", r.toolName());
    }

    @Test void r3_dockerPs() {
        var r = router.classify("docker ps", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("CommandExecuteTool", r.toolName());
    }

    @Test void r3_kubectlGetPods() {
        var r = router.classify("kubectl get pods", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("CommandExecuteTool", r.toolName());
    }

    @Test void r3_npmInstall() {
        var r = router.classify("npm install express", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("CommandExecuteTool", r.toolName());
    }

    @Test void r3_gitCheckout() {
        var r = router.classify("git checkout dev", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("CommandExecuteTool", r.toolName());
    }

    @Test void r3_curlCommand() {
        var r = router.classify("curl https://example.com/api", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("CommandExecuteTool", r.toolName());
    }

    @Test void r3_mavenVersionInquiry() {
        // "帮我查看当前 maven 的版本" — has maven cmd name, no question keyword
        // QUESTION_PHRASING: 什么是|是什么|为什么|怎么|如何|原理|机制|区别|解释 — none present
        var r = router.classify("帮我查看当前 maven 的版本", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("CommandExecuteTool", r.toolName());
    }

    @Test void r3_questionAboutDocker_rejected() {
        // "什么是 Docker" — has question phrasing → R3 rejects
        // R4: needsRag("什么是 Docker") — no code patterns match → false
        // Falls through to AMBIGUOUS
        var r = router.classify("什么是 Docker", PROJECT);
        assertTrue(r.ambiguous());
        assertEquals("AMBIGUOUS", r.toolName());
    }

    @Test void r3_mavenPrincipleQuestion_rejected() {
        // "maven 的工作原理是什么" — has "原理" question keyword → R3 rejects
        var r = router.classify("maven 的工作原理是什么", PROJECT);
        // R4: needsRag — pattern 4: (实现|定义|背后)\s*(原理|机制|...) — "工作" is not in prefix list
        // pattern 10: 做(什么|啥)(的)? — "maven 的工作" doesn't match
        // So R4 returns false
        // Falls through to AMBIGUOUS
        assertTrue(r.ambiguous());
        assertEquals("AMBIGUOUS", r.toolName());
    }

    // ─── R4: Code Understanding → NONE ────────────────────────

    @Test void r4_selfHealingQuestion() {
        var r = router.classify("PluginBasedStreamingAgentService 怎么实现自愈", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r4_workingPrinciple() {
        var r = router.classify("PluginBasedStreamingAgentService 工作原理", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r4_whatDoesXDo() {
        var r = router.classify("MemoryService 做什么", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r4_callChain() {
        var r = router.classify("ToolSelector 调用链", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r4_whyDesignedThisWay() {
        var r = router.classify("PluginBasedStreamingAgentService 为什么这样设计", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r4_whatIsSpringBoot() {
        var r = router.classify("什么是 Spring Boot", PROJECT);
        // needsRag("什么是 Spring Boot"): "Spring Boot" is two capitalized words
        // Pattern 9: "Spring" → S+p+r+i+n+g → all lowercase after S, then space (not [A-Za-z0-9])
        // Pattern 9 requires [A-Z][a-z]+[A-Z] — "Spring" is just one word, doesn't have second uppercase
        // So needsRag returns false
        // Falls through to AMBIGUOUS
        assertTrue(r.ambiguous());
        assertEquals("AMBIGUOUS", r.toolName());
    }

    @Test void r4_whatDoesProjectDo() {
        var r = router.classify("项目主要是做什么的", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r4_howDoesMemoryStoreWork() {
        var r = router.classify("MemoryService 怎么存长期记忆", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r4_javaVsPython() {
        var r = router.classify("Java 和 Python 有什么区别", PROJECT);
        // needsRag: "Java" — Pattern 9: J+a+v+a — is there a second uppercase? "Java" → J + ava. No second uppercase.
        // "Python" → P + ython. No second uppercase.
        // Pattern 2: 怎么|如何|怎样 — none
        // Pattern 10: 做(什么|啥)(的)? — none
        // So needsRag returns false
        // Falls through to AMBIGUOUS
        assertTrue(r.ambiguous());
        assertEquals("AMBIGUOUS", r.toolName());
    }

    @Test void r4_sourceCodeReference() {
        var r = router.classify("看看 ToolRegistry 的源码", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    // ─── R5: Code Generation → NONE ───────────────────────────

    @Test void r5_quickSortCode() {
        var r = router.classify("给一下快速排序的代码", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r5_giveMeJavaQuickSort() {
        var r = router.classify("给我一个 Java 快速排序的实现", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r5_writeAlgorithm() {
        var r = router.classify("写一段二分查找的算法", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r5_outputSomeCode() {
        var r = router.classify("输出一段冒泡排序的 Python 代码", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r5_codeGenWithFileName_notMatched() {
        // "生成一个 QuickSort.java" — has code gen verb BUT also has file extension
        // R5 should NOT match (negative: file extension present)
        // Falls through to R6
        var r = router.classify("生成一个 QuickSort.java", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("FileWriteTool", r.toolName());
        assertEquals("QuickSort.java", r.input());
    }

    @Test void r5_codeGenWithCreate_notMatched() {
        // "帮我创建一个 HelloWorld.java" — "创建" is a write verb, not a pure gen verb
        // CODE_GEN_VERB: 给一下|给我|写一段|写一个|输出一段|给我一个|给一段
        // "创建" is NOT in this list
        var r = router.classify("帮我创建一个 HelloWorld.java", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("FileWriteTool", r.toolName());
    }

    // ─── R6: File Write → FileWriteTool ───────────────────────

    @Test void r6_createJavaClass() {
        var r = router.classify("帮我创建一个新的 Java 类 HelloWorld.java", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("FileWriteTool", r.toolName());
        assertEquals("HelloWorld.java", r.input());
    }

    @Test void r6_writeReadme() {
        var r = router.classify("write a README.md file", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("FileWriteTool", r.toolName());
        assertEquals("README.md", r.input());
    }

    @Test void r6_generateProperties() {
        var r = router.classify("生成一个 application.properties 配置文件", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("FileWriteTool", r.toolName());
        assertEquals("application.properties", r.input());
    }

    @Test void r6_saveToFile() {
        var r = router.classify("把这个保存到文件 output.txt", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("FileWriteTool", r.toolName());
        assertEquals("output.txt", r.input());
    }

    @Test void r6_writeNoExtensionButFileKeyword() {
        var r = router.classify("创建文件 ConfigHelper", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("FileWriteTool", r.toolName());
    }

    @Test void r6_cookingQuestion_notFileWrite() {
        // "怎么做蛋糕" — has "做" but not a file write context
        // FILE_WRITE_VERB has "写|创建|生成|保存|write|create|generate|save"
        // "做" is NOT in FILE_WRITE_VERB
        // CODE_GEN_VERB: 给一下|给我|写一段|写一个|输出一段|给我一个|给一段 — "做" is NOT in here either
        // R4: needsRag("怎么做蛋糕") — pattern 2: 怎么\s*做 — wait, 做 is not in the verb list (实现|定义|工作|调用|写|用|调)
        // Pattern 10: 做(什么|啥)(的)? — "怎么做" - "做" then "怎么"? No, "做(什么|啥)" matches "做什么" or "做啥", not "怎么做"
        // So R4: needsRag checks pattern 2: (怎么|如何|怎样)\s*(实现|定义|工作|调用|写|用|调) — "怎么" matches, then \s*, then "做" - 做 is NOT in the verb list. No match.
        // Pattern 10: 做(什么|啥)(的)? — starts with "做" and expects 什么 or 啥. "怎么做蛋糕" — "做" then "怎" - doesn't match 什么|啥
        // So needsRag returns false → R4 doesn't fire
        // R5: CODE_GEN_VERB — "做" not in the list
        // R6: FILE_WRITE_VERB — "做" not in the list → R6 doesn't fire
        // R7: SEARCH_VERB — no
        // R8: PROJECT_SCAN_VERB — no
        // → AMBIGUOUS
        var r = router.classify("怎么做蛋糕", PROJECT);
        assertTrue(r.ambiguous());
        assertEquals("AMBIGUOUS", r.toolName());
    }

    // ─── R7: Text Search → GrepTool ───────────────────────────

    @Test void r7_searchForAutowired() {
        // No CamelCase identifiers to trigger R4
        var r = router.classify("搜索所有包含 @Autowired 的文件", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("GrepTool", r.toolName());
    }

    @Test void r7_findInJavaFiles() {
        // "PostConstruct" matches CamelCase pattern 9 → R4 fires (priority 4 > 7)
        // This is the known mixed-intent limitation: class-name presence +
        // search verb → code understanding wins over text search.
        var r = router.classify("find all files containing @PostConstruct", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    @Test void r7_locateConfig() {
        var r = router.classify("locate database configuration in the project", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("GrepTool", r.toolName());
    }

    @Test void r7_grepForPattern() {
        var r = router.classify("grep for Exception in the codebase", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("GrepTool", r.toolName());
    }

    @Test void r7_whereIsPostConstruct() {
        // "PostConstruct" matches CamelCase pattern 9 → R4 fires (priority 4 > 7)
        var r = router.classify("在项目里搜一下哪里用了 @PostConstruct", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("NONE", r.toolName());
    }

    // ─── R8: Project Scan → ProjectScanTool ───────────────────

    @Test void r8_projectDirectoryStructure() {
        var r = router.classify("帮我分析这个项目的目录结构", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("ProjectScanTool", r.toolName());
    }

    @Test void r8_scanProject() {
        var r = router.classify("scan the project structure and list all files", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("ProjectScanTool", r.toolName());
    }

    @Test void r8_listFiles() {
        var r = router.classify("列出项目里所有的文件和文件夹", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("ProjectScanTool", r.toolName());
    }

    @Test void r8_overallLayout() {
        var r = router.classify("看看这个项目的整体布局", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("ProjectScanTool", r.toolName());
    }

    @Test void r8_listDir() {
        var r = router.classify("list the project directory", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("ProjectScanTool", r.toolName());
    }

    // ─── AMBIGUOUS → ToolSelector fallback ────────────────────

    @Test void ambiguous_howWasProjectBuilt() {
        var r = router.classify("这个项目是怎么搭起来的", PROJECT);
        // R4: needsRag("这个项目是怎么搭起来的"):
        // Pattern 2: (怎么|如何|怎样)\s*(实现|定义|工作|调用|写|用|调) — "怎么" matches, \s* matches "", then "搭" — not in verb list. No match.
        // Pattern 1: no class name
        // Pattern 9: no CamelCase
        // Pattern 10: no 做什么
        // So needsRag → false
        // R5-R8: no matches
        // → AMBIGUOUS
        assertTrue(r.ambiguous());
        assertEquals("AMBIGUOUS", r.toolName());
    }

    @Test void ambiguous_analyzeArchitecture() {
        var r = router.classify("帮我看看整体架构", PROJECT);
        assertTrue(r.ambiguous());
        assertEquals("AMBIGUOUS", r.toolName());
    }

    @Test void ambiguous_blankMessage() {
        var r = router.classify("", PROJECT);
        assertTrue(r.ambiguous());
        assertEquals("AMBIGUOUS", r.toolName());
    }

    @Test void ambiguous_nullMessage() {
        var r = router.classify(null, PROJECT);
        assertTrue(r.ambiguous());
        assertEquals("AMBIGUOUS", r.toolName());
    }

    @Test void ambiguous_howToSetupCiCd() {
        var r = router.classify("怎么搭建 CI/CD 流水线", PROJECT);
        // R4: needsRag — pattern 2: "怎么" matches, then "搭建" - "搭" not in verb list → no
        // R3: no command name → no
        // R5-R8: no → AMBIGUOUS
        assertTrue(r.ambiguous());
        assertEquals("AMBIGUOUS", r.toolName());
    }

    @Test void ambiguous_improvePerformance() {
        var r = router.classify("这个系统性能怎么优化", PROJECT);
        assertTrue(r.ambiguous());
        assertEquals("AMBIGUOUS", r.toolName());
    }

    @Test void ambiguous_recommendLibrary() {
        var r = router.classify("推荐一个适合的日志库", PROJECT);
        assertTrue(r.ambiguous());
        assertEquals("AMBIGUOUS", r.toolName());
    }

    // ─── Input extraction ─────────────────────────────────────

    @Test void r2_inputIsFilename() {
        // "Dockerfile" has no dot-extension so CODE_FILE_EXT won't match
        // R2 falls through → R4: needsRag false (Dockerfile is single word, not CamelCase)
        // → AMBIGUOUS (ambiguous — may need LLM to determine intent)
        var r = router.classify("读取 Dockerfile 的内容", PROJECT);
        assertTrue(r.ambiguous());
        assertEquals("AMBIGUOUS", r.toolName());
    }

    @Test void r6_inputIsFilename() {
        var r = router.classify("写一个 logback.xml", PROJECT);
        assertFalse(r.ambiguous());
        assertEquals("FileWriteTool", r.toolName());
        assertEquals("logback.xml", r.input());
    }
}
