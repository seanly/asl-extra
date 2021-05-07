package cn.k8ops.ant.asl.pipeline;

import cn.k8ops.ant.asl.tasks.PipelineTask;
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

        for (Stage stage : config.getStages()) {
            result = runStage(stage);
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

    private boolean runStage(Stage stage) {
        boolean result = false;

        log(String.format("--//STAGE: %s", stage.getName()));

        Map<String, String> localEnviron = config.getEnvironment();
        localEnviron.putAll(stage.getEnvironment());
        localEnviron.putAll(processBuilder.environment());
        if (!stage.shouldRun(localEnviron)) {
            return true;
        }

        log("--//STEPS:...");
        for (Step step : stage.getSteps()) {
            result = runStep(step);
            if (!result) {
                break;
            }
        }

        if (stage.getAfterSteps().size() != 0) {
            log("\n\n--//AFTER-STEPS:...");
            boolean result2 = runSteps(stage.getAfterSteps());
            if (result) {
                result = result2;
            }
        }

        return result;
    }

    private boolean runSteps(List<Step> steps) {
        for(Step step : steps) {
            if (!runStep(step)) {
                return false;
            }
        }

        return true;
    }

    private boolean runStep(Step task) {
        return ant(task);
    }

    public final static String DIR_DOT_CI = ".ci";

    @SneakyThrows
    private boolean ant(Step step) {

        String runId = String.format("%s-%s", step.getId(), getCurrentTime());

        Properties properties = new Properties();
        properties.putAll(step.getProperties());

        for (Map.Entry<String, String> environ : step.getEnvironment().entrySet()) {
            properties.put(String.format("env.%s", environ.getKey()), environ.getValue());
        }

        File propsFile = new File(getWs(), DIR_DOT_CI + File.separator + runId + ".properties");
        if (!propsFile.getParentFile().exists()) {
            propsFile.getParentFile().mkdirs();
        }

        String wsDir = null;

        String aslRoot;
        if (getProject() == null) {
            aslRoot = System.getProperty("asl.root");
            wsDir = System.getProperty("ws.dir");
        } else {
            aslRoot = getProject().getProperty("asl.root");
            wsDir = getProject().getProperty("ws.dir");
        }

        if (aslRoot == null) {
            throw new BuildException("asl.root is not set.");
        }

        if (wsDir == null) {
            wsDir = getWs().getAbsolutePath();
        }

        properties.put("ws.dir", wsDir);
        properties.store(new FileOutputStream(propsFile), "task properties");

        File aslDir = new File(aslRoot);
        if (!aslDir.exists()) {
            throw new BuildException("asl.root dir is not exists.");
        }

        File runXml = new File(aslDir, "run.xml");

        try {
            Ant ant = new Ant(pipelineTask);
            ant.setInheritRefs(true);
            ant.setAntfile(runXml.getAbsolutePath());
            ant.setTarget("step");
            Property property = ant.createProperty();
            property.setFile(propsFile.getAbsoluteFile());
            ant.execute();
        } catch (BuildException e) {
            log(e.getMessage());
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
