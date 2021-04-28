package cn.k8ops.ant.asl.pipeline;

import cn.k8ops.ant.asl.pipeline.exception.ConfigException;
import lombok.Data;

import java.util.Map;

import static cn.k8ops.ant.asl.pipeline.Config.KEY_TASK_ID;

@Data
public class Task {

    private String id;
    private Map<String, String> properties;
    private Map<String, String> environment;

    public Task(String id, Map<String, String> properties) {
        this.id = id;
        this.properties = properties;
    }

    public Task(String id) {
        this.id = id;
    }

    public static Task parse(Object  rawConfig, Map<String, String> environment) {
        Task task = null;
        if (rawConfig instanceof String) {
            task = new Task((String)rawConfig);
        }

        if (rawConfig instanceof Map) {
            Map taskConfig = (Map) rawConfig;

            if (taskConfig.size() == 1) {
                Map.Entry<String, Object> taskEntry = (Map.Entry<String, Object>) taskConfig.entrySet().iterator().next();

                if (taskEntry.getKey().equals(KEY_TASK_ID)) {
                    /**
                     * v2 task format
                     * - task.id: semver
                     */
                    task = new Task((String) taskEntry.getValue());
                } else {
                    /**
                     * v2 task format
                     * - maven:
                     *     goal: clean package
                     *     options: -Dmaven.test.skip=true
                     */
                    if (taskEntry.getValue() instanceof Map) {
                        task = new Task(taskEntry.getKey(), (Map<String, String>) taskEntry.getValue());
                    } else {
                        throw new ConfigException("task configure format error");
                    }
                }
            } else {
                /**
                 * v1 task format
                 * - task.id: maven
                 *   goal: clean package
                 *   options: -Dmaven.test.skip=true
                 */
                if (taskConfig.containsKey(KEY_TASK_ID)) {
                    String id = (String) taskConfig.get(KEY_TASK_ID);
                    task = new Task(id, taskConfig);
                }
            }
        }

        if (task != null) {
            task.environment = environment;
        }

        return task;
    }
}
