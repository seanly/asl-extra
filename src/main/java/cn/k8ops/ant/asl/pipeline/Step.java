package cn.k8ops.ant.asl.pipeline;

import cn.k8ops.ant.asl.pipeline.exception.ConfigException;
import cn.k8ops.ant.asl.pipeline.util.StringMatch;
import lombok.Data;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.k8ops.ant.asl.pipeline.Config.*;

@Data
public class Step {

    private Map rawConfig;

    private final String name;
    private Map<String, String> environment = new HashMap<String, String>();
    private List<Task> tasks = new ArrayList<Task>();
    private List<Task> afterTasks = new ArrayList<Task>();
    private Map<String, Object> conditions = new HashMap<String, Object>();

    public Step(String name) {
        this.name = name;
    }

    public static Step parse(Map rawConfig, Map<String, String> environment) {
        if (!rawConfig.containsKey(KEY_NAME)) {
            throw new ConfigException("step config format error");
        }

        Step step = new Step((String) rawConfig.get(KEY_NAME));
        step.rawConfig = rawConfig;
        step.environment = environment;

        // parse 'environment'
        if (rawConfig.containsKey(KEY_ENVIRONMENT)) {
            step.environment.putAll((Map<? extends String, ? extends String>) rawConfig.get(KEY_ENVIRONMENT));
        }

        // parse 'when'
        if (rawConfig.containsKey(KEY_WHEN)) {
            step.conditions = (Map<String, Object>) rawConfig.get(KEY_WHEN);
        }

        // parse 'tasks/after-tasks'
        step.tasks = step.parseTasks(KEY_TASKS);
        step.afterTasks = step.parseTasks(KEY_AFTER_TASKS);

        if (step.tasks.size() == 0) {
            throw new ConfigException("step config format error");
        }

        return step;
    }

    private List<Task> parseTasks(String keyword) {
        List<Task> tasks = new ArrayList<Task>();

        if (rawConfig.containsKey(keyword)) {

            List tasksConfig = (List) rawConfig.get(keyword);

            if (tasksConfig != null) {
                for (Object taskConfig : tasksConfig) {
                    Task task = Task.parse(taskConfig, environment);
                    if (task != null) {
                        tasks.add(task);
                    }
                }
            }
        }

        return tasks;
    }

    /**
     * when:
     *   status: [failure, success]
     *   # or
     *   branch: [master, release] # or feature/*
     *   # or
     *   branch:
     *     include: [ master, feature/* ]
     *     exclude: [ feature/something, feature/old_thing* ]
     *   # or
     *   environment:
     *     PIPELINE_COMMIT_MESSAGE: "*skip_ci*"
     *   # or
     *   environment:
     *     PIPELINE_COMMIT_MESSAGE:
     *       exclude: "qlab*" # or [ "*skip_ci*", "*skip build*"]
     *
     *   **NOTICE** 当前只支持environment
     */
    public boolean shouldRun(Map<String, String> envvars) {

        if (conditions == null || conditions.size() == 0 ) {
            return true;
        }

        if (conditions.containsKey(KEY_ENVIRONMENT) && conditions.get(KEY_ENVIRONMENT) instanceof Map) {
            Map<String, Object> whenEnviron = (Map<String, Object>) conditions.get(KEY_ENVIRONMENT);

            for (Map.Entry<String, Object> entry : whenEnviron.entrySet()) {
                final StringMatch spec = StringMatch.fromSpecString(entry.getValue());
                if (! spec.matches(envvars.get(entry.getKey()))) {
                    return false;
                }
            }
        }
        return true;
    }
}
