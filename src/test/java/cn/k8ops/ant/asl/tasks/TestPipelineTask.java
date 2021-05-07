package cn.k8ops.ant.asl.tasks;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.junit.Test;

public class TestPipelineTask {

    PipelineTask task = new PipelineTask();

    private static class MyProject extends Project {
        @Override
        public void log(Task t, String message, int level) {
            System.out.println(message);
        }
    }

    @Test
    public void testExecute() {
        MyProject project = new MyProject();
        project.setProperty("asl.root", "/Users/seanly/Programming/jenkins/ant-asl");
        task.setFile("./sample/pipeline.yml");
        task.setProject(project);

        task.execute();
    }
}
