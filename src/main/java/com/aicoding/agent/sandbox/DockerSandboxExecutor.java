package com.aicoding.agent.sandbox;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class DockerSandboxExecutor implements SandboxExecutor {

    private static final int MAX_OUTPUT_SIZE = 50000;
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final int MEMORY_LIMIT_MB = 256;
    private static final double CPU_LIMIT = 0.5;

    private static final List<Pattern> BLACKLIST_PATTERNS;

    static {
        BLACKLIST_PATTERNS = new ArrayList<>();
        BLACKLIST_PATTERNS.add(Pattern.compile("rm\\s+-rf\\s+/", Pattern.CASE_INSENSITIVE));
        BLACKLIST_PATTERNS.add(Pattern.compile("rm\\s+-rf\\s+/\\*", Pattern.CASE_INSENSITIVE));
        BLACKLIST_PATTERNS.add(Pattern.compile("fork\\s*\\(", Pattern.CASE_INSENSITIVE));
        BLACKLIST_PATTERNS.add(Pattern.compile("mkfs\\.ext", Pattern.CASE_INSENSITIVE));
        BLACKLIST_PATTERNS.add(Pattern.compile("dd\\s+if=.*of=/dev/", Pattern.CASE_INSENSITIVE));
        BLACKLIST_PATTERNS.add(Pattern.compile(">\\s*/dev/sd", Pattern.CASE_INSENSITIVE));
        BLACKLIST_PATTERNS.add(Pattern.compile("chmod\\s+-R\\s+777\\s+/", Pattern.CASE_INSENSITIVE));
        BLACKLIST_PATTERNS.add(Pattern.compile("wget\\s+.*\\|\\s*sh", Pattern.CASE_INSENSITIVE));
        BLACKLIST_PATTERNS.add(Pattern.compile("curl\\s+.*\\|\\s*sh", Pattern.CASE_INSENSITIVE));
        BLACKLIST_PATTERNS.add(Pattern.compile("shutdown", Pattern.CASE_INSENSITIVE));
        BLACKLIST_PATTERNS.add(Pattern.compile("reboot", Pattern.CASE_INSENSITIVE));
        BLACKLIST_PATTERNS.add(Pattern.compile("init\\s+0", Pattern.CASE_INSENSITIVE));
        BLACKLIST_PATTERNS.add(Pattern.compile("telinit\\s+0", Pattern.CASE_INSENSITIVE));
        BLACKLIST_PATTERNS.add(Pattern.compile("mount\\s+--bind", Pattern.CASE_INSENSITIVE));
        BLACKLIST_PATTERNS.add(Pattern.compile("chroot", Pattern.CASE_INSENSITIVE));
        BLACKLIST_PATTERNS.add(Pattern.compile("/etc/init\\.d", Pattern.CASE_INSENSITIVE));
        BLACKLIST_PATTERNS.add(Pattern.compile("systemctl", Pattern.CASE_INSENSITIVE));
        BLACKLIST_PATTERNS.add(Pattern.compile("service\\s+", Pattern.CASE_INSENSITIVE));
    }

    public DockerSandboxExecutor() {
    }

    @Override
    public ExecutionResult execute(CommandRequest request) {
        long startTime = System.currentTimeMillis();

        String command = request.getCommand();
        int timeout = request.getTimeoutSeconds() > 0 ? request.getTimeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;

        String securityError = checkSecurity(command);
        if (securityError != null) {
            return ExecutionResult.error("Security violation: " + securityError, System.currentTimeMillis() - startTime);
        }

        try {
            String dockerCommand = buildDockerCommand(request);

            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command("bash", "-c", dockerCommand);
            processBuilder.redirectErrorStream(false);

            Process process = processBuilder.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (stdout.length() < MAX_OUTPUT_SIZE) {
                            stdout.append(line).append("\n");
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            });

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (stderr.length() < MAX_OUTPUT_SIZE) {
                            stderr.append(line).append("\n");
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            });

            stdoutThread.start();
            stderrThread.start();

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);

            stdoutThread.join(500);
            stderrThread.join(500);

            long costTime = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                return ExecutionResult.error("Timeout after " + timeout + " seconds", costTime);
            }

            String stdoutStr = stdout.toString();
            String stderrStr = stderr.toString();

            if (stdoutStr.length() > MAX_OUTPUT_SIZE) {
                stdoutStr = stdoutStr.substring(0, MAX_OUTPUT_SIZE) + "\n... (truncated)";
            }
            if (stderrStr.length() > MAX_OUTPUT_SIZE) {
                stderrStr = stderrStr.substring(0, MAX_OUTPUT_SIZE) + "\n... (truncated)";
            }

            return ExecutionResult.success(stdoutStr, stderrStr, process.exitValue(), costTime);

        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            return ExecutionResult.error("Execution error: " + e.getMessage(), costTime);
        }
    }

    private String buildDockerCommand(CommandRequest request) {
        StringBuilder cmd = new StringBuilder();
        cmd.append("docker run --rm ");
        cmd.append("--memory=").append(MEMORY_LIMIT_MB).append("m ");
        cmd.append("--cpus=").append(CPU_LIMIT).append(" ");
        cmd.append("--network=none ");
        cmd.append("--user=1000:1000 ");
        cmd.append("-v /tmp/agent-workspace:/workspace ");
        cmd.append("-w /workspace ");
        cmd.append(request.getImage()).append(" ");
        cmd.append("bash -c \"");

        String escapedCommand = request.getCommand()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("$", "\\$")
                .replace("`", "\\`");

        cmd.append(escapedCommand);
        cmd.append("\"");

        return cmd.toString();
    }

    private String checkSecurity(String command) {
        if (command == null || command.isBlank()) {
            return "Empty command";
        }

        for (Pattern pattern : BLACKLIST_PATTERNS) {
            if (pattern.matcher(command).find()) {
                return "Command matches blacklisted pattern: " + pattern.pattern();
            }
        }

        return null;
    }
}
