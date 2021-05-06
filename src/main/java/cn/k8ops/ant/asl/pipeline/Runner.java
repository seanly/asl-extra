package cn.k8ops.ant.asl.pipeline;

import cn.k8ops.ant.tasks.PipelineTask;
import lombok.SneakyThrows;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Ant;
import org.apache.tools.ant.taskdefs.Property;
import org.apache.tools.ant.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class Runner {

    protected ProcessBuilder processBuilder = new ProcessBuilder();

    protected static boolean isWindows;
    protected static String osName;

    protected Config config;
    private final PipelineTask pipelineTask;

    public Runner(String configPath, PipelineTask pipelineTask) {
        verifyConfigPath(configPath);

        this.config = Config.parse(configPath);
        this.pipelineTask = pipelineTask;

        // Check for windows..
        osName = System.getProperty("os.name", "unknown").toLowerCase();
        isWindows = osName.contains("windows");
    }

    private void verifyConfigPath(String configPath) {

        if (null == StringUtils.trimToNull(configPath)) {
            throw new BuildException("config not set.");
        }

        File configFile = new File(configPath);
        if (!configFile.exists()) {
            throw new BuildException("config file is not exist.");
        }
    }

    public boolean start() {
        initDirs();
        boolean result = false;

        for (Step step : config.getSteps()) {
            result = runStep(step);
            if (!result) {
                break;
            }
        }
        return result;
    }

    private void log(String message) {
        pipelineTask.log(message);
    }
    private Project getProject() {
        return pipelineTask.getProject();
    }

    private boolean runStep(Step step) {
        boolean result = false;

        log(String.format("--//STEP: %s------", step.getName()));

        Map<String, String> localEnviron = config.getEnvironment();
        localEnviron.putAll(step.getEnvironment());
        localEnviron.putAll(processBuilder.environment());
        if (!step.shouldRun(localEnviron)) {
            return true;
        }

        log("--//TASKS-------------");
        for (Task task : step.getTasks()) {
            result = runTask(task);
            if (!result) {
                break;
            }
        }

        if (step.getAfterTasks().size() != 0) {
            log("--//AFTER-TASKS------");
            boolean result2 = runTasks(step.getAfterTasks());
            if (result) {
                result = result2;
            }
        }

        return result;
    }

    private boolean runTasks(List<Task> tasks) {
        for(Task task : tasks) {
            if (!runTask(task)) {
                return false;
            }
        }

        return true;
    }

    private boolean runTask(Task task) {
        return ant(task);
    }

    public final static String DIR_DOT_CI = ".ci";

    @SneakyThrows
    private boolean ant(Task task) {
        log(String.format("--//task: %s", task.getId()));

        String runId = String.format("%s-%s", task.getId(), getCurrentTime());

        Properties properties = new Properties();
        properties.putAll(task.getProperties());

        for (Map.Entry<String, String> environ : task.getEnvironment().entrySet()) {
            properties.put(String.format("env.%s", environ.getKey()), environ.getValue());
        }

        File propsFile = new File(getWs(), DIR_DOT_CI + File.separator + runId + ".properties");
        if (!propsFile.getParentFile().exists()) {
            propsFile.getParentFile().mkdirs();
        }
        properties.store(new FileOutputStream(propsFile), "task properties");

        String aslRoot;
        if (getProject() == null) {
            aslRoot = System.getProperty("asl.root");
        } else {
            aslRoot = getProject().getProperty("asl.root");
        }

        if (aslRoot == null) {
            throw new BuildException("asl.root is not set.");
        }

        File aslDir = new File(aslRoot);
        if (!aslDir.exists()) {
            throw new BuildException("asl.root dir is not exists.");
        }

        File runXml = new File(aslDir, "run.xml");

        try {
            Ant ant = new Ant(pipelineTask);
            ant.setInheritRefs(true);
            ant.setAntfile(runXml.getAbsolutePath());
            ant.setTarget("task");
            Property property = ant.createProperty();
            property.setFile(propsFile.getAbsoluteFile());
            ant.execute();
        } catch (BuildException e) {
            return false;
        }

        return true;
    }

    private File getWs() {
        return config.getConfigFile().getParentFile();
    }

    private void initDirs() {
        File dotCIDir = new File(getWs(), DIR_DOT_CI);
        if (!dotCIDir.exists()) {
            dotCIDir.mkdirs();
        }
    }

    public static String getCurrentTime() {
        return new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime());
    }
}
