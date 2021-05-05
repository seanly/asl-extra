package cn.k8ops.ant.tasks;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;

public class DapperTask extends Task {

    private Map<String, String> environ = new HashMap<>();

    @Setter
    @Getter
    private String file;

    @Setter
    @Getter
    private boolean quiet;

    @SneakyThrows
    @Override
    public void execute() {
        Run();
    }

    public static String getCurrentTime() {
        return new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime());
    }

    private File getWs() {
        return new File(file).getParentFile();
    }

    private String tag() {
        File dapperFile = new File(file);
        String cwd = dapperFile.getParentFile().getName();
        return String.format("%s:%s", cwd, getCurrentTime());
    }

    @SneakyThrows
    public void Run() {
        String tag = Build();

        List<String> commands = new ArrayList<>();
        commands.add("docker");
        commands.add("run");
        String name = runArgs(tag, commands);

        log(String.format("Running build in %s", name));

        log(String.join(" ", commands));
        ProcessBuilder pb = new ProcessBuilder(commands);
        Process p = pb.inheritIO().start();
        if (p.waitFor() != 0) {
            throw new BuildException("docker build error");
        }
    }

    public String shellExec(List<String> commands) {
        ProcessBuilder pb = new ProcessBuilder(commands);
        Process p;
        String result = "";
        try {
            p = pb.start();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

            StringJoiner sj = new StringJoiner(System.getProperty("line.separator"));
            reader.lines().iterator().forEachRemaining(sj::add);
            result = sj.toString();

            p.waitFor();
            p.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private String runArgs(String tag, List<String> commands) {
        String name = String.format("%s-%s", tag.split(":")[0], getCurrentTime());
        commands.add("-i");
        commands.add("--name");
        commands.add(name);

        // -- mount DAPPER_SOURCE
        commands.add("-v");
        commands.add(String.format("%s:%s", getWs().getAbsolutePath(), environ.get("DAPPER_SOURCE")));

        // -- uid/gid
        String uid = shellExec(Arrays.asList("id", "-u"));
        String gid = shellExec(Arrays.asList("id", "-g"));

        commands.add("-e");
        commands.add(String.format("DAPPER_UID=%s", uid));
        commands.add("-e");
        commands.add(String.format("DAPPER_GID=%s", gid));

        // -- DAPPER_ENV
        List<String> environments = new ArrayList<>();
        if (environ.containsKey("DAPPER_ENV")) {
            String envionStr = (String) environ.get("DAPPER_ENV");
            String[] envionArr = envionStr.split(" ");

            for(String env: envionArr) {
                environments.add(env.trim());
            }
        }

        for (String env : environments) {
            commands.add("-e");
            commands.add(env);
        }

        // -- tag

        commands.add(tag);

        return name;
    }

    @SneakyThrows
    public String Build() {

        File dapperFile = new File(file);

        String tag = tag();
        log(String.format("Building %s using %s", tag, dapperFile.getName()));

        List<String> commands = new ArrayList<>();
        commands.add("docker");
        commands.add("build");
        commands.add("-t");
        commands.add(tag);

        if (quiet) {
            commands.add("-q");
        }

        commands.add("-f");
        commands.add(dapperFile.getAbsolutePath());
        commands.add(dapperFile.getParent());

        log(String.join(" ", commands));
        ProcessBuilder pb = new ProcessBuilder(commands);
        Process p = pb.inheritIO().start();
        if (p.waitFor() != 0) {
            throw new BuildException("docker build error");
        }

        readEnv(tag);

        return tag;
    }

    @SneakyThrows
    private void readEnv(String tag) {

        String jsonStr = shellExec(Arrays.asList("docker", "inspect", "-f", "{{json .Config.Env}}", tag));

        if(StringUtils.trimToNull(jsonStr) == null) {
            throw new BuildException("docker inspect error");
        }

        for (Object env : JSON.parseArray(jsonStr) ) {
            if (env instanceof String) {
                String[] envItem = ((String) env).split("=");
                if (envItem[0].startsWith("DAPPER")) {
                    environ.put(envItem[0], envItem[1]);
                }
            }
        }
    }
}
