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

        log(String.format("--//-----STEP: %s------", step.getName()));

        Map<String, String> localEnviron = config.getEnvironment();
        localEnviron.putAll(step.getEnvironment());
        if (!step.shouldRun(localEnviron)) {
            return true;
        }

        for (cn.k8ops.ant.asl.pipeline.Task task : step.getTasks()) {

            log("-----TASKS...-------------");
            result = runTask(task);
            if (!result) {
                break;
            }
        }

        if (step.getAfterTasks().size() != 0) {
            log("------AFTER-TASKS... ------");
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

        // environment
        String[] environ = task.getEnvironment().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).toArray(String[]::new);

        String[] cmds = commands.toArray(new String[commands.size()]);
        Process p = Runtime.getRuntime().exec(cmds, environ, getWs());
        StreamCopier copier = new StreamCopier(p.getInputStream());
        copier.start();

        int exitVal = p.waitFor();
        copier.doJoin();
        //String stdOutAndError = copier.getOutput();

        if (exitVal != 0 ) {
            return false;
        }

        return true;
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

    //-----
    private class StreamCopier extends Thread {
        private final BufferedReader reader;
        private boolean joined;
        private boolean terminated;
        private StringBuilder sb;

        StreamCopier(InputStream input) {
            this.reader = new BufferedReader(new InputStreamReader(input));
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                sb = new StringBuilder();
                for (String line; (line = reader.readLine()) != null;) {
                    synchronized (this) {
                        if (joined) {
                            // The main thread was notified that the process
                            // ended and has already given up waiting for
                            // output from the foreground process.
                            break;
                        }
                        sb.append(line);
                        log(line);
                    }
                }
            } catch (IOException ex) {
                throw new BuildException(ex);
            } finally {
                if (isWindows) {
                    synchronized (this) {
                        terminated = true;
                        notifyAll();
                    }
                }
            }
        }

        public String getOutput() {
            if (sb != null) {
                return sb.toString();
            }
            return "";
        }

        public void doJoin() throws InterruptedException {
            if (isWindows) {
                // Windows doesn't disconnect background processes (start /b)
                // from the console of foreground processes, so waiting until
                // the end of output from server.bat means waiting until the
                // server process itself ends. We can't wait that long, so we
                // wait one second after .waitFor() ends. Hopefully this will
                // be long enough to copy all the output from the script.

                synchronized (this) {
                    long begin = System.nanoTime();
                    long end = begin
                            + TimeUnit.NANOSECONDS.convert(1, TimeUnit.SECONDS);
                    long duration = end - begin;
                    while (!terminated && duration > 0) {
                        TimeUnit.NANOSECONDS.timedWait(this, duration);
                        duration = end - System.nanoTime();
                    }

                    // If the thread didn't end after waiting for a second,
                    // then assume it's stuck in a blocking read. Oh well,
                    // it's a daemon thread, so it'll go away eventually. Let
                    // it know that we gave up to avoid spurious output in case
                    // it eventually wakes up.
                    joined = true;
                }
            } else {
                super.join();
            }
        }
    }
}
