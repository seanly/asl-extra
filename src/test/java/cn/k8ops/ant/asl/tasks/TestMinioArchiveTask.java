package cn.k8ops.ant.asl.tasks;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.junit.Test;

import java.io.File;


public class TestMinioArchiveTask {

    MinioArchiveTask task = new MinioArchiveTask();

    private static class MyProject extends Project {

        @Override
        public void log(Task t, String message, int level) {
            System.out.println(message);
        }
    }

    @Test
    public void testArchive() {

        task.setProject(new MyProject());

        task.endpoint = "http://minio.local/";
        task.accessKey = "minio";
        task.secretKey = "minio123";
        task.bucket = "test";
        task.remotePath = "qa/hello/world";

        FileSet fileSet = new FileSet();
        fileSet.setDir(new File("sample"));
        fileSet.setIncludes("**/*");

        task.addFileSet(fileSet);

        task.execute();
    }

}
