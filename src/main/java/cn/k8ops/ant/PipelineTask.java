package cn.k8ops.ant;

import cn.k8ops.ant.asl.pipeline.Runner;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

public class PipelineTask extends Task {

    @Setter
    @Getter
    private String file;

    @SneakyThrows
    @Override
    public void execute() {

        Runner runner = new Runner(file, this);
        boolean ret = runner.start();
        if (!ret) {
            throw new BuildException("pipeline run error.");
        }
    }

}
