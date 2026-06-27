package com.aicoding.agent.eval;

import com.aicoding.agent.registry.ToolSelector;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * ToolSelection regression eval.
 * Reads eval-dataset.json, calls the real ToolSelector (LLM-driven),
 * and reports pass rate + failed cases.
 *
 * Run via: mvn test -Dtest=ToolSelectionEvalTest
 * Or as a standalone main method.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ToolSelectionEvalTest {

    @Autowired
    private ToolSelector toolSelector;

    private static final String PROJECT_PATH = "/tmp/eval-project";

    @Test
    void runEval() throws Exception {
        List<EvalCase> cases = loadDataset();
        List<String> failures = new ArrayList<>();
        int passed = 0;

        System.out.println("\n========================================");
        System.out.println("  ToolSelection Eval Runner");
        System.out.println("  Dataset: " + cases.size() + " cases");
        System.out.println("========================================\n");

        for (int i = 0; i < cases.size(); i++) {
            EvalCase c = cases.get(i);
            ToolSelector.ToolSelection result = toolSelector.select(c.input(), PROJECT_PATH);

            String actual = result.toolName() != null ? result.toolName() : "NONE";
            boolean toolOk = c.matches(actual);
            boolean ragOk = c.matchesRag(result.needsRag());
            boolean ok = toolOk && ragOk;

            String marker = ok ? "PASS" : "FAIL";
            System.out.printf("  [%s] #%d  input: \"%s\"%n", marker, i + 1, c.input());
            System.out.printf("        tool:    expected=%-18s  actual=%s%n", c.expectedTool(), actual);
            if (c.expectedNeedsRag() != null) {
                System.out.printf("        needsRag: expected=%-17s  actual=%s%n",
                        c.expectedNeedsRag(), result.needsRag());
            }

            if (ok) {
                passed++;
            } else {
                String reason = !toolOk ? "tool mismatch" : "needsRag mismatch";
                failures.add(String.format("#%d \"%s\" → expected=%s actual=%s (%s)",
                        i + 1, c.input(), c.expectedTool(), actual, reason));
            }
        }

        System.out.println("========================================");
        System.out.printf("  Pass rate: %d/%d (%.0f%%)%n",
                passed, cases.size(), 100.0 * passed / cases.size());
        System.out.println("========================================");

        if (!failures.isEmpty()) {
            System.out.println("\n  FAILED CASES:");
            failures.forEach(f -> System.out.println("  " + f));
            System.out.println();
            fail(failures.size() + " case(s) failed. See output above for details.");
        }
    }

    private List<EvalCase> loadDataset() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("eval-dataset.json")) {
            if (is == null) {
                throw new IllegalStateException("eval-dataset.json not found in test resources");
            }
            return mapper.readValue(is, new TypeReference<>() {});
        }
    }

    /**
     * Standalone entry point — requires Spring Boot context.
     * For quick runs, use {@code mvn test -Dtest=ToolSelectionEvalTest}.
     */
    public static void main(String[] args) {
        System.out.println("Run via: mvn test -Dtest=ToolSelectionEvalTest#runEval");
    }
}
