package cn.k8ops.ant.asl.pipeline;

import cn.k8ops.ant.asl.pipeline.exception.ConfigException;
import cn.k8ops.ant.asl.pipeline.util.StringMatch;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class Stage {

    private Map rawConfig;

    private final String name;
    private Map<String, String> environment = new HashMap<String, String>();
    private List<Step> steps = new ArrayList<Step>();
    private List<Step> afterSteps = new ArrayList<Step>();
    private Map<String, Object> conditions = new HashMap<String, Object>();

    public Stage(String name) {
        this.name = name;
    }

    public static Stage parse(Map rawConfig, Map<String, String> environment) {
        if (!rawConfig.containsKey(Config.KEY_NAME)) {
            throw new ConfigException("stage config format error");
        }

        Stage step = new Stage((String) rawConfig.get(Config.KEY_NAME));
        step.rawConfig = rawConfig;
        step.environment = environment;

        // parse 'environment'
        if (rawConfig.containsKey(Config.KEY_ENVIRONMENT)) {
            step.environment.putAll((Map<? extends String, ? extends String>) rawConfig.get(Config.KEY_ENVIRONMENT));
        }

        // parse 'when'
        if (rawConfig.containsKey(Config.KEY_WHEN)) {
            step.conditions = (Map<String, Object>) rawConfig.get(Config.KEY_WHEN);
        }

        // parse 'tasks/after-tasks'
        step.steps = step.parseTasks(Config.KEY_STEPS);
        step.afterSteps = step.parseTasks(Config.KEY_AFTER_STEPS);

        if (step.steps.size() == 0) {
            throw new ConfigException("stage config format error");
        }

        return step;
    }

    private List<Step> parseTasks(String keyword) {
        List<Step> tasks = new ArrayList<Step>();

        if (rawConfig.containsKey(keyword)) {

            List tasksConfig = (List) rawConfig.get(keyword);

            if (tasksConfig != null) {
                for (Object taskConfig : tasksConfig) {
                    Step task = Step.parse(taskConfig, environment);
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

        if (conditions.containsKey(Config.KEY_ENVIRONMENT) && conditions.get(Config.KEY_ENVIRONMENT) instanceof Map) {
            Map<String, Object> whenEnviron = (Map<String, Object>) conditions.get(Config.KEY_ENVIRONMENT);

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
