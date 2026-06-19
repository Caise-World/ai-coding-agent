package com.aicoding.agent.tool;

public interface Tool {
    String name();
    String description();
    String execute(String input);
}
