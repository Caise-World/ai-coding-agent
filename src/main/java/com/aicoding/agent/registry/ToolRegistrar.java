package com.aicoding.agent.registry;

import com.aicoding.agent.tool.Tool;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ToolRegistrar implements ApplicationContextAware {

    private final ToolRegistry toolRegistry;
    private ApplicationContext applicationContext;

    public ToolRegistrar(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        this.applicationContext = ctx;
    }

    @PostConstruct
    public void registerTools() {
        Map<String, Tool> beans = applicationContext.getBeansOfType(Tool.class);
        beans.values().forEach(toolRegistry::register);
    }
}
