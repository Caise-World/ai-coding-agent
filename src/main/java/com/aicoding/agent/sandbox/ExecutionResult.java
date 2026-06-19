package com.aicoding.agent.sandbox;

public class ExecutionResult {
    private String stdout;
    private String stderr;
    private int exitCode;
    private long costTimeMs;
    private boolean success;
    private String error;

    public ExecutionResult() {
        this.success = false;
    }

    public static ExecutionResult success(String stdout, String stderr, int exitCode, long costTimeMs) {
        ExecutionResult result = new ExecutionResult();
        result.stdout = stdout;
        result.stderr = stderr;
        result.exitCode = exitCode;
        result.costTimeMs = costTimeMs;
        result.success = exitCode == 0;
        return result;
    }

    public static ExecutionResult error(String error, long costTimeMs) {
        ExecutionResult result = new ExecutionResult();
        result.error = error;
        result.costTimeMs = costTimeMs;
        result.success = false;
        return result;
    }

    public String getStdout() { return stdout; }
    public void setStdout(String stdout) { this.stdout = stdout; }
    public String getStderr() { return stderr; }
    public void setStderr(String stderr) { this.stderr = stderr; }
    public int getExitCode() { return exitCode; }
    public void setExitCode(int exitCode) { this.exitCode = exitCode; }
    public long getCostTimeMs() { return costTimeMs; }
    public void setCostTimeMs(long costTimeMs) { this.costTimeMs = costTimeMs; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
