package com.aicoding.agent.registry;

import com.aicoding.agent.tool.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistrar {

    private final ToolRegistry toolRegistry;
    private final ProjectScanTool projectScanTool;
    private final FileReadTool fileReadTool;
    private final FileWriteTool fileWriteTool;
    private final CommandExecuteTool commandExecuteTool;

    public ToolRegistrar(
            ToolRegistry toolRegistry,
            ProjectScanTool projectScanTool,
            FileReadTool fileReadTool,
            FileWriteTool fileWriteTool,
            CommandExecuteTool commandExecuteTool) {
        this.toolRegistry = toolRegistry;
        this.projectScanTool = projectScanTool;
        this.fileReadTool = fileReadTool;
        this.fileWriteTool = fileWriteTool;
        this.commandExecuteTool = commandExecuteTool;
    }

    @PostConstruct
    public void registerTools() {
        toolRegistry.register(projectScanTool);
        toolRegistry.register(fileReadTool);
        toolRegistry.register(fileWriteTool);
        toolRegistry.register(commandExecuteTool);
    }
}
