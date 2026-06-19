package com.aicoding.agent.service;

import com.aicoding.agent.model.SubTask;
import com.aicoding.agent.model.TaskState;
import com.aicoding.agent.tool.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TaskExecutorService {

    private final ProjectScanTool projectScanTool;
    private final FileReadTool fileReadTool;
    private final FileWriteTool fileWriteTool;
    private final CommandExecuteTool commandExecuteTool;

    public TaskExecutorService(
            ProjectScanTool projectScanTool,
            FileReadTool fileReadTool,
            FileWriteTool fileWriteTool,
            CommandExecuteTool commandExecuteTool) {
        this.projectScanTool = projectScanTool;
        this.fileReadTool = fileReadTool;
        this.fileWriteTool = fileWriteTool;
        this.commandExecuteTool = commandExecuteTool;
    }

    public String executeSubTask(SubTask task) {
        Tool tool = getTool(task.getTool());
        if (tool == null) {
            return "Error: Unknown tool: " + task.getTool();
        }

        try {
            String result = tool.execute(task.getInput());
            task.setResult(result);
            task.setState(TaskState.SUCCESS);
            return result;
        } catch (Exception e) {
            String error = "Error executing " + task.getTool() + ": " + e.getMessage();
            task.setResult(error);
            return error;
        }
    }

    public String executeAll(List<SubTask> subtasks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Task Execution Summary:\n");
        sb.append("=".repeat(50)).append("\n\n");

        for (SubTask task : subtasks) {
            sb.append("[").append(task.getId()).append("] ").append(task.getDescription()).append("\n");
            sb.append("Tool: ").append(task.getTool()).append("\n");
            sb.append("Input: ").append(task.getInput()).append("\n");
            sb.append("-".repeat(30)).append("\n");

            String result = executeSubTask(task);
            sb.append("Result:\n").append(result).append("\n");
            sb.append("Status: ").append(task.isCompleted() ? "SUCCESS" : "FAILED").append("\n");
            sb.append("\n");
        }

        return sb.toString();
    }

    private Tool getTool(String toolName) {
        return switch (toolName) {
            case "ProjectScanTool" -> projectScanTool;
            case "FileReadTool" -> fileReadTool;
            case "FileWriteTool" -> fileWriteTool;
            case "CommandExecuteTool" -> commandExecuteTool;
            default -> null;
        };
    }
}
