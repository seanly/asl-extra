package cn.k8ops.ant.asl.tasks;

import org.junit.Test;

public class TestDapperTask {

    DapperTask task = new DapperTask();

    @Test
    public void testRun() {
        task.setFile("sample/Dockerfile.dapper");
        task.Run();
    }
}
