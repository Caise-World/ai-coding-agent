package com.aicoding.agent.sandbox;

public class CommandRequest {
    private String command;
    private int timeoutSeconds = 10;
    private String workDir = "/workspace";
    private String image = "maven:3.9-eclipse-temurin-17";

    public CommandRequest() {}

    public CommandRequest(String command) {
        this.command = command;
    }

    public CommandRequest(String command, int timeoutSeconds) {
        this.command = command;
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public String getWorkDir() { return workDir; }
    public void setWorkDir(String workDir) { this.workDir = workDir; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
}
