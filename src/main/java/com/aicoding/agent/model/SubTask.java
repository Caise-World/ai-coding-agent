package com.aicoding.agent.model;

public class SubTask {
    private int id;
    private String description;
    private String tool;
    private String input;
    private String result;
    private TaskState state;

    public SubTask() {
        this.state = TaskState.PENDING;
    }

    public SubTask(int id, String description, String tool, String input) {
        this.id = id;
        this.description = description;
        this.tool = tool;
        this.input = input;
        this.state = TaskState.PENDING;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTool() { return tool; }
    public void setTool(String tool) { this.tool = tool; }
    public String getInput() { return input; }
    public void setInput(String input) { this.input = input; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public TaskState getState() { return state; }
    public void setState(TaskState state) { this.state = state; }
    public boolean isCompleted() { return state == TaskState.SUCCESS; }
}
