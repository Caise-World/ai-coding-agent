package com.aicoding.agent.tool;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

@Component
public class CommandExecuteTool implements Tool {

    private static final int TIMEOUT_SECONDS = 300;

    @Override
    public String name() {
        return "CommandExecuteTool";
    }

    @Override
    public String description() {
        return "Executes shell commands. Input: absolute command to execute. Output: stdout + stderr. Supports: mvn test, mvn compile, etc.";
    }

    @Override
    public String execute(String input) {
        try {
            String command = input.trim();
            StringBuilder result = new StringBuilder();

            result.append("Executing: ").append(command).append("\n");
            result.append("=".repeat(50)).append("\n");

            ProcessBuilder processBuilder = new ProcessBuilder();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                processBuilder.command("cmd.exe", "/c", command);
            } else {
                processBuilder.command("bash", "-c", command);
            }

            processBuilder.redirectErrorStream(false);
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();

            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                } catch (Exception e) {
                    error.append("Error reading stdout: ").append(e.getMessage()).append("\n");
                }
            });

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        error.append(line).append("\n");
                    }
                } catch (Exception e) {
                    error.append("Error reading stderr: ").append(e.getMessage()).append("\n");
                }
            });

            stdoutThread.start();
            stderrThread.start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return result + "TIMEOUT: Command exceeded " + TIMEOUT_SECONDS + " seconds\n";
            }

            stdoutThread.join(1000);
            stderrThread.join(1000);

            if (output.length() > 0) {
                result.append("STDOUT:\n").append(output);
            }
            if (error.length() > 0) {
                result.append("STDERR:\n").append(error);
            }

            result.append("=".repeat(50)).append("\n");
            result.append("Exit code: ").append(process.exitValue()).append("\n");

            return result.toString();
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }
}
