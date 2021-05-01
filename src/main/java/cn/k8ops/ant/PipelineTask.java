package cn.k8ops.ant;

import cn.k8ops.ant.asl.pipeline.Config;
import cn.k8ops.ant.asl.pipeline.Step;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.util.StringUtils;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class PipelineTask extends Task {

    @Setter
    @Getter
    private String file;

    private Config config;
    protected static boolean isWindows;
    protected static String osName;
    protected ProcessBuilder processBuilder = new ProcessBuilder();

    protected void initTask() {
        // Check for windows..
        osName = System.getProperty("os.name", "unknown").toLowerCase();
        isWindows = osName.indexOf("windows") >= 0;
    }

    @SneakyThrows
    @Override
    public void execute() {
        initTask();

        if (null == StringUtils.trimToNull(file)) {
            throw new BuildException("config not set.");
        }

        File configFile = new File(file);
        if (!configFile.exists()) {
            throw new BuildException("config file is not exist.");
        }

        this.config = Config.parse(file);
        initDirs();

        boolean ret = runPipeline();
        if (!ret) {
            throw new BuildException("pipeline run error.");
        }
    }

    public boolean runPipeline() {

        boolean result = false;

        for (Step step : config.getSteps()) {
            result = runStep(step);

            if (!result) {
                break;
            }
        }

        return result;
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
        for (cn.k8ops.ant.asl.pipeline.Task task : step.getTasks()) {
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

    private boolean runTasks(List<cn.k8ops.ant.asl.pipeline.Task> tasks) {
        for(cn.k8ops.ant.asl.pipeline.Task task : tasks) {
            if (!runTask(task)) {
                return false;
            }
        }

        return true;
    }

    private boolean runTask(cn.k8ops.ant.asl.pipeline.Task task) {
        return ant(task);
    }

    public final static String DIR_DOT_CI = ".ci";

    @SneakyThrows
    private boolean ant(cn.k8ops.ant.asl.pipeline.Task task) {
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

        File antExec = new File(aslDir, "tools/ant/bin/ant");
        File runXml = new File(aslDir, "run.xml");

        // command
        List<String> commands = new ArrayList<>();
        commands.add(antExec.getAbsolutePath());
        commands.add("-f");
        commands.add(runXml.getAbsolutePath());
        commands.add("task");
        commands.add("-propertyfile");
        commands.add(propsFile.getAbsolutePath());
        commands.add("-logger");
        commands.add("org.apache.tools.ant.NoBannerLogger");

        processBuilder.command(commands);
        Map<String, String> env = processBuilder.environment();
        env.putAll(task.getEnvironment());
        Process p = processBuilder.inheritIO().start();
        return p.waitFor() == 0;
    }

    private File getWs() {
        File configFile = new File(file);
        return configFile.getParentFile();
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
